for dep in libjpeg xz zstd libwebp; do need $dep; done
fetch libtiff "$LIBTIFF_URL" "$LIBTIFF_SHA256"
cmake_build "$SRC/libtiff" -Dtiff-tools=OFF -Dtiff-tests=OFF -Dtiff-docs=OFF -Dtiff-contrib=OFF \
    -Djbig=OFF -Dlerc=OFF -Dlibdeflate=OFF -Djpeg=ON -Dzlib=ON -Dlzma=ON -Dzstd=ON -Dwebp=ON
