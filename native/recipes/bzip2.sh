fetch bzip2 "$BZIP2_URL" "$BZIP2_SHA256"
# bzip2 は Makefile だけを持つので、ライブラリだけを作って手で置く
(cd "$SRC/bzip2" && make -j"$JOBS" libbz2.a CC="$CC" AR="$AR" RANLIB="$RANLIB" CFLAGS="$CFLAGS -D_FILE_OFFSET_BITS=64")
install -D -m 644 "$SRC/bzip2/libbz2.a" "$PREFIX/lib/libbz2.a"
install -D -m 644 "$SRC/bzip2/bzlib.h" "$PREFIX/include/bzlib.h"
