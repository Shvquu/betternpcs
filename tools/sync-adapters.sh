#!/usr/bin/env bash
#
# Regenerates the per-version NMS adapters from the 1.21.4 reference implementation.
#
# WHY THIS EXISTS
#
# Every versions/v* module compiles against its own Paper dev bundle, which is what guarantees the
# adapter actually links against the server it will run on. The cost of that guarantee is six copies
# of code that is mostly identical. This script keeps the copies honest: the reference is edited, the
# copies are regenerated, and the handful of genuine differences between Minecraft versions are
# applied here — in one place, with a note saying what changed and when.
#
# Merging the modules whose sources come out byte-identical was considered and rejected. It would
# mean an adapter compiled against 1.21.4 running on 1.21.8, which relies on NMS staying binary
# compatible across patch releases. That is usually true and occasionally, silently, not.
#
# USAGE
#
#   tools/sync-adapters.sh            regenerate every adapter
#   ./gradlew build                   verify: each module compiles against its own bundle
#
set -euo pipefail

cd "$(dirname "$0")/.."

REFERENCE_MODULE="v1_21_4"
REFERENCE_DIR="versions/${REFERENCE_MODULE}/src/main/java/dev/shvquu/betternpcs/nms/${REFERENCE_MODULE}"

# module | adapter class | description used in javadoc and in the startup banner
TARGETS=(
  "v1_21_5|V1_21_5Adapter|1.21.5"
  "v1_21_8|V1_21_8Adapter|1.21.6 - 1.21.8"
  "v1_21_11|V1_21_11Adapter|1.21.9 - 1.21.11"
  "v26_1|V26_1Adapter|26.1"
  "v26_2|V26_2Adapter|26.2"
)

# --- Version deltas -------------------------------------------------------------------------------
#
# authlib 7 turned GameProfile from a class into a record, so getProperties() became properties().
# Paper picked that up with Minecraft 1.21.9.
AUTHLIB_RECORD_MODULES=" v1_21_11 v26_1 v26_2 "

# Minecraft 26.1 replaced ServerboundInteractPacket's visitor-style Handler with a plain record.
INTERACT_RECORD_MODULES=" v26_1 v26_2 "

echo "Reference: ${REFERENCE_MODULE}"

for target in "${TARGETS[@]}"; do
  IFS='|' read -r module adapter description <<< "$target"
  directory="versions/${module}/src/main/java/dev/shvquu/betternpcs/nms/${module}"
  mkdir -p "$directory"

  for helper in NpcEntities InteractionListener; do
    sed "s/nms\.${REFERENCE_MODULE}/nms.${module}/" \
      "${REFERENCE_DIR}/${helper}.java" > "${directory}/${helper}.java"
  done

  sed -e "s/nms\.${REFERENCE_MODULE}/nms.${module}/" \
      -e "s/V1_21_4Adapter/${adapter}/g" \
      -e "s/Minecraft 1\.21\.4\./Minecraft ${description}./" \
      -e "s/Paper 1\.21\.4 (Mojang-mapped)/Paper ${description} (Mojang-mapped)/" \
      -e "s/the Mojang-mapped 1\.21\.4 dev bundle/the Mojang-mapped ${description} dev bundle/" \
      "${REFERENCE_DIR}/V1_21_4Adapter.java" > "${directory}/${adapter}.java"

  if [[ "$AUTHLIB_RECORD_MODULES" == *" $module "* ]]; then
    sed -i 's/profile\.getProperties()/profile.properties()/' "${directory}/NpcEntities.java"
  fi

  if [[ "$INTERACT_RECORD_MODULES" == *" $module "* ]]; then
    python3 tools/patch-interact-record.py "${directory}/InteractionListener.java"
  fi

  echo "  regenerated ${module} (${description})"
done

echo
echo "Now run: ./gradlew build"
