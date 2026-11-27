fetch x264 "$X264_URL" "$X264_SHA256"
(cd "$SRC/x264" && ./configure --host=$TARGET --cross-prefix="$TOOLCHAIN/bin/llvm-" --sysroot="$SYSROOT" \
    --prefix="$PREFIX" --enable-static --enable-pic --disable-cli && make -j"$JOBS" && make install)
