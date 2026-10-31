#!/usr/bin/env bash
# Every scene through tools/exactness.py (optimisations off vs on); a scene that differs is run off vs off too, to tell
# the optimisations' differences from the scene's own nondeterminism. Summary: run/exactness-summary.txt
cd "$(dirname "$0")/.."
OUT=run/exactness-summary.txt
mkdir -p run
SCENES=${SCENES:-"restcarts items itemwake carts vehicles cramming physics hoppers furnaces smelters beactive bezoo pistons redstone crops fluids mobs villagers jobclash calmzoo zoo"}
MOBSLEEP="-Dlab.sleep.components=true -Dlab.sleep.mobRuntime=true -Dlab.opt.WriteBarrier=true"
one() {   # label, scene, args...
  local label=$1 scene=$2; shift 2
  local r; r=$(python tools/exactness.py "$scene" "$@" 2>&1 | tail -4 | tr '\n' ' ')
  echo "$label | $r" | tee -a $OUT
  echo "$r" | grep -q EXACT
}
echo "=== $(date '+%F %T')" >> $OUT
for s in $SCENES; do
  extra=(); [ "$s" = zoo ] && extra=(--monsters)
  one "$s defaults" "$s" "${extra[@]}" || one "$s off-vs-off" "$s" --offoff "${extra[@]}"
  if [ "$s" = mobs ] || [ "$s" = calmzoo ]; then
    one "$s defaults+mob sleep" "$s" --flags "$MOBSLEEP" || one "$s off-vs-off" "$s" --offoff
  fi
done
