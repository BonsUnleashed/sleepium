# sleep:lag_furnaces - 1024 burning furnaces (block entity tick)
# area x 500..532, z 100..132
gamerule doDaylightCycle false
gamerule doWeatherCycle false
gamerule doMobSpawning false
gamerule doTraderSpawning false
gamerule doPatrolSpawning false
gamerule doInsomnia false
gamerule doMobLoot false
gamerule randomTickSpeed 3
time set noon
weather clear
forceload add 500 100 532 132
kill @e[type=!minecraft:player,x=500,y=-61,z=100,dx=32,dy=8,dz=32]
fill 500 -60 100 532 -54 132 minecraft:air
fill 500 -60 100 531 -60 131 minecraft:furnace[facing=north]{Items:[{Slot:0b,id:"minecraft:cobblestone",count:64},{Slot:1b,id:"minecraft:coal_block",count:64}]}
tellraw @a {"text":"sleep: lag_furnaces built","color":"yellow"}
