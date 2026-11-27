# GPL のコーデックを含む FFmpeg。ADR-0006
for dep in x264 x265 libvpx dav1d opus lame libwebp; do need $dep; done

fetch ffmpeg "$FFMPEG_URL" "$FFMPEG_SHA256"
# x265 は C++ なので、リンクは clang++ で行い C++ 標準ライブラリを静的に入れる
# ffprobe は作らない。長さは ffmpeg の出力から読めるので、30 MB の重複を避ける
(cd "$SRC/ffmpeg" && ./configure \
    --prefix="$PREFIX" --enable-cross-compile --target-os=android --arch=aarch64 --cpu=armv8-a \
    --cc="$CC" --cxx="$CXX" --ld="$CXX" --ar="$AR" --ranlib="$RANLIB" --strip="$STRIP" --nm="$NM" \
    --sysroot="$SYSROOT" --pkg-config=pkg-config --pkg-config-flags=--static \
    --extra-cflags="$CFLAGS" --extra-ldflags="$LDFLAGS" \
    --enable-gpl --enable-version3 --enable-pic --disable-doc --disable-debug \
    --disable-ffplay --disable-ffprobe \
    --enable-zlib --enable-libx264 --enable-libx265 --enable-libvpx --enable-libdav1d \
    --enable-libopus --enable-libmp3lame --enable-libwebp --disable-indev=android_camera \
    && make -j"$JOBS")
install_tool "$SRC/ffmpeg/ffmpeg" ffmpeg ffmpeg
