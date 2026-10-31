# sleep:client_water_toggle_off - water under lights switched every 2 s, ending off
gamerule doDaylightCycle false
gamerule doWeatherCycle false
time set midnight
weather clear
fill -1 236 15 49 240 65 minecraft:stone
fill 0 237 16 48 240 64 minecraft:air
fill 0 237 16 48 239 64 minecraft:water
function sleep:wtog_on
schedule function sleep:wtog_off 60t append
schedule function sleep:wtog_on 100t append
schedule function sleep:wtog_off 140t append
schedule function sleep:wtog_on 180t append
schedule function sleep:wtog_off 220t append
schedule function sleep:wtog_on 260t append
schedule function sleep:wtog_off 300t append
