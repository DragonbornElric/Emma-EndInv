#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DIST_DIR="$SCRIPT_DIR/dist"
ENDINV_DIR="$SCRIPT_DIR/emma-endinv"
GRADLE_PROPS="$ENDINV_DIR/gradle.properties"

MC_VERSION=$(awk -F= '/^minecraft_version=/{print $2}' "$GRADLE_PROPS")
FABRIC_LOADER_VERSION=$(awk -F= '/^fabric_loader_version=/{print $2}' "$GRADLE_PROPS")
MOD_VERSION=$(awk -F= '/^version=/{print $2}' "$GRADLE_PROPS")
MOD_ID=$(awk -F= '/^mod_id=/{print $2}' "$GRADLE_PROPS")
# Jar prefix before the 1.3 rename to emma_endinv; old jars with it are removed when deploying.
LEGACY_MOD_ID="endless_inventory"

FABRIC_JAR_GLOB="${ENDINV_DIR}/fabric/build/libs/${MOD_ID}-fabric-${MC_VERSION}*.jar"
NEOFORGE_JAR_GLOB="${ENDINV_DIR}/neoforge/build/libs/${MOD_ID}-neoforge-${MC_VERSION}*.jar"
FOLIA_VERSION=$(awk -F= '/^folia_version=/{print $2}' "$GRADLE_PROPS")
PAPER_VERSION=$(awk -F= '/^paper_version=/{print $2}' "$GRADLE_PROPS")
FOLIA_JAR_GLOB="${ENDINV_DIR}/folia/build/libs/${MOD_ID}-folia-${MC_VERSION}*.jar"

PRISM_INSTANCES_DIR="$APPDATA/PrismLauncher/instances"
FABRIC_MODS_DIR="C:/Users/Owner/Fabric Mods"

# Remote Emma instance on emmabrain (Linux box, Flatpak PrismLauncher).
# Host/user/port come from ~/.ssh/config (Host emmabrain).
EMMABRAIN_HOST="emmabrain"
EMMABRAIN_MODS="/home/emmabrain/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/EmmaAI/minecraft/mods"

# The local instances, emmabrain and the default drop folders run this Minecraft version.
# Builds of other versions (the mc-26.2 / mc-26.3 branches) are only staged in dist/,
# unless a --*-mods-dir is given explicitly.
LOCAL_MC_VERSION="26.1.2"
AUTO_DEPLOY=1
[[ "$MC_VERSION" != "$LOCAL_MC_VERSION" ]] && AUTO_DEPLOY=0

FABRIC_MODS_DIRS=()
NEOFORGE_MODS_DIRS=()
FOLIA_MODS_DIRS=()
if [[ $AUTO_DEPLOY -eq 1 ]]; then
    NEOFORGE_MODS_DIRS=("C:/Users/Owner/Neoforge Mods")
    FOLIA_MODS_DIRS=("C:/Users/Owner/Paper Plugins")
fi
GRADLE_ARGS=()

# Copy a jar into a mods/plugins folder, first removing any other build of it for that loader
# (older versions, and jars still named with the pre-1.3 endless_inventory id), so the folder
# never holds two copies of the mod.
install_jar() {
    local jar="$1" dir="$2" loader="$3"
    local name; name="$(basename "$jar")"
    local old
    for old in "$dir/${MOD_ID}-${loader}-"*.jar "$dir/${LEGACY_MOD_ID}-${loader}-"*.jar; do
        [[ -e "$old" && "$(basename "$old")" != "$name" ]] && rm -f "$old" && echo "  removed old $(basename "$old")"
    done
    cp "$jar" "$dir/$name"
}

resolve_mods_dir() {
    for instance_name in "$@"; do
        local modern="$PRISM_INSTANCES_DIR/$instance_name/minecraft/mods"
        if [[ -d "$modern" ]]; then echo "$modern"; return 0; fi
        local legacy="$PRISM_INSTANCES_DIR/$instance_name/.minecraft/mods"
        if [[ -d "$legacy" ]]; then echo "$legacy"; return 0; fi
    done
    return 1
}

