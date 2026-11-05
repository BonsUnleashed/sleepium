# a wave of client_cursedfate_fight: 8 zombies every 10 ticks (16 per second), 120 waves (960 zombies over 60 s)
summon minecraft:zombie 6.5 250 22.5
summon minecraft:zombie 42.5 250 22.5
summon minecraft:zombie 6.5 250 58.5
summon minecraft:zombie 42.5 250 58.5
summon minecraft:zombie 24.5 250 22.5
summon minecraft:zombie 24.5 250 58.5
summon minecraft:zombie 6.5 250 40.5
summon minecraft:zombie 42.5 250 40.5
scoreboard players add #w cfwave 1
execute if score #w cfwave matches ..119 run schedule function sleep:cf_wave 10t
