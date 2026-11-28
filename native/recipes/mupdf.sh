# mutool だけを同梱する。依存は MuPDF の配布物に含まれるものを使う
fetch mupdf "$MUPDF_URL" "$MUPDF_SHA256"
# 自前でビルドしたライブラリの見出しと混ざらないよう、共通の CFLAGS と LDFLAGS は渡さない
mupdf_make() {
    CFLAGS="-O2" CXXFLAGS="-O2" LDFLAGS="$PAGE_ALIGN_16K" make -C "$SRC/mupdf" OS=android build=release shared=no verbose=no \
        HAVE_X11=no HAVE_GLUT=no HAVE_OBJCOPY=no HAVE_LIBCRYPTO=no HAVE_CURL=no HAVE_PTHREAD=yes \
        HAVE_TESSERACT=no HAVE_LEPTONICA=no HAVE_ZXINGCPP=no HAVE_LIBARCHIVE=no PTHREAD_LIBS= \
        CC="$CC" CXX="$CXX" AR="$AR" XCFLAGS="-fPIC" XLIBS="$STATIC_CXX_RUNTIME $STATIC_CXX_LIBS -llog" "$@"
}
mupdf_make generate
mupdf_make -j"$JOBS" tools
install_tool "$(find "$SRC/mupdf/build" -name mutool -type f | head -1)" mupdf mutool