if EMMA_MODS="$(resolve_mods_dir "Emma 26.1" "Emma")"; then : ; else
    EMMA_MODS="$PRISM_INSTANCES_DIR/Emma 26.1/minecraft/mods"
fi
if ELRIC_MODS="$(resolve_mods_dir "Elric 26.1" "Elric")"; then : ; else
    ELRIC_MODS="$PRISM_INSTANCES_DIR/Elric/minecraft/mods"
fi
if CAMERABOT_MODS="$(resolve_mods_dir "CameraBot26.1")"; then : ; else
    CAMERABOT_MODS="$PRISM_INSTANCES_DIR/CameraBot26.1/minecraft/mods"
fi
if BRANDON_MODS="$(resolve_mods_dir "Brandon 26.1")"; then : ; else
    BRANDON_MODS="$PRISM_INSTANCES_DIR/Brandon 26.1/minecraft/mods"
fi

if [[ -z "${GRADLE_PROJECT_CACHE_DIR:-}" && -n "${LOCALAPPDATA:-}" ]]; then
    GRADLE_PROJECT_CACHE_DIR="${LOCALAPPDATA}\\Temp\\emma-endinv-gradle-cache"
fi
if [[ -n "${GRADLE_PROJECT_CACHE_DIR:-}" ]]; then
    GRADLE_ARGS+=(--project-cache-dir "$GRADLE_PROJECT_CACHE_DIR")
fi

usage() {
    cat <<'EOF'
Usage:
  ./build_and_deploy.sh [options]

Options:
  --fabric-mods-dir "/path"    Deploy Fabric jar to additional directory (repeatable)
  --neoforge-mods-dir "/path"  Deploy NeoForge jar to additional directory (repeatable)
  --folia-mods-dir "/path"     Deploy Folia jar to a plugins/ directory (repeatable)
  --skip-neoforge               Skip NeoForge build
  --skip-folia                  Skip Folia build (default: Folia is built but not auto-deployed)
  --emmabrain-only              Build Fabric only, deploy only to emmabrain (EmmaAI); implies --skip-neoforge --skip-folia
  -h, --help

Builds Fabric, NeoForge, and Folia jars. The Fabric jar deploys to the default
PrismLauncher Emma, Elric, and CameraBot26.1 instances and the Fabric Mods folder,
plus the Emma instance on emmabrain (EmmaAI, via ssh/scp — see ~/.ssh/config).
The NeoForge jar deploys to C:/Users/Owner/Neoforge Mods and any --neoforge-mods-dir targets.
The Folia jar deploys to C:/Users/Owner/Paper Plugins and any --folia-mods-dir targets.
EOF
}

