# magick だけを同梱する。外部の委譲先はすべて静的にリンクし、C++ の委譲先のために C++ 標準ライブラリも静的に足す
for dep in libjpeg libpng libtiff libwebp libjxl libheif libraw lcms2 bzip2 xz zstd; do need $dep; done
drop_shared_cxx_runtime_from_pc_files

fetch imagemagick "$IMAGEMAGICK_URL" "$IMAGEMAGICK_SHA256"
autotools "$SRC/imagemagick" --without-modules --disable-openmp --disable-docs --with-quantum-depth=16 --enable-hdri \
    --without-x --without-perl --without-magick-plus-plus --without-fontconfig --without-freetype --without-xml \
    --without-gslib --without-gvc --without-djvu --without-openexr --without-pango --without-rsvg --without-wmf \
    --without-fftw --without-jbig --without-zip --without-dps --without-lqr --without-raqm --without-uhdr \
    --with-jpeg --with-png --with-tiff --with-webp --with-jxl --with-heic --with-raw --with-lcms --with-zlib \
    --with-bzlib --with-lzma --with-zstd \
    LIBS="$STATIC_CXX_LIBS -lm -llog" PKG_CONFIG="pkg-config --static"
install_tool "$SRC/imagemagick/utilities/magick" imagemagick magick
