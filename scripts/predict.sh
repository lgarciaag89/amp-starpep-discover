#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
APP_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
JAR="$APP_DIR/target/amp-starpep-discover.jar"

if [ -n "${JAVA:-}" ]; then
    JAVA_CMD=$JAVA
elif [ -n "${JAVA_HOME:-}" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
else
    JAVA_CMD=java
fi

if ! command -v "$JAVA_CMD" >/dev/null 2>&1; then
    printf 'Java was not found. Install JDK 21 or set JAVA_HOME/JAVA.\n' >&2
    exit 1
fi

JAVA_VERSION=$(
    "$JAVA_CMD" -XshowSettings:properties -version 2>&1 |
        sed -n 's/^[[:space:]]*java.specification.version = //p' |
        head -n 1
)
case "$JAVA_VERSION" in
    ''|*[!0-9]*)
        printf 'Could not determine the Java version from %s.\n' "$JAVA_CMD" >&2
        exit 1
        ;;
esac
if [ "$JAVA_VERSION" -lt 21 ]; then
    printf 'Java 21 or newer is required; found Java %s.\n' "$JAVA_VERSION" >&2
    exit 1
fi

if [ ! -f "$JAR" ]; then
    printf 'Packaged application not found. Run "mvn clean package" in %s first.\n' \
        "$APP_DIR" >&2
    exit 1
fi

exec "$JAVA_CMD" -cp "$JAR" tomocomd.PredictPeptidesCli "$@"
