#!/usr/bin/env bash
# commit.sh <version folder> "<message>" <paths...>: builds that version's folder (a worktree of its branch) and commits
# the paths there only when it compiles; then says what the other versions do not have yet (tools/notported.py).
# The co-author trailer is added. Run from anywhere: the folder is a sibling of this one (sleepium/<branch>/).
set -u
HERE="$(cd "$(dirname "$0")/.." && pwd)"
DIR="$HERE/../$1"; shift
MSG=$1; shift
cd "$DIR" || exit 1
OUT=$(timeout 900 ./gradlew compileJava -q 2>&1)
if echo "$OUT" | grep -qE "error:|FAILED|BUILD FAILED"; then
  echo "build failed, nothing committed:"; echo "$OUT" | grep -E "error|\.java:" | head -10; exit 1
fi
git add -A "$@" && git commit -q -F - <<MSG_EOF
$MSG

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG_EOF
git log --oneline -1 | cut -c1-100
python "$HERE/tools/notported.py" || echo "(carry these over with git cherry-pick -x, or mark them Only: <branch>)"
