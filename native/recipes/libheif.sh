# HEIC と AVIF。heif-enc と heif-dec を同梱する
for dep in libjpeg libpng libde265 x265 libaom dav1d libwebp; do need $dep; done

fetch libheif "$LIBHEIF_URL" "$LIBHEIF_SHA256"
cmake_build "$SRC/libheif" -DWITH_EXAMPLES=ON -DBUILD_TESTING=OFF -DENABLE_PLUGIN_LOADING=OFF \
    -DWITH_LIBDE265=ON -DWITH_X265=ON -DWITH_AOM_DECODER=ON -DWITH_AOM_ENCODER=ON -DWITH_DAV1D=ON \
    -DWITH_GDK_PIXBUF=OFF -DWITH_OpenJPEG_DECODER=OFF -DWITH_OpenJPEG_ENCODER=OFF -DWITH_FFMPEG_DECODER=OFF \
    -DWITH_UNCOMPRESSED_CODEC=OFF -DCMAKE_DISABLE_FIND_PACKAGE_TIFF=ON -DCMAKE_EXE_LINKER_FLAGS="$LDFLAGS"
install_tool "$SRC/libheif/build-android/examples/heif-enc" libheif heif-enc
install_tool "$SRC/libheif/build-android/examples/heif-dec" libheif heif-dec
