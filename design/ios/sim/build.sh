#!/bin/zsh
# 编译模拟器外壳：./build.sh [品牌图标 png] [输出目录]
# 只用 Xcode 自带的 swiftc / actool / codesign，不需要 Xcode 工程、不联网、不装东西。产物不进仓库。
set -euo pipefail
HERE=${0:A:h}
ICON=${1:-$HERE/../../brand/icon-512.png}
OUT=${2:-${TMPDIR:-/tmp}/gugu-ios-sim}
SDK=$(xcrun -sdk iphonesimulator --show-sdk-path)
APP="$OUT/GuguDemo.app"
WORK="$OUT/work"
rm -rf "$APP" "$WORK"; mkdir -p "$APP" "$WORK"

# 资源目录：图标（1024，去透明）+ 启动底色
XC="$WORK/Assets.xcassets"
mkdir -p "$XC/AppIcon.appiconset" "$XC/LaunchBlack.colorset"
print '{"info":{"author":"xcode","version":1}}' > "$XC/Contents.json"
sips -s format jpeg -z 1024 1024 "$ICON" --out "$WORK/icon.jpg" >/dev/null
sips -s format png "$WORK/icon.jpg" --out "$XC/AppIcon.appiconset/icon-1024.png" >/dev/null
print '{"images":[{"filename":"icon-1024.png","idiom":"universal","platform":"ios","size":"1024x1024"}],"info":{"author":"xcode","version":1}}' > "$XC/AppIcon.appiconset/Contents.json"
print '{"colors":[{"color":{"color-space":"srgb","components":{"alpha":"1.000","red":"0x10","green":"0x0E","blue":"0x0B"}},"idiom":"universal"}],"info":{"author":"xcode","version":1}}' > "$XC/LaunchBlack.colorset/Contents.json"

xcrun actool "$XC" --compile "$APP" --platform iphonesimulator --minimum-deployment-target 17.0 \
  --target-device iphone --app-icon AppIcon --output-partial-info-plist "$WORK/partial.plist" \
  --output-format human-readable-text --notices --warnings >/dev/null

# Info.plist = 模板 + actool 生成的图标键
cp "$HERE/Info.plist" "$APP/Info.plist"
/usr/libexec/PlistBuddy -x -c "Merge $WORK/partial.plist" "$APP/Info.plist" >/dev/null
plutil -convert binary1 "$APP/Info.plist"

xcrun -sdk iphonesimulator swiftc -parse-as-library -O \
  -target arm64-apple-ios17.0-simulator -sdk "$SDK" -module-name GuguDemo \
  "$HERE/App.swift" -o "$APP/GuguDemo"

codesign --force --sign - --timestamp=none "$APP" >/dev/null
echo "$APP"
