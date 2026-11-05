# sleep:client_horde - the vanilla part of client_cursedfate_fight: zombies in waves (8 every 10 ticks, 960 in 60 s)
# burning at noon on a walled platform (fire on most of them, deaths, dropped flesh), normal difficulty (the client
# harness's world is peaceful). Measured with -Dsleep.clientAuto.timeline=true, view -Dsleep.clientAuto.tp=24,272,8,0,40
gamerule doDaylightCycle false
gamerule doWeatherCycle false
gamerule doMobSpawning false
difficulty normal
time set noon
weather clear
fill 0 249 16 48 249 64 minecraft:grass_block
fill 0 250 16 48 251 16 minecraft:stone
fill 0 250 64 48 251 64 minecraft:stone
fill 0 250 16 0 251 64 minecraft:stone
fill 48 250 16 48 251 64 minecraft:stone
scoreboard objectives add cfwave dummy
scoreboard players set #w cfwave 0
schedule function sleep:cf_wave 10t
