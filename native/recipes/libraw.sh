need lcms2; need libjpeg
fetch libraw "$LIBRAW_URL" "$LIBRAW_SHA256"
(cd "$SRC/libraw" && autoreconf -fi)
autotools "$SRC/libraw" --disable-examples --disable-openmp --disable-jasper --enable-lcms --enable-jpeg
