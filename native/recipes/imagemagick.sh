# magick だけを同梱する。外部の委譲先はすべて静的にリンクする
for dep in libjpeg libpng libtiff libwebp libjxl libheif libraw lcms2 bzip2 xz zstd; do need $dep; done

# C++ の委譲先の .pc は共有版の C++ 標準ライブラリを求める。静的版を自分で足すので取り除く
sed -i -E 's/(^|[[:space:]])-l(stdc\+\+|c\+\+)([[:space:]]|$)/\1\3/g' "$PREFIX"/lib/pkgconfig/*.pc

fetch imagemagick "$IMAGEMAGICK_URL" "$IMAGEMAGICK_SHA256"
# 委譲先に C++ のライブラリがあるので、C++ 標準ライブラリを静的に足す
autotools "$SRC/imagemagick" --without-modules --disable-openmp --disable-docs --with-quantum-depth=16 --enable-hdri \
    --without-x --without-perl --without-magick-plus-plus --without-fontconfig --without-freetype --without-xml \
    --without-gslib --without-gvc --without-djvu --without-openexr --without-pango --without-rsvg --without-wmf \
    --without-fftw --without-jbig --without-zip --without-dps --without-lqr --without-raqm --without-uhdr \
    --with-jpeg --with-png --with-tiff --with-webp --with-jxl --with-heic --with-raw --with-lcms --with-zlib \
    --with-bzlib --with-lzma --with-zstd \
    LIBS="-lc++_static -lc++abi -lm -llog" PKG_CONFIG="pkg-config --static"
install_tool "$SRC/imagemagick/utilities/magick" imagemagick magick
