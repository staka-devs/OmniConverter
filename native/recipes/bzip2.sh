# bzip2。Makefile しか持たないので、ライブラリだけを作って手で置く
fetch bzip2 "$BZIP2_URL" "$BZIP2_SHA256"
(cd "$SRC/bzip2" && make -j"$JOBS" libbz2.a CC="$CC" AR="$AR" RANLIB="$RANLIB" CFLAGS="$CFLAGS -D_FILE_OFFSET_BITS=64")
install -D -m 644 "$SRC/bzip2/libbz2.a" "$PREFIX/lib/libbz2.a"
install -D -m 644 "$SRC/bzip2/bzlib.h" "$PREFIX/include/bzlib.h"
