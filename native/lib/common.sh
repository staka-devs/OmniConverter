# 全レシピ共通のツールチェーン設定と補助関数。build.sh が読み込む
set -euo pipefail

NATIVE_DIR=/native
WORK=$NATIVE_DIR/work
DL=$WORK/dl
SRC=$WORK/src
PREFIX=$WORK/prefix
OUT=$NATIVE_DIR/out
JOBS=$(nproc)

# minSdk に合わせる。ADR-0007
API=29
TARGET=aarch64-linux-android
TOOLCHAIN=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64
SYSROOT=$TOOLCHAIN/sysroot

export CC="$TOOLCHAIN/bin/${TARGET}${API}-clang"
export CXX="$TOOLCHAIN/bin/${TARGET}${API}-clang++"
export AR=$TOOLCHAIN/bin/llvm-ar
export RANLIB=$TOOLCHAIN/bin/llvm-ranlib
export STRIP=$TOOLCHAIN/bin/llvm-strip
export NM=$TOOLCHAIN/bin/llvm-nm
export LD=$TOOLCHAIN/bin/ld.lld
export CFLAGS="-O2 -fPIC -I$PREFIX/include"
export CXXFLAGS="$CFLAGS"
# 16 KB ページの端末でも読み込めるよう揃える。C++ 標準ライブラリは静的に入れ、実行ファイル単体で動かす
export LDFLAGS="-L$PREFIX/lib -Wl,-z,max-page-size=16384 -static-libstdc++"
export PKG_CONFIG_LIBDIR=$PREFIX/lib/pkgconfig:$PREFIX/share/pkgconfig
export PKG_CONFIG_PATH=
export PATH=$PREFIX/bin:$PATH

mkdir -p "$DL" "$SRC" "$PREFIX/lib" "$OUT"
# Android の pthread と rt は libc にある。-lpthread を求めるビルドのために空の静的ライブラリを置く
for l in pthread rt; do [ -f "$PREFIX/lib/lib$l.a" ] || "$AR" rcs "$PREFIX/lib/lib$l.a"; done
# zlib は NDK の共有版を使う。pkg-config で探すビルドのために .pc だけを置く
mkdir -p "$PREFIX/lib/pkgconfig"
printf 'Name: zlib\nDescription: NDK zlib\nVersion: 1.3\nLibs: -lz\nCflags:\n' > "$PREFIX/lib/pkgconfig/zlib.pc"

log() { printf '\n==> %s\n' "$*"; }

# fetch <名前> <URL> <sha256>: 取得してハッシュを検証し、$SRC/<名前> に展開し直す
fetch() {
    local name=$1 url=$2 sha=$3
    local file="$DL/${url##*/}"
    [ -f "$file" ] || curl -fsSL --retry 3 -o "$file" "$url"
    echo "$sha  $file" | sha256sum -c - >/dev/null || { echo "ハッシュが一致しません: $file" >&2; rm -f "$file"; exit 1; }
    rm -rf "${SRC:?}/$name" && mkdir -p "$SRC/$name"
    tar -xf "$file" -C "$SRC/$name" --strip-components=1
    collect_licenses "$name"
}

# 配布物に添えるため、ソースに含まれるライセンス文書を $OUT/assets/licenses/<名前>/ に集める。ADR-0006
collect_licenses() {
    local name=$1
    rm -rf "${OUT:?}/assets/licenses/$name"
    (cd "$SRC/$name" && find . -maxdepth 3 -type f \
        \( -iname 'LICENSE*' -o -iname 'COPYING*' -o -iname 'COPYRIGHT*' -o -iname 'NOTICE*' \) \
        -not -path '*/test*' -exec install -D -m 644 {} "$OUT/assets/licenses/$name/{}" \;)
}

# autotools <ソース> [configure の追加引数...]
autotools() {
    local dir=$1; shift
    (cd "$dir" && ./configure --host=$TARGET --prefix="$PREFIX" --enable-static --disable-shared "$@" && make -j"$JOBS" && make install)
}

# cmake_build <ソース> [cmake の追加引数...]
cmake_build() {
    local dir=$1; shift
    cmake -S "$dir" -B "$dir/build-android" -G Ninja \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-$API \
        -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="$PREFIX" -DCMAKE_PREFIX_PATH="$PREFIX" \
        -DCMAKE_FIND_ROOT_PATH="$PREFIX" -DBUILD_SHARED_LIBS=OFF -DCMAKE_POSITION_INDEPENDENT_CODE=ON "$@"
    cmake --build "$dir/build-android" -j"$JOBS"
    cmake --install "$dir/build-android"
}

# meson_build <ソース> [meson の追加引数...]
meson_build() {
    local dir=$1; shift
    local cross=$WORK/android-cross.ini
    cat > "$cross" <<INI
[binaries]
c = '$CC'
cpp = '$CXX'
ar = '$AR'
strip = '$STRIP'
pkg-config = 'pkg-config'
[host_machine]
system = 'android'
cpu_family = 'aarch64'
cpu = 'aarch64'
endian = 'little'
INI
    meson setup "$dir/build-android" "$dir" --cross-file "$cross" --prefix "$PREFIX" --libdir lib \
        --buildtype release --default-library static "$@"
    ninja -C "$dir/build-android" -j"$JOBS"
    ninja -C "$dir/build-android" install
}

# install_tool <実行ファイル> <エンジン> <名前>: lib<名前>.so としてエンジンの jniLibs へ置く。ADR-0004
install_tool() {
    local bin=$1 name=$3
    local dest="$OUT/$2/arm64-v8a"
    mkdir -p "$dest"
    "$STRIP" -o "$dest/lib$name.so" "$bin"
    verify_tool "$dest/lib$name.so"
}

# 端末で単体実行できる形かを確かめる。aarch64 の PIE で、端末に必ずある NDK のシステムライブラリだけに依存すること
verify_tool() {
    local f=$1
    file -L "$f" | grep -q 'ARM aarch64' || { echo "aarch64 ではありません: $f" >&2; exit 1; }
    file -L "$f" | grep -q 'pie executable\|shared object' || { echo "PIE ではありません: $f" >&2; exit 1; }
    local needed
    needed=$("$TOOLCHAIN/bin/llvm-readelf" -d "$f" | sed -n 's/.*Shared library: \[\(.*\)\]/\1/p' \
        | grep -vxE 'libc\.so|libm\.so|libdl\.so|liblog\.so|libz\.so|libandroid\.so|libmediandk\.so' || true)
    [ -z "$needed" ] || { echo "同梱されない共有ライブラリに依存しています: $needed" >&2; exit 1; }
    log "OK $(basename "$f") $(du -h "$f" | cut -f1)"
}
