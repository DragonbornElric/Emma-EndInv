#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DIST_DIR="$SCRIPT_DIR/dist"
ENDINV_DIR="$SCRIPT_DIR/emma-endinv"
BUILD_LIBS_DIR="$ENDINV_DIR/build/libs"
ENDINV_JAR="emma-endinv-1.2.0.jar"

MODS_DIRS=()
GRADLE_ARGS=()

if [[ -n "${GRADLE_PROJECT_CACHE_DIR:-}" ]]; then
    GRADLE_ARGS+=(--project-cache-dir "$GRADLE_PROJECT_CACHE_DIR")
fi

usage() {
    cat <<'EOF'
Usage:
  ./build_and_deploy.sh
  ./build_and_deploy.sh --mods-dir "/path/to/mods"
  ./build_and_deploy.sh --mods-dir "/path/a" --mods-dir "/path/b"

Builds the standalone Emma-EndInv mod, copies the jar into dist/, and optionally
copies it into one or more Minecraft mods directories.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --mods-dir)
            if [[ $# -lt 2 ]]; then
                echo "ERROR: --mods-dir requires a path" >&2
                exit 1
            fi
            MODS_DIRS+=("$2")
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "ERROR: Unknown argument: $1" >&2
            usage >&2
            exit 1
            ;;
    esac
done

mkdir -p "$DIST_DIR"

echo "=== Building Emma-EndInv ==="
cd "$ENDINV_DIR"
./gradlew.bat "${GRADLE_ARGS[@]}" build 2>&1 | grep -v "^Note:" | grep -v "not valid semver" | grep -v "\[Incubating\]" | grep -v "problems-report"

if [[ ! -f "$BUILD_LIBS_DIR/$ENDINV_JAR" ]]; then
    echo "ERROR: Expected jar not found: $BUILD_LIBS_DIR/$ENDINV_JAR" >&2
    exit 1
fi

cp "$BUILD_LIBS_DIR/$ENDINV_JAR" "$DIST_DIR/$ENDINV_JAR"
echo "Staged jar: $DIST_DIR/$ENDINV_JAR"

for mods_dir in "${MODS_DIRS[@]}"; do
    if [[ ! -d "$mods_dir" ]]; then
        echo "WARNING: Mods directory not found: $mods_dir" >&2
        continue
    fi

    cp "$DIST_DIR/$ENDINV_JAR" "$mods_dir/$ENDINV_JAR"
    echo "Deployed to: $mods_dir"
done

echo "=== Done ==="
