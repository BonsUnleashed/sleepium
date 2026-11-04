# sleep:client_water_near - water under lights switched every 2 ticks
gamerule doDaylightCycle false
gamerule doWeatherCycle false
time set midnight
weather clear
fill -1 236 15 49 240 65 minecraft:stone
fill 0 237 16 48 240 64 minecraft:air
fill 0 237 16 48 239 64 minecraft:water
schedule function sleep:wnear_on 2t
