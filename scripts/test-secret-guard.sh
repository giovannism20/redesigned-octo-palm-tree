#!/usr/bin/env bash
# Self-test for the pre-commit secret guard (.githooks/pre-commit).
# Builds a throwaway repository, tries to commit forbidden files and a fake
# token generated at runtime (so no secret-looking literal lives in this file),
# and asserts the hook blocks them while allowing a normal file.
set -euo pipefail

repo_root=$(git rev-parse --show-toplevel)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

git init -q "$work"
cp -r "$repo_root/.githooks" "$work/.githooks"
cp "$repo_root/.gitleaks.toml" "$work/.gitleaks.toml"
cd "$work"
git config core.hooksPath .githooks
git config user.email "guard-test@example.invalid"
git config user.name "Guard Test"

failures=0
expect_blocked() {
  local label=$1
  if git commit -q -m "test: $label" >/dev/null 2>&1; then
    echo "FAIL: $label was committed"
    failures=$((failures + 1))
    git reset -q --soft HEAD~1 2>/dev/null || true
  else
    echo "ok:   $label blocked"
  fi
  git reset -q
}

# 1. Signing keystore.
head -c 64 /dev/urandom > release.jks
git add -f release.jks
expect_blocked "keystore (.jks)"
rm -f release.jks

# 2. Secrets file force-added past .gitignore.
printf 'apiKey=placeholder\n' > secrets.properties
git add -f secrets.properties
expect_blocked "secrets.properties"
rm -f secrets.properties

# 3. Fake GitHub token assembled at runtime.
prefix="gh""p_"
alphabet=ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789
body=""
for _ in $(seq 36); do body+=${alphabet:RANDOM%62:1}; done
printf 'const val TOKEN = "%s%s"\n' "$prefix" "$body" > Leak.kt
git add Leak.kt
expect_blocked "hard-coded token"
rm -f Leak.kt

# 4. A normal file must pass.
printf 'fun main() = println("hello")\n' > Main.kt
git add Main.kt
if git commit -q -m "test: normal file" >/dev/null 2>&1; then
  echo "ok:   normal file committed"
else
  echo "FAIL: normal file was blocked"
  failures=$((failures + 1))
fi

if [ "$failures" -ne 0 ]; then
  echo "secret guard self-test: $failures failure(s)"
  exit 1
fi
echo "secret guard self-test: all checks passed"
