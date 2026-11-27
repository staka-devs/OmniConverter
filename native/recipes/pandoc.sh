# 公式の静的バイナリを検証して置く。動的リンカを使わないので PIE でなくても動く。ADR-0011
fetch pandoc "$PANDOC_URL" "$PANDOC_SHA256"
bin="$SRC/pandoc/bin/pandoc"
file -L "$bin" | grep -q "ARM aarch64" || { echo "aarch64 ではありません: $bin" >&2; exit 1; }
file -L "$bin" | grep -q "statically linked" || { echo "静的リンクではありません: $bin" >&2; exit 1; }
mkdir -p "$OUT/pandoc/arm64-v8a"
cp "$bin" "$OUT/pandoc/arm64-v8a/libpandoc.so"
log "OK libpandoc.so $(du -h "$bin" | cut -f1)"