SKIP_NEOFORGE=0
SKIP_FOLIA=0
EMMABRAIN_ONLY=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        --fabric-mods-dir)
            [[ $# -lt 2 ]] && { echo "ERROR: --fabric-mods-dir requires a path" >&2; exit 1; }
            FABRIC_MODS_DIRS+=("$2"); shift 2 ;;
        --neoforge-mods-dir)
            [[ $# -lt 2 ]] && { echo "ERROR: --neoforge-mods-dir requires a path" >&2; exit 1; }
            NEOFORGE_MODS_DIRS+=("$2"); shift 2 ;;
        --folia-mods-dir)
            [[ $# -lt 2 ]] && { echo "ERROR: --folia-mods-dir requires a path" >&2; exit 1; }
            FOLIA_MODS_DIRS+=("$2"); shift 2 ;;
        # Legacy compat: --mods-dir deploys the Fabric jar
        --mods-dir)
            [[ $# -lt 2 ]] && { echo "ERROR: --mods-dir requires a path" >&2; exit 1; }
            FABRIC_MODS_DIRS+=("$2"); shift 2 ;;
        --skip-neoforge)
            SKIP_NEOFORGE=1; shift ;;
        --skip-folia)
            SKIP_FOLIA=1; shift ;;
        --emmabrain-only)
            EMMABRAIN_ONLY=1; SKIP_NEOFORGE=1; SKIP_FOLIA=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "ERROR: Unknown argument: $1" >&2; usage >&2; exit 1 ;;
    esac
done

mkdir -p "$DIST_DIR"

echo "=== Building Emma-EndInv ==="
cd "$ENDINV_DIR"

quiet_gradle() {
    grep -v "^Note:" \
    | grep -v "not valid semver" \
    | grep -v "\[Incubating\]" \
    | grep -v "problems-report" \
    | grep -v "^\*\*\*" \
    | grep -v "^ DONE \| REUSE\| DL " \
    | grep -v "Waiting for lock" \
    | grep -v "^Loaded [0-9]* artifacts" \
    | grep -v "^Total runtime:" \
    | grep -v "^> Task :" \
    | grep -v "honour the JVM settings" \
    | grep -v "Daemon will be stopped" \
    | grep -v "single-use Daemon" \
    | grep -v "warning: \[removal\]" \
    | grep -v "Deprecated Gradle features" \
    | grep -v "making it incompatible" \
    | grep -v "You can use '--warning-mode" \
    | grep -v "please refer to https://" \
    | grep -v "^$"
}

# No server-plugin dev bundle pinned for this Minecraft version (folia_version and paper_version empty).
[[ -z "$FOLIA_VERSION" && -z "$PAPER_VERSION" ]] && SKIP_FOLIA=1

GRADLE_TARGETS=(:fabric:build)
[[ $SKIP_NEOFORGE -eq 0 ]] && GRADLE_TARGETS+=(:neoforge:build)
[[ $SKIP_FOLIA -eq 0 ]] && GRADLE_TARGETS+=(:folia:build)
./gradlew.bat "${GRADLE_ARGS[@]}" "${GRADLE_TARGETS[@]}" 2>&1 | quiet_gradle

# Find the built jars (glob avoids hardcoding exact classifier/version suffix)
FABRIC_JAR=$(ls ${ENDINV_DIR}/fabric/build/libs/${MOD_ID}-fabric-${MC_VERSION}*.jar 2>/dev/null | grep -v sources | grep -v javadoc | head -1 || true)
NEOFORGE_JAR=$(ls ${ENDINV_DIR}/neoforge/build/libs/${MOD_ID}-neoforge-${MC_VERSION}*.jar 2>/dev/null | grep -v sources | grep -v javadoc | head -1 || true)
FOLIA_JAR=$(ls ${ENDINV_DIR}/folia/build/libs/${MOD_ID}-folia-${MC_VERSION}*.jar 2>/dev/null | grep -v sources | grep -v javadoc | head -1 || true)

if [[ -z "$FABRIC_JAR" ]]; then
    echo "ERROR: Fabric jar not found under fabric/build/libs/" >&2; exit 1
fi

FABRIC_JAR_NAME="$(basename "$FABRIC_JAR")"
cp "$FABRIC_JAR" "$DIST_DIR/$FABRIC_JAR_NAME"
echo "Staged Fabric jar: $DIST_DIR/$FABRIC_JAR_NAME"

if [[ $SKIP_NEOFORGE -eq 0 ]]; then
    if [[ -z "$NEOFORGE_JAR" ]]; then
        echo "ERROR: NeoForge jar not found under neoforge/build/libs/" >&2; exit 1
    fi
    NEOFORGE_JAR_NAME="$(basename "$NEOFORGE_JAR")"
    cp "$NEOFORGE_JAR" "$DIST_DIR/$NEOFORGE_JAR_NAME"
    echo "Staged NeoForge jar: $DIST_DIR/$NEOFORGE_JAR_NAME"
fi

if [[ $AUTO_DEPLOY -eq 0 ]]; then
    echo "Minecraft $MC_VERSION is not the local version ($LOCAL_MC_VERSION): jars staged in dist/ only"
elif [[ $EMMABRAIN_ONLY -eq 0 ]]; then
    echo "=== Deploying Fabric jar to default instances ==="

    for named_target in \
        "Emma:$EMMA_MODS" \
        "Elric:$ELRIC_MODS" \
        "CameraBot26.1:$CAMERABOT_MODS" \
        "Brandon 26.1:$BRANDON_MODS" \
        "Fabric Mods:$FABRIC_MODS_DIR"; do
        target_name="${named_target%%:*}"
        target_dir="${named_target#*:}"
        if [[ ! -d "$target_dir" ]]; then
            echo "WARNING: $target_name mods folder not found: $target_dir" >&2; continue
        fi
        install_jar "$FABRIC_JAR" "$target_dir" fabric
        echo "Deployed Fabric to $target_name: $target_dir"
    done
fi

# Emma on emmabrain: same jar as local Emma, deployed over ssh/scp (Flatpak PrismLauncher, Linux box).
if [[ $AUTO_DEPLOY -eq 0 ]]; then
    :
elif ssh -o ConnectTimeout=5 -o BatchMode=yes "$EMMABRAIN_HOST" "mkdir -p '$EMMABRAIN_MODS'" 2>/dev/null; then
    ssh -o BatchMode=yes "$EMMABRAIN_HOST" "cd '$EMMABRAIN_MODS' && for f in ${MOD_ID}-fabric-*.jar ${LEGACY_MOD_ID}-fabric-*.jar; do [ -e \"\$f\" ] && [ \"\$f\" != '$FABRIC_JAR_NAME' ] && rm -f \"\$f\" && echo \"  removed old \$f\"; done; true"
    scp "$FABRIC_JAR" "$EMMABRAIN_HOST:$EMMABRAIN_MODS/$FABRIC_JAR_NAME"
    echo "Deployed Fabric to emmabrain (EmmaAI): $EMMABRAIN_MODS"
else
    echo "WARNING: emmabrain unreachable via ssh, skipping remote deploy" >&2
fi

for mods_dir in "${FABRIC_MODS_DIRS[@]}"; do
    if [[ ! -d "$mods_dir" ]]; then
        echo "WARNING: Fabric mods dir not found: $mods_dir" >&2; continue
    fi
    install_jar "$FABRIC_JAR" "$mods_dir" fabric
    echo "Deployed Fabric to: $mods_dir"
done

if [[ $SKIP_NEOFORGE -eq 0 && ${#NEOFORGE_MODS_DIRS[@]} -gt 0 ]]; then
    echo "=== Deploying NeoForge jar ==="
    for mods_dir in "${NEOFORGE_MODS_DIRS[@]}"; do
        if [[ ! -d "$mods_dir" ]]; then
            echo "WARNING: NeoForge mods dir not found: $mods_dir" >&2; continue
        fi
        install_jar "$NEOFORGE_JAR" "$mods_dir" neoforge
        echo "Deployed NeoForge to: $mods_dir"
    done
fi

if [[ $SKIP_FOLIA -eq 0 ]]; then
    if [[ -z "$FOLIA_JAR" ]]; then
        echo "ERROR: Folia jar not found under folia/build/libs/" >&2; exit 1
    fi
    FOLIA_JAR_NAME="$(basename "$FOLIA_JAR")"
    cp "$FOLIA_JAR" "$DIST_DIR/$FOLIA_JAR_NAME"
    echo "Staged Folia jar: $DIST_DIR/$FOLIA_JAR_NAME"
    if [[ ${#FOLIA_MODS_DIRS[@]} -gt 0 ]]; then
        echo "=== Deploying Folia jar ==="
        for plugins_dir in "${FOLIA_MODS_DIRS[@]}"; do
            if [[ ! -d "$plugins_dir" ]]; then
                echo "WARNING: Folia plugins dir not found: $plugins_dir" >&2; continue
            fi
            install_jar "$FOLIA_JAR" "$plugins_dir" folia
            echo "Deployed Folia to: $plugins_dir"
        done
    fi
fi

echo "=== Done ==="
