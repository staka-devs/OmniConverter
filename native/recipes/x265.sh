# x265。アセンブリと .pc に Android 向けの手直しが要る

# x265 はアセンブリを独自の規則でコンパイルし、Android の対象指定を渡さない
add_android_target_to_asm_rule() {
    sed -i "s|set(ARM_ARGS -O3)|set(ARM_ARGS -O3 --target=$TARGET$MIN_SDK)|" "$SRC/x265/source/CMakeLists.txt"
}

# 生成される x265.pc は -l-l:libunwind.a という壊れた指定と共有版の -lc++ を含む。リンクは clang++ と静的な C++ 標準ライブラリに任せる
drop_broken_link_flags_from_pc() {
    sed -i 's|^Libs.private:.*|Libs.private: -lm -ldl|' "$PREFIX/lib/pkgconfig/x265.pc"
}

fetch x265 "$X265_URL" "$X265_SHA256"
add_android_target_to_asm_rule
cmake_build "$SRC/x265/source" -DENABLE_SHARED=OFF -DENABLE_CLI=OFF
drop_broken_link_flags_from_pc
