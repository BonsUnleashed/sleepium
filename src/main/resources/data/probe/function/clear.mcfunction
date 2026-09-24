# probe:clear - remove the scene
kill @e[type=!minecraft:player]
fill -1 -60 -21 80 -59 -9 minecraft:air
fill 0 -60 0 31 -56 31 minecraft:air
fill 40 -60 0 55 -56 15 minecraft:air
kill @e[type=minecraft:item]
