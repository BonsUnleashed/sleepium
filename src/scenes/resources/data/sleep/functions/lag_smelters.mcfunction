# sleep:lag_smelters - 1024 smelter columns chest>hopper>furnace>hopper>chest, 256 per chunk (tech-server density)
# area x 608..639, z 96..127
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
forceload add 608 96 639 127
kill @e[type=!minecraft:player,x=608,y=-61,z=96,dx=31,dy=8,dz=31]
fill 608 -60 96 639 -54 127 minecraft:air
fill 608 -60 96 639 -60 127 minecraft:chest
fill 608 -59 96 639 -59 127 minecraft:hopper[facing=down]
fill 608 -58 96 639 -58 127 minecraft:furnace[facing=north]{Items:[{Slot:1b,id:"minecraft:coal_block",Count:64b}]}
fill 608 -57 96 639 -57 127 minecraft:hopper[facing=down]
fill 608 -56 96 639 -56 127 minecraft:chest{Items:[{Slot:0b,id:"minecraft:cobblestone",Count:64b},{Slot:1b,id:"minecraft:cobblestone",Count:64b},{Slot:2b,id:"minecraft:cobblestone",Count:64b},{Slot:3b,id:"minecraft:cobblestone",Count:64b},{Slot:4b,id:"minecraft:cobblestone",Count:64b},{Slot:5b,id:"minecraft:cobblestone",Count:64b},{Slot:6b,id:"minecraft:cobblestone",Count:64b},{Slot:7b,id:"minecraft:cobblestone",Count:64b},{Slot:8b,id:"minecraft:cobblestone",Count:64b},{Slot:9b,id:"minecraft:cobblestone",Count:64b},{Slot:10b,id:"minecraft:cobblestone",Count:64b},{Slot:11b,id:"minecraft:cobblestone",Count:64b},{Slot:12b,id:"minecraft:cobblestone",Count:64b},{Slot:13b,id:"minecraft:cobblestone",Count:64b},{Slot:14b,id:"minecraft:cobblestone",Count:64b},{Slot:15b,id:"minecraft:cobblestone",Count:64b},{Slot:16b,id:"minecraft:cobblestone",Count:64b},{Slot:17b,id:"minecraft:cobblestone",Count:64b},{Slot:18b,id:"minecraft:cobblestone",Count:64b},{Slot:19b,id:"minecraft:cobblestone",Count:64b},{Slot:20b,id:"minecraft:cobblestone",Count:64b},{Slot:21b,id:"minecraft:cobblestone",Count:64b},{Slot:22b,id:"minecraft:cobblestone",Count:64b},{Slot:23b,id:"minecraft:cobblestone",Count:64b},{Slot:24b,id:"minecraft:cobblestone",Count:64b},{Slot:25b,id:"minecraft:cobblestone",Count:64b},{Slot:26b,id:"minecraft:cobblestone",Count:64b}]}
tellraw @a {"text":"sleep: lag_smelters built","color":"yellow"}
