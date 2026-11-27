fetch dav1d "$DAV1D_URL" "$DAV1D_SHA256"
meson_build "$SRC/dav1d" -Denable_tools=false -Denable_tests=false
