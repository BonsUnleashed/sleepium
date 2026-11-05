# sleep:client_cursedfate_fight - players' Cursed Fate videos (11-04) in motion: zombies summoned in waves (the video's
# command block) burning at noon (fire, deaths, dropped flesh), Cursed Fate mobs with their AI, the set's effects (black
# hole, domains, flame pillars, Jacob's ladder) summoned while it runs. Measured with -Dsleep.clientAuto.timeline=true.
# (normal difficulty: the client harness makes a peaceful world, where zombies vanish; view from -Dsleep.clientAuto.tp=24,272,8,0,40)
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
fill 1 250 17 47 250 63 minecraft:grass replace minecraft:air
summon cursedfate:yuji_itadori 12.5 250 28.5 {PersistenceRequired:1b}
summon cursedfate:yuji_itadori 36.5 250 52.5 {PersistenceRequired:1b}
summon cursedfate:rika 36.5 250 28.5 {PersistenceRequired:1b}
summon cursedfate:ultimate_mechamaru 12.5 250 52.5 {PersistenceRequired:1b}
summon cursedfate:garuda_s 24.5 252 40.5 {PersistenceRequired:1b}
summon cursedfate:mouth_curse 24.5 250 30.5 {PersistenceRequired:1b}
summon cursedfate:elephant_curse 24.5 250 50.5 {PersistenceRequired:1b}
summon cursedfate:rugby_curse 18.5 250 40.5 {PersistenceRequired:1b}
scoreboard objectives add cfwave dummy
scoreboard players set #w cfwave 0
schedule function sleep:cf_wave 10t
schedule function sleep:cf_vfx1 200t
schedule function sleep:cf_vfx2 400t
schedule function sleep:cf_vfx3 600t
schedule function sleep:cf_vfx4 800t
schedule function sleep:cf_vfx5 1000t
