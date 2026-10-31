# sleep:lag_fluids - continuous water and lava flow (scheduled fluid ticks): sources on a grid are re-placed every
# 60 ticks by sleep:fluid_cycle after the flowing fluid is cleared. area x 1100..1164, z 100..164
gamerule doMobSpawning false
time set noon
weather clear
forceload add 1100 100 1164 164
kill @e[type=!minecraft:player,x=1100,y=-61,z=100,dx=64,dy=8,dz=64]
fill 1100 -60 100 1164 -55 164 minecraft:air
fill 1100 -60 100 1164 -60 164 minecraft:stone
fill 1100 -59 132 1164 -57 132 minecraft:stone
function sleep:fluid_cycle
