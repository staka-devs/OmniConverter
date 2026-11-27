fetch lz4 "$LZ4_URL" "$LZ4_SHA256"
cmake_build "$SRC/lz4/build/cmake" -DLZ4_BUILD_CLI=OFF -DBUILD_STATIC_LIBS=ON
