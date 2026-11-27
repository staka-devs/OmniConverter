fetch opus "$OPUS_URL" "$OPUS_SHA256"
autotools "$SRC/opus" --disable-doc --disable-extra-programs
