fetch x265 "$X265_URL" "$X265_SHA256"
# x265 はアセンブリを独自の規則でコンパイルし、Android の対象指定を渡さない。対象を引数に足す
sed -i "s|set(ARM_ARGS -O3)|set(ARM_ARGS -O3 --target=$TARGET$API)|" "$SRC/x265/source/CMakeLists.txt"
cmake_build "$SRC/x265/source" -DENABLE_SHARED=OFF -DENABLE_CLI=OFF
# 生成される x265.pc は -l-l:libunwind.a という壊れた指定と共有版の -lc++ を含む。リンクは clang++ と -static-libstdc++ に任せる
sed -i 's|^Libs.private:.*|Libs.private: -lm -ldl|' "$PREFIX/lib/pkgconfig/x265.pc"
