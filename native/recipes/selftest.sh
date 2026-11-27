# ツールチェーンの確認用。C と C++ の小さな実行ファイルを作り、同梱できる形かを検査する
mkdir -p "$WORK/selftest"
cat > "$WORK/selftest/hello.cpp" <<'CPP'
#include <cstdio>
#include <string>
int main() { std::string s = "omni"; std::printf("%s\n", s.c_str()); return 0; }
CPP
"$CXX" $CXXFLAGS $LDFLAGS -o "$WORK/selftest/hello" "$WORK/selftest/hello.cpp"
install_tool "$WORK/selftest/hello" selftest hello
