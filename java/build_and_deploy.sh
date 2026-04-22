#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DIST_DIR="$SCRIPT_DIR/dist"
ENDINV_DIR="$SCRIPT_DIR/emma-endinv"
BUILD_LIBS_DIR="$ENDINV_DIR/build/libs"
ENDINV_JAR="emma-endinv-1.2.0.jar"
PRISM_INSTANCES_DIR="$APPDATA/PrismLauncher/instances"

MODS_DIRS=()
GRADLE_ARGS=()

# Resolve a Prism instance mods folder, supporting both MC 26.x layout
# (<instance>/minecraft/mods) and legacy layout (<instance>/.minecraft/mods).
# Accepts multiple instance names and returns the first existing directory.
resolve_mods_dir() {
    for instance_name in "$@"; do
        local modern="$PRISM_INSTANCES_DIR/$instance_name/minecraft/mods"
        if [[ -d "$modern" ]]; then
            echo "$modern"
            return 0
        fi

        local legacy="$PRISM_INSTANCES_DIR/$instance_name/.minecraft/mods"
        if [[ -d "$legacy" ]]; then
            echo "$legacy"
            return 0
        fi
    done

    return 1
}

# Try modern/renamed instances first, then legacy names.
if EMMA_MODS_RESOLVED="$(resolve_mods_dir "Emma 26.1" "Emma")"; then
    EMMA_MODS="$EMMA_MODS_RESOLVED"
else
    EMMA_MODS="$PRISM_INSTANCES_DIR/Emma 26.1/minecraft/mods"
fi

if ELRIC_MODS_RESOLVED="$(resolve_mods_dir "Elric 26.1" "Elric")"; then
    ELRIC_MODS="$ELRIC_MODS_RESOLVED"
else
    ELRIC_MODS="$PRISM_INSTANCES_DIR/Elric/minecraft/mods"
fi

if ALTOCLEF_MODS_RESOLVED="$(resolve_mods_dir "Emma 26.1 EmmaClef")"; then
    ALTOCLEF_MODS="$ALTOCLEF_MODS_RESOLVED"
else
    ALTOCLEF_MODS="$PRISM_INSTANCES_DIR/Emma 26.1 EmmaClef/minecraft/mods"
fi

# Default to a Temp-backed Gradle project cache on Windows so VS Code's Java
# tooling does not lock the module-local Loom cache during bash.exe runs.
if [[ -z "${GRADLE_PROJECT_CACHE_DIR:-}" && -n "${LOCALAPPDATA:-}" ]]; then
    GRADLE_PROJECT_CACHE_DIR="${LOCALAPPDATA}\\Temp\\emma-endinv-gradle-cache"
fi

if [[ -n "${GRADLE_PROJECT_CACHE_DIR:-}" ]]; then
    GRADLE_ARGS+=(--project-cache-dir "$GRADLE_PROJECT_CACHE_DIR")
fi

usage() {
    cat <<'EOF'
Usage:
  ./build_and_deploy.sh
  ./build_and_deploy.sh --mods-dir "/path/to/mods"
  ./build_and_deploy.sh --mods-dir "/path/a" --mods-dir "/path/b"

Builds the standalone Emma-EndInv mod, copies the jar into dist/, deploys it to
the default PrismLauncher Emma, Elric, and AltoClef instances, and optionally
copies it into one or more additional Minecraft mods directories.
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

echo "=== Deploying to PrismLauncher mods folders ==="

for named_target in \
    "Emma:$EMMA_MODS" \
    "Elric:$ELRIC_MODS" \
    "Emma 26.1 AltoClef:$ALTOCLEF_MODS"; do
    target_name="${named_target%%:*}"
    target_dir="${named_target#*:}"

    if [[ ! -d "$target_dir" ]]; then
        echo "WARNING: $target_name mods folder not found at $target_dir" >&2
        continue
    fi

    cp "$DIST_DIR/$ENDINV_JAR" "$target_dir/$ENDINV_JAR"
    echo "Deployed to $target_name: $target_dir"
done

for mods_dir in "${MODS_DIRS[@]}"; do
    if [[ ! -d "$mods_dir" ]]; then
        echo "WARNING: Mods directory not found: $mods_dir" >&2
        continue
    fi

    cp "$DIST_DIR/$ENDINV_JAR" "$mods_dir/$ENDINV_JAR"
    echo "Deployed to: $mods_dir"
done

echo "=== Done ==="
