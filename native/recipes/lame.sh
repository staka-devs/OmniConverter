fetch lame "$LAME_URL" "$LAME_SHA256"
refresh_config_scripts "$SRC/lame"
autotools "$SRC/lame" --disable-frontend --disable-gtktest --disable-decoder
