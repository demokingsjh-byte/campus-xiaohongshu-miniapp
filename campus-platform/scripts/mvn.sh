#!/usr/bin/env bash
# 本机 Maven 启动包装脚本。
#
# 背景：本机 PATH 上的 mvn（/c/java/apache-maven-3.8.8）缺少 boot 目录，
# 且 JAVA_HOME 指向 jre1.8.0_172（没有编译器），直接 `mvn` 会报
# “找不到或无法加载主类 org.codehaus.plexus.classworlds.launcher.Launcher”
# 或 “No compiler is provided in this environment”。
#
# 用法（在 campus-platform 目录下执行）：
#   ./scripts/mvn.sh -o -pl yudao-module-campus test -Dtest=GuideModelClientTest
#   ./scripts/mvn.sh -o -pl yudao-module-campus -am clean package -DskipTests
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR_POSIX="$(cd "$SCRIPT_DIR/.." && pwd)"
MAVEN_DIR_POSIX="${MAVEN_DIR:-$PROJECT_DIR_POSIX/.mvn/apache-maven-3.9.9}"

# Windows 版 java.exe 不认 /c/... 形式的 POSIX 路径，统一转成 C:/... 形式。
to_windows_path() {
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -m "$1"
  else
    echo "$1"
  fi
}
PROJECT_DIR="$(to_windows_path "$PROJECT_DIR_POSIX")"
MAVEN_DIR="$(to_windows_path "$MAVEN_DIR_POSIX")"
JDK_DIR="${JDK_DIR:-C:/Program Files/Java/jdk1.8.0_172}"

if [ ! -x "$MAVEN_DIR/boot/plexus-classworlds-2.8.0.jar" ] && [ ! -f "$MAVEN_DIR/boot/plexus-classworlds-2.8.0.jar" ]; then
  echo "未找到 $MAVEN_DIR，请先执行 ./mvnw 下载 Maven 或设置 MAVEN_DIR" >&2
  exit 1
fi
if [ ! -x "$JDK_DIR/bin/java.exe" ]; then
  echo "未找到 JDK：$JDK_DIR，请设置 JDK_DIR 指向本机 JDK 安装目录" >&2
  exit 1
fi

export JAVA_HOME="$JDK_DIR"
exec "$JDK_DIR/bin/java.exe" \
  -classpath "$MAVEN_DIR/boot/plexus-classworlds-2.8.0.jar" \
  "-Dclassworlds.conf=$MAVEN_DIR/bin/m2.conf" \
  "-Dmaven.home=$MAVEN_DIR" \
  "-Dmaven.conf=$MAVEN_DIR/conf" \
  "-Dmaven.multiModuleProjectDirectory=$PROJECT_DIR" \
  org.codehaus.plexus.classworlds.launcher.Launcher "$@"
