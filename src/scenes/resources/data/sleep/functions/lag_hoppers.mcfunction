# sleep:lag_hoppers - 1024 empty hoppers (item-suck AABB scan every tick)
# area x 300..332, z 100..132
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
forceload add 300 100 332 132
kill @e[type=!minecraft:player,x=300,y=-61,z=100,dx=32,dy=8,dz=32]
fill 300 -60 100 332 -54 132 minecraft:air
fill 300 -60 100 331 -60 131 minecraft:hopper[facing=down]
tellraw @a {"text":"sleep: lag_hoppers built","color":"yellow"}
