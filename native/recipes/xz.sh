fetch xz "$XZ_URL" "$XZ_SHA256"
autotools "$SRC/xz" --disable-xz --disable-xzdec --disable-lzmadec --disable-lzmainfo --disable-scripts --disable-doc --disable-nls
