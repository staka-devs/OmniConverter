fetch libvpx "$LIBVPX_URL" "$LIBVPX_SHA256"
(cd "$SRC/libvpx" && ./configure --target=arm64-android-gcc --prefix="$PREFIX" --enable-pic --enable-vp9-highbitdepth \
    --disable-examples --disable-tools --disable-docs --disable-unit-tests && make -j"$JOBS" && make install)
