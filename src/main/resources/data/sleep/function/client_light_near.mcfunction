# sleep:client_light_near - light-only changes: floor y 239 (section 14), glowstone grid y 241 (section 15) switched every 2 ticks
gamerule doDaylightCycle false
gamerule doWeatherCycle false
time set midnight
weather clear
fill 0 239 16 48 239 64 minecraft:smooth_stone
schedule function sleep:light_near_on 2t
