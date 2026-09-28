# probe:client_light_toggle_off - the grid switched every 2 s, ending off
gamerule doDaylightCycle false
gamerule doWeatherCycle false
time set midnight
weather clear
fill 0 239 16 48 239 64 minecraft:smooth_stone
function probe:ltog_on
schedule function probe:ltog_off 60t append
schedule function probe:ltog_on 100t append
schedule function probe:ltog_off 140t append
schedule function probe:ltog_on 180t append
schedule function probe:ltog_off 220t append
schedule function probe:ltog_on 260t append
schedule function probe:ltog_off 300t append
