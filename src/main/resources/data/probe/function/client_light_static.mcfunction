# probe:client_light - constant relighting (ClientAuto -Dprobe.clientAuto.scene=client_light): a grid of light
# sources (196) switched between glowstone and stone every 2 ticks by two functions scheduling each other.
gamerule doDaylightCycle false
gamerule doWeatherCycle false
time set midnight
weather clear
fill 0 250 16 48 250 64 minecraft:smooth_stone
# (static: no switching)
