fetch lame "$LAME_URL" "$LAME_SHA256"
# 古い config.sub は aarch64-linux-android を知らないので差し替える
cp /usr/share/misc/config.sub /usr/share/misc/config.guess "$SRC/lame/" 2>/dev/null \
    || cp /usr/share/automake-*/config.sub /usr/share/automake-*/config.guess "$SRC/lame/"
autotools "$SRC/lame" --disable-frontend --disable-gtktest --disable-decoder
