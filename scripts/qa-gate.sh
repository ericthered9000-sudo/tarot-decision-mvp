#!/bin/bash
#
# qa-gate.sh <project-dir> [--full]
# Keystone Digital pre-handoff quality gate.
#   Fast mode (default): TypeScript check + configured lint. Runs on every git push (hook-installed).
#   --full: also runs the full Android validation pipeline (lint -> detekt -> test -> assembleDebug)
#           via the validation-pipeline skill. Run before ANY Bob->Tessa handoff or release build.
#
# Exit codes: 0 = PASS, 1 = checks failed, 2 = nothing to check (config problem)
# Per SOUL.md: no code ships without Tessa's PASS — this gate is the automated floor, not the ceiling.

set -u
PROJ="${1:-}"
FULL=0
[ "${2:-}" = "--full" ] && FULL=1
[ -z "$PROJ" ] && { echo "usage: qa-gate.sh <project-dir> [--full]"; exit 2; }
[ -d "$PROJ" ] || { echo "not a directory: $PROJ"; exit 2; }
cd "$PROJ" || exit 2

FAILED=0
PASS=()
FAIL=()
SKIP=()

note() { echo -e "[qa-gate] $1"; }

# ---- 1. TypeScript ----
if [ -f tsconfig.json ] || [ -f app.config.ts ] || compgen -G "*.ts" > /dev/null; then
  if [ -x node_modules/.bin/tsc ]; then
    note "-- tsc --noEmit ..."
    if node_modules/.bin/tsc --noEmit > /tmp/qag-gate-tsc.log 2>&1; then
      PASS+=("tsc")
      note "   tsc: PASS"
    else
      FAILED=1
      FAIL+=("tsc")
      note "   tsc: FAIL"
      head -30 /tmp/qag-gate-tsc.log | sed 's/^/   | /'
    fi
  else
    SKIP+=("tsc (node_modules missing)")
  fi
fi

# ---- 2. lint (package.json script) ----
if [ -f package.json ] && node -e "const p=require('./package.json'); process.exit(p.scripts&&p.scripts.lint?0:1)" 2>/dev/null; then
  note "-- npm run lint ..."
  if npm run lint --silent > /tmp/qag-gate-lint.log 2>&1; then
    PASS+=("lint")
    note "   lint: PASS"
  else
    FAILED=1
    FAIL+=("lint")
    note "   lint: FAIL"
    head -20 /tmp/qag-gate-lint.log | sed 's/^/   | /'
  fi
else
  SKIP+=("lint (no script)")
fi

# ---- 3. Android validation pipeline (full mode) ----
if [ "$FULL" = "1" ]; then
  ANDROID_DIR=""
  [ -d android ] && [ -x android/gradlew ] && ANDROID_DIR="$PROJ/android"
  [ -z "$ANDROID_DIR" ] && [ -x android ] && [ -f "$PROJ/android/gradlew" ] && ANDROID_DIR="$PROJ/android"
  if [ -x "$ANDROID_DIR/gradlew" ]; then
    note "-- android validate.sh (lint/detekt/test/build) — this takes minutes ..."
    if bash /home/james/.hermes/skills/openclaw-imports/validation-pipeline/scripts/validate.sh "$ANDROID_DIR"; then
      PASS+=("android-pipeline")
    else
      FAILED=1
      FAIL+=("android-pipeline")
    fi
  else
    SKIP+=("android-pipeline (no gradlew)")
  fi
else
  SKIP+=("android-pipeline (fast mode; run qa-gate.sh <dir> --full before releases)")
fi

echo
echo "================ qa-gate verdict: $PROJ ================"
[ ${#PASS[@]} -gt 0 ] && echo "  PASS: ${PASS[*]}"
[ ${#SKIP[@]} -gt 0 ] && echo "  SKIP: ${SKIP[*]}"
[ ${#FAIL[@]} -gt 0 ] && echo "  FAIL: ${FAIL[*]}  <=== PUSH/HANDOFF BLOCKED"
echo "  Verdict: $([ $FAILED -eq 0 ] && echo PASS || echo FAIL)"
exit $FAILED