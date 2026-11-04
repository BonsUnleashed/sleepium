# sleep:lag_itemwake - resting items woken by the world (entity sleep test). Items settle and fall asleep; then
# scheduled functions pour lava next to rows of them (150, 230, 310 ticks later; lava flows a block per 30 ticks),
# hoppers pull items resting on them, pickup delays and ages run down, and far rows are untouched controls.
# area x 1200..1240, z 100..132
gamerule doDaylightCycle false
gamerule doWeatherCycle false
gamerule doMobSpawning false
gamerule doTraderSpawning false
gamerule doPatrolSpawning false
gamerule doInsomnia false
gamerule doMobLoot false
gamerule randomTickSpeed 3
gamerule doFireTick false
time set noon
weather clear
forceload add 1200 100 1240 132
kill @e[type=!minecraft:player,x=1200,y=-61,z=100,dx=40,dy=8,dz=32]
fill 1200 -60 100 1240 -54 132 minecraft:air
fill 1200 -61 100 1240 -61 132 minecraft:stone
summon minecraft:item 1204.5 -60.0 101.5 {Item:{id:"minecraft:cobblestone",Count:1b},Age:-32768s,PickupDelay:40s}
summon minecraft:item 1206.5 -60.0 101.5 {Item:{id:"minecraft:dirt",Count:2b},PickupDelay:50s}
summon minecraft:item 1208.5 -60.0 101.5 {Item:{id:"minecraft:oak_planks",Count:3b},Age:-32768s,PickupDelay:60s}
summon minecraft:item 1210.5 -60.0 101.5 {Item:{id:"minecraft:stone",Count:4b},PickupDelay:70s}
summon minecraft:item 1212.5 -60.0 101.5 {Item:{id:"minecraft:sand",Count:5b},Age:-32768s,PickupDelay:80s}
summon minecraft:item 1214.5 -60.0 101.5 {Item:{id:"minecraft:gravel",Count:6b},PickupDelay:90s}
summon minecraft:item 1216.5 -60.0 101.5 {Item:{id:"minecraft:andesite",Count:7b},Age:-32768s,PickupDelay:100s}
summon minecraft:item 1218.5 -60.0 101.5 {Item:{id:"minecraft:diorite",Count:8b},PickupDelay:110s}
summon minecraft:item 1204.5 -60.0 103.5 {Item:{id:"minecraft:dirt",Count:8b},Age:-32768s}
summon minecraft:item 1206.5 -60.0 103.5 {Item:{id:"minecraft:oak_planks",Count:9b}}
summon minecraft:item 1208.5 -60.0 103.5 {Item:{id:"minecraft:stone",Count:10b},Age:-32768s}
summon minecraft:item 1210.5 -60.0 103.5 {Item:{id:"minecraft:sand",Count:11b}}
summon minecraft:item 1212.5 -60.0 103.5 {Item:{id:"minecraft:gravel",Count:12b},Age:-32768s}
summon minecraft:item 1214.5 -60.0 103.5 {Item:{id:"minecraft:andesite",Count:13b}}
summon minecraft:item 1216.5 -60.0 103.5 {Item:{id:"minecraft:diorite",Count:14b},Age:-32768s}
summon minecraft:item 1218.5 -60.0 103.5 {Item:{id:"minecraft:cobblestone",Count:15b}}
summon minecraft:item 1204.5 -60.0 105.5 {Item:{id:"minecraft:oak_planks",Count:15b},Age:-32768s}
summon minecraft:item 1206.5 -60.0 105.5 {Item:{id:"minecraft:stone",Count:16b}}
summon minecraft:item 1208.5 -60.0 105.5 {Item:{id:"minecraft:sand",Count:17b},Age:-32768s}
summon minecraft:item 1210.5 -60.0 105.5 {Item:{id:"minecraft:gravel",Count:18b}}
summon minecraft:item 1212.5 -60.0 105.5 {Item:{id:"minecraft:andesite",Count:19b},Age:-32768s}
summon minecraft:item 1214.5 -60.0 105.5 {Item:{id:"minecraft:diorite",Count:20b}}
summon minecraft:item 1216.5 -60.0 105.5 {Item:{id:"minecraft:cobblestone",Count:21b},Age:-32768s}
summon minecraft:item 1218.5 -60.0 105.5 {Item:{id:"minecraft:dirt",Count:22b}}
summon minecraft:item 1204.5 -60.0 107.5 {Item:{id:"minecraft:stone",Count:22b},Age:-32768s,PickupDelay:40s}
summon minecraft:item 1206.5 -60.0 107.5 {Item:{id:"minecraft:sand",Count:23b},PickupDelay:50s}
summon minecraft:item 1208.5 -60.0 107.5 {Item:{id:"minecraft:gravel",Count:24b},Age:-32768s,PickupDelay:60s}
summon minecraft:item 1210.5 -60.0 107.5 {Item:{id:"minecraft:andesite",Count:25b},PickupDelay:70s}
summon minecraft:item 1212.5 -60.0 107.5 {Item:{id:"minecraft:diorite",Count:26b},Age:-32768s,PickupDelay:80s}
summon minecraft:item 1214.5 -60.0 107.5 {Item:{id:"minecraft:cobblestone",Count:27b},PickupDelay:90s}
summon minecraft:item 1216.5 -60.0 107.5 {Item:{id:"minecraft:dirt",Count:28b},Age:-32768s,PickupDelay:100s}
summon minecraft:item 1218.5 -60.0 107.5 {Item:{id:"minecraft:oak_planks",Count:29b},PickupDelay:110s}
summon minecraft:item 1204.5 -60.0 109.5 {Item:{id:"minecraft:sand",Count:29b},Age:-32768s}
summon minecraft:item 1206.5 -60.0 109.5 {Item:{id:"minecraft:gravel",Count:30b}}
summon minecraft:item 1208.5 -60.0 109.5 {Item:{id:"minecraft:andesite",Count:31b},Age:-32768s}
summon minecraft:item 1210.5 -60.0 109.5 {Item:{id:"minecraft:diorite",Count:32b}}
summon minecraft:item 1212.5 -60.0 109.5 {Item:{id:"minecraft:cobblestone",Count:33b},Age:-32768s}
summon minecraft:item 1214.5 -60.0 109.5 {Item:{id:"minecraft:dirt",Count:34b}}
summon minecraft:item 1216.5 -60.0 109.5 {Item:{id:"minecraft:oak_planks",Count:35b},Age:-32768s}
summon minecraft:item 1218.5 -60.0 109.5 {Item:{id:"minecraft:stone",Count:36b}}
summon minecraft:item 1204.5 -60.0 111.5 {Item:{id:"minecraft:gravel",Count:36b},Age:-32768s}
summon minecraft:item 1206.5 -60.0 111.5 {Item:{id:"minecraft:andesite",Count:37b}}
summon minecraft:item 1208.5 -60.0 111.5 {Item:{id:"minecraft:diorite",Count:38b},Age:-32768s}
summon minecraft:item 1210.5 -60.0 111.5 {Item:{id:"minecraft:cobblestone",Count:39b}}
summon minecraft:item 1212.5 -60.0 111.5 {Item:{id:"minecraft:dirt",Count:40b},Age:-32768s}
summon minecraft:item 1214.5 -60.0 111.5 {Item:{id:"minecraft:oak_planks",Count:41b}}
summon minecraft:item 1216.5 -60.0 111.5 {Item:{id:"minecraft:stone",Count:42b},Age:-32768s}
summon minecraft:item 1218.5 -60.0 111.5 {Item:{id:"minecraft:sand",Count:43b}}
summon minecraft:item 1204.5 -60.0 113.5 {Item:{id:"minecraft:andesite",Count:43b},Age:-32768s,PickupDelay:40s}
summon minecraft:item 1206.5 -60.0 113.5 {Item:{id:"minecraft:diorite",Count:44b},PickupDelay:50s}
summon minecraft:item 1208.5 -60.0 113.5 {Item:{id:"minecraft:cobblestone",Count:45b},Age:-32768s,PickupDelay:60s}
summon minecraft:item 1210.5 -60.0 113.5 {Item:{id:"minecraft:dirt",Count:46b},PickupDelay:70s}
summon minecraft:item 1212.5 -60.0 113.5 {Item:{id:"minecraft:oak_planks",Count:47b},Age:-32768s,PickupDelay:80s}
summon minecraft:item 1214.5 -60.0 113.5 {Item:{id:"minecraft:stone",Count:48b},PickupDelay:90s}
summon minecraft:item 1216.5 -60.0 113.5 {Item:{id:"minecraft:sand",Count:49b},Age:-32768s,PickupDelay:100s}
summon minecraft:item 1218.5 -60.0 113.5 {Item:{id:"minecraft:gravel",Count:50b},PickupDelay:110s}
summon minecraft:item 1204.5 -60.0 115.5 {Item:{id:"minecraft:diorite",Count:50b},Age:-32768s}
summon minecraft:item 1206.5 -60.0 115.5 {Item:{id:"minecraft:cobblestone",Count:51b}}
summon minecraft:item 1208.5 -60.0 115.5 {Item:{id:"minecraft:dirt",Count:52b},Age:-32768s}
summon minecraft:item 1210.5 -60.0 115.5 {Item:{id:"minecraft:oak_planks",Count:53b}}
summon minecraft:item 1212.5 -60.0 115.5 {Item:{id:"minecraft:stone",Count:54b},Age:-32768s}
summon minecraft:item 1214.5 -60.0 115.5 {Item:{id:"minecraft:sand",Count:55b}}
summon minecraft:item 1216.5 -60.0 115.5 {Item:{id:"minecraft:gravel",Count:56b},Age:-32768s}
summon minecraft:item 1218.5 -60.0 115.5 {Item:{id:"minecraft:andesite",Count:57b}}
summon minecraft:item 1204.5 -60.0 117.5 {Item:{id:"minecraft:cobblestone",Count:57b},Age:-32768s}
summon minecraft:item 1206.5 -60.0 117.5 {Item:{id:"minecraft:dirt",Count:58b}}
summon minecraft:item 1208.5 -60.0 117.5 {Item:{id:"minecraft:oak_planks",Count:59b},Age:-32768s}
summon minecraft:item 1210.5 -60.0 117.5 {Item:{id:"minecraft:stone",Count:60b}}
summon minecraft:item 1212.5 -60.0 117.5 {Item:{id:"minecraft:sand",Count:61b},Age:-32768s}
summon minecraft:item 1214.5 -60.0 117.5 {Item:{id:"minecraft:gravel",Count:62b}}
summon minecraft:item 1216.5 -60.0 117.5 {Item:{id:"minecraft:andesite",Count:63b},Age:-32768s}
summon minecraft:item 1218.5 -60.0 117.5 {Item:{id:"minecraft:diorite",Count:64b}}
summon minecraft:item 1204.5 -60.0 119.5 {Item:{id:"minecraft:dirt",Count:64b},Age:-32768s,PickupDelay:40s}
summon minecraft:item 1206.5 -60.0 119.5 {Item:{id:"minecraft:oak_planks",Count:1b},PickupDelay:50s}
summon minecraft:item 1208.5 -60.0 119.5 {Item:{id:"minecraft:stone",Count:2b},Age:-32768s,PickupDelay:60s}
summon minecraft:item 1210.5 -60.0 119.5 {Item:{id:"minecraft:sand",Count:3b},PickupDelay:70s}
summon minecraft:item 1212.5 -60.0 119.5 {Item:{id:"minecraft:gravel",Count:4b},Age:-32768s,PickupDelay:80s}
summon minecraft:item 1214.5 -60.0 119.5 {Item:{id:"minecraft:andesite",Count:5b},PickupDelay:90s}
summon minecraft:item 1216.5 -60.0 119.5 {Item:{id:"minecraft:diorite",Count:6b},Age:-32768s,PickupDelay:100s}
summon minecraft:item 1218.5 -60.0 119.5 {Item:{id:"minecraft:cobblestone",Count:7b},PickupDelay:110s}
summon minecraft:item 1204.5 -60.0 121.5 {Item:{id:"minecraft:oak_planks",Count:7b},Age:-32768s}
summon minecraft:item 1206.5 -60.0 121.5 {Item:{id:"minecraft:stone",Count:8b}}
summon minecraft:item 1208.5 -60.0 121.5 {Item:{id:"minecraft:sand",Count:9b},Age:-32768s}
summon minecraft:item 1210.5 -60.0 121.5 {Item:{id:"minecraft:gravel",Count:10b}}
summon minecraft:item 1212.5 -60.0 121.5 {Item:{id:"minecraft:andesite",Count:11b},Age:-32768s}
summon minecraft:item 1214.5 -60.0 121.5 {Item:{id:"minecraft:diorite",Count:12b}}
summon minecraft:item 1216.5 -60.0 121.5 {Item:{id:"minecraft:cobblestone",Count:13b},Age:-32768s}
summon minecraft:item 1218.5 -60.0 121.5 {Item:{id:"minecraft:dirt",Count:14b}}
summon minecraft:item 1204.5 -60.0 123.5 {Item:{id:"minecraft:stone",Count:14b},Age:-32768s}
summon minecraft:item 1206.5 -60.0 123.5 {Item:{id:"minecraft:sand",Count:15b}}
summon minecraft:item 1208.5 -60.0 123.5 {Item:{id:"minecraft:gravel",Count:16b},Age:-32768s}
summon minecraft:item 1210.5 -60.0 123.5 {Item:{id:"minecraft:andesite",Count:17b}}
summon minecraft:item 1212.5 -60.0 123.5 {Item:{id:"minecraft:diorite",Count:18b},Age:-32768s}
summon minecraft:item 1214.5 -60.0 123.5 {Item:{id:"minecraft:cobblestone",Count:19b}}
summon minecraft:item 1216.5 -60.0 123.5 {Item:{id:"minecraft:dirt",Count:20b},Age:-32768s}
summon minecraft:item 1218.5 -60.0 123.5 {Item:{id:"minecraft:oak_planks",Count:21b}}
setblock 1224 -61 101 minecraft:chest
setblock 1224 -60 101 minecraft:hopper[facing=down]
summon minecraft:item 1224.5 -58.9 101.5 {Item:{id:"minecraft:cobblestone",Count:3b},PickupDelay:60s}
setblock 1226 -61 101 minecraft:chest
setblock 1226 -60 101 minecraft:hopper[facing=down]
summon minecraft:item 1226.5 -58.9 101.5 {Item:{id:"minecraft:dirt",Count:8b},PickupDelay:60s}
setblock 1228 -61 101 minecraft:chest
setblock 1228 -60 101 minecraft:hopper[facing=down]
summon minecraft:item 1228.5 -58.9 101.5 {Item:{id:"minecraft:oak_planks",Count:13b},PickupDelay:60s}
setblock 1230 -61 101 minecraft:chest
setblock 1230 -60 101 minecraft:hopper[facing=down]
summon minecraft:item 1230.5 -58.9 101.5 {Item:{id:"minecraft:stone",Count:18b},PickupDelay:60s}
setblock 1232 -61 101 minecraft:chest
setblock 1232 -60 101 minecraft:hopper[facing=down]
summon minecraft:item 1232.5 -58.9 101.5 {Item:{id:"minecraft:sand",Count:23b},PickupDelay:60s}
setblock 1234 -61 101 minecraft:chest
setblock 1234 -60 101 minecraft:hopper[facing=down]
summon minecraft:item 1234.5 -58.9 101.5 {Item:{id:"minecraft:gravel",Count:28b},PickupDelay:60s}
setblock 1236 -61 101 minecraft:chest
setblock 1236 -60 101 minecraft:hopper[facing=down]
summon minecraft:item 1236.5 -58.9 101.5 {Item:{id:"minecraft:andesite",Count:33b},PickupDelay:60s}
setblock 1224 -61 103 minecraft:chest
setblock 1224 -60 103 minecraft:hopper[facing=down]
summon minecraft:item 1224.5 -58.9 103.5 {Item:{id:"minecraft:dirt",Count:3b},PickupDelay:80s}
setblock 1226 -61 103 minecraft:chest
setblock 1226 -60 103 minecraft:hopper[facing=down]
summon minecraft:item 1226.5 -58.9 103.5 {Item:{id:"minecraft:oak_planks",Count:8b},PickupDelay:80s}
setblock 1228 -61 103 minecraft:chest
setblock 1228 -60 103 minecraft:hopper[facing=down]
summon minecraft:item 1228.5 -58.9 103.5 {Item:{id:"minecraft:stone",Count:13b},PickupDelay:80s}
setblock 1230 -61 103 minecraft:chest
setblock 1230 -60 103 minecraft:hopper[facing=down]
summon minecraft:item 1230.5 -58.9 103.5 {Item:{id:"minecraft:sand",Count:18b},PickupDelay:80s}
setblock 1232 -61 103 minecraft:chest
setblock 1232 -60 103 minecraft:hopper[facing=down]
summon minecraft:item 1232.5 -58.9 103.5 {Item:{id:"minecraft:gravel",Count:23b},PickupDelay:80s}
setblock 1234 -61 103 minecraft:chest
setblock 1234 -60 103 minecraft:hopper[facing=down]
summon minecraft:item 1234.5 -58.9 103.5 {Item:{id:"minecraft:andesite",Count:28b},PickupDelay:80s}
setblock 1236 -61 103 minecraft:chest
setblock 1236 -60 103 minecraft:hopper[facing=down]
summon minecraft:item 1236.5 -58.9 103.5 {Item:{id:"minecraft:diorite",Count:33b},PickupDelay:80s}
setblock 1224 -61 105 minecraft:chest
setblock 1224 -60 105 minecraft:hopper[facing=down]
summon minecraft:item 1224.5 -58.9 105.5 {Item:{id:"minecraft:oak_planks",Count:3b},PickupDelay:100s}
setblock 1226 -61 105 minecraft:chest
setblock 1226 -60 105 minecraft:hopper[facing=down]
summon minecraft:item 1226.5 -58.9 105.5 {Item:{id:"minecraft:stone",Count:8b},PickupDelay:100s}
setblock 1228 -61 105 minecraft:chest
setblock 1228 -60 105 minecraft:hopper[facing=down]
summon minecraft:item 1228.5 -58.9 105.5 {Item:{id:"minecraft:sand",Count:13b},PickupDelay:100s}
setblock 1230 -61 105 minecraft:chest
setblock 1230 -60 105 minecraft:hopper[facing=down]
summon minecraft:item 1230.5 -58.9 105.5 {Item:{id:"minecraft:gravel",Count:18b},PickupDelay:100s}
setblock 1232 -61 105 minecraft:chest
setblock 1232 -60 105 minecraft:hopper[facing=down]
summon minecraft:item 1232.5 -58.9 105.5 {Item:{id:"minecraft:andesite",Count:23b},PickupDelay:100s}
setblock 1234 -61 105 minecraft:chest
setblock 1234 -60 105 minecraft:hopper[facing=down]
summon minecraft:item 1234.5 -58.9 105.5 {Item:{id:"minecraft:diorite",Count:28b},PickupDelay:100s}
setblock 1236 -61 105 minecraft:chest
setblock 1236 -60 105 minecraft:hopper[facing=down]
summon minecraft:item 1236.5 -58.9 105.5 {Item:{id:"minecraft:cobblestone",Count:33b},PickupDelay:100s}
setblock 1224 -61 107 minecraft:chest
setblock 1224 -60 107 minecraft:hopper[facing=down]
summon minecraft:item 1224.5 -58.9 107.5 {Item:{id:"minecraft:stone",Count:3b},PickupDelay:120s}
setblock 1226 -61 107 minecraft:chest
setblock 1226 -60 107 minecraft:hopper[facing=down]
summon minecraft:item 1226.5 -58.9 107.5 {Item:{id:"minecraft:sand",Count:8b},PickupDelay:120s}
setblock 1228 -61 107 minecraft:chest
setblock 1228 -60 107 minecraft:hopper[facing=down]
summon minecraft:item 1228.5 -58.9 107.5 {Item:{id:"minecraft:gravel",Count:13b},PickupDelay:120s}
setblock 1230 -61 107 minecraft:chest
setblock 1230 -60 107 minecraft:hopper[facing=down]
summon minecraft:item 1230.5 -58.9 107.5 {Item:{id:"minecraft:andesite",Count:18b},PickupDelay:120s}
setblock 1232 -61 107 minecraft:chest
setblock 1232 -60 107 minecraft:hopper[facing=down]
summon minecraft:item 1232.5 -58.9 107.5 {Item:{id:"minecraft:diorite",Count:23b},PickupDelay:120s}
setblock 1234 -61 107 minecraft:chest
setblock 1234 -60 107 minecraft:hopper[facing=down]
summon minecraft:item 1234.5 -58.9 107.5 {Item:{id:"minecraft:cobblestone",Count:28b},PickupDelay:120s}
setblock 1236 -61 107 minecraft:chest
setblock 1236 -60 107 minecraft:hopper[facing=down]
summon minecraft:item 1236.5 -58.9 107.5 {Item:{id:"minecraft:dirt",Count:33b},PickupDelay:120s}
setblock 1224 -61 109 minecraft:chest
setblock 1224 -60 109 minecraft:hopper[facing=down]
summon minecraft:item 1224.5 -58.9 109.5 {Item:{id:"minecraft:sand",Count:3b},PickupDelay:140s}
setblock 1226 -61 109 minecraft:chest
setblock 1226 -60 109 minecraft:hopper[facing=down]
summon minecraft:item 1226.5 -58.9 109.5 {Item:{id:"minecraft:gravel",Count:8b},PickupDelay:140s}
setblock 1228 -61 109 minecraft:chest
setblock 1228 -60 109 minecraft:hopper[facing=down]
summon minecraft:item 1228.5 -58.9 109.5 {Item:{id:"minecraft:andesite",Count:13b},PickupDelay:140s}
setblock 1230 -61 109 minecraft:chest
setblock 1230 -60 109 minecraft:hopper[facing=down]
summon minecraft:item 1230.5 -58.9 109.5 {Item:{id:"minecraft:diorite",Count:18b},PickupDelay:140s}
setblock 1232 -61 109 minecraft:chest
setblock 1232 -60 109 minecraft:hopper[facing=down]
summon minecraft:item 1232.5 -58.9 109.5 {Item:{id:"minecraft:cobblestone",Count:23b},PickupDelay:140s}
setblock 1234 -61 109 minecraft:chest
setblock 1234 -60 109 minecraft:hopper[facing=down]
summon minecraft:item 1234.5 -58.9 109.5 {Item:{id:"minecraft:dirt",Count:28b},PickupDelay:140s}
setblock 1236 -61 109 minecraft:chest
setblock 1236 -60 109 minecraft:hopper[facing=down]
summon minecraft:item 1236.5 -58.9 109.5 {Item:{id:"minecraft:oak_planks",Count:33b},PickupDelay:140s}
setblock 1224 -61 111 minecraft:chest
setblock 1224 -60 111 minecraft:hopper[facing=down]
summon minecraft:item 1224.5 -58.9 111.5 {Item:{id:"minecraft:gravel",Count:3b},PickupDelay:160s}
setblock 1226 -61 111 minecraft:chest
setblock 1226 -60 111 minecraft:hopper[facing=down]
summon minecraft:item 1226.5 -58.9 111.5 {Item:{id:"minecraft:andesite",Count:8b},PickupDelay:160s}
setblock 1228 -61 111 minecraft:chest
setblock 1228 -60 111 minecraft:hopper[facing=down]
summon minecraft:item 1228.5 -58.9 111.5 {Item:{id:"minecraft:diorite",Count:13b},PickupDelay:160s}
setblock 1230 -61 111 minecraft:chest
setblock 1230 -60 111 minecraft:hopper[facing=down]
summon minecraft:item 1230.5 -58.9 111.5 {Item:{id:"minecraft:cobblestone",Count:18b},PickupDelay:160s}
setblock 1232 -61 111 minecraft:chest
setblock 1232 -60 111 minecraft:hopper[facing=down]
summon minecraft:item 1232.5 -58.9 111.5 {Item:{id:"minecraft:dirt",Count:23b},PickupDelay:160s}
setblock 1234 -61 111 minecraft:chest
setblock 1234 -60 111 minecraft:hopper[facing=down]
summon minecraft:item 1234.5 -58.9 111.5 {Item:{id:"minecraft:oak_planks",Count:28b},PickupDelay:160s}
setblock 1236 -61 111 minecraft:chest
setblock 1236 -60 111 minecraft:hopper[facing=down]
summon minecraft:item 1236.5 -58.9 111.5 {Item:{id:"minecraft:stone",Count:33b},PickupDelay:160s}
setblock 1224 -61 113 minecraft:chest
setblock 1224 -60 113 minecraft:hopper[facing=down]
summon minecraft:item 1224.5 -58.9 113.5 {Item:{id:"minecraft:andesite",Count:3b},PickupDelay:180s}
setblock 1226 -61 113 minecraft:chest
setblock 1226 -60 113 minecraft:hopper[facing=down]
summon minecraft:item 1226.5 -58.9 113.5 {Item:{id:"minecraft:diorite",Count:8b},PickupDelay:180s}
setblock 1228 -61 113 minecraft:chest
setblock 1228 -60 113 minecraft:hopper[facing=down]
summon minecraft:item 1228.5 -58.9 113.5 {Item:{id:"minecraft:cobblestone",Count:13b},PickupDelay:180s}
setblock 1230 -61 113 minecraft:chest
setblock 1230 -60 113 minecraft:hopper[facing=down]
summon minecraft:item 1230.5 -58.9 113.5 {Item:{id:"minecraft:dirt",Count:18b},PickupDelay:180s}
setblock 1232 -61 113 minecraft:chest
setblock 1232 -60 113 minecraft:hopper[facing=down]
summon minecraft:item 1232.5 -58.9 113.5 {Item:{id:"minecraft:oak_planks",Count:23b},PickupDelay:180s}
setblock 1234 -61 113 minecraft:chest
setblock 1234 -60 113 minecraft:hopper[facing=down]
summon minecraft:item 1234.5 -58.9 113.5 {Item:{id:"minecraft:stone",Count:28b},PickupDelay:180s}
setblock 1236 -61 113 minecraft:chest
setblock 1236 -60 113 minecraft:hopper[facing=down]
summon minecraft:item 1236.5 -58.9 113.5 {Item:{id:"minecraft:sand",Count:33b},PickupDelay:180s}
setblock 1224 -61 115 minecraft:chest
setblock 1224 -60 115 minecraft:hopper[facing=down]
summon minecraft:item 1224.5 -58.9 115.5 {Item:{id:"minecraft:diorite",Count:3b},PickupDelay:200s}
setblock 1226 -61 115 minecraft:chest
setblock 1226 -60 115 minecraft:hopper[facing=down]
summon minecraft:item 1226.5 -58.9 115.5 {Item:{id:"minecraft:cobblestone",Count:8b},PickupDelay:200s}
setblock 1228 -61 115 minecraft:chest
setblock 1228 -60 115 minecraft:hopper[facing=down]
summon minecraft:item 1228.5 -58.9 115.5 {Item:{id:"minecraft:dirt",Count:13b},PickupDelay:200s}
setblock 1230 -61 115 minecraft:chest
setblock 1230 -60 115 minecraft:hopper[facing=down]
summon minecraft:item 1230.5 -58.9 115.5 {Item:{id:"minecraft:oak_planks",Count:18b},PickupDelay:200s}
setblock 1232 -61 115 minecraft:chest
setblock 1232 -60 115 minecraft:hopper[facing=down]
summon minecraft:item 1232.5 -58.9 115.5 {Item:{id:"minecraft:stone",Count:23b},PickupDelay:200s}
setblock 1234 -61 115 minecraft:chest
setblock 1234 -60 115 minecraft:hopper[facing=down]
summon minecraft:item 1234.5 -58.9 115.5 {Item:{id:"minecraft:sand",Count:28b},PickupDelay:200s}
setblock 1236 -61 115 minecraft:chest
setblock 1236 -60 115 minecraft:hopper[facing=down]
summon minecraft:item 1236.5 -58.9 115.5 {Item:{id:"minecraft:gravel",Count:33b},PickupDelay:200s}
summon minecraft:item 1202.5 -60.0 126.5 {Item:{id:"minecraft:cobblestone",Count:64b}}
summon minecraft:item 1205.5 -60.0 126.5 {Item:{id:"minecraft:dirt",Count:64b}}
summon minecraft:item 1208.5 -60.0 126.5 {Item:{id:"minecraft:oak_planks",Count:64b}}
summon minecraft:item 1211.5 -60.0 126.5 {Item:{id:"minecraft:stone",Count:64b}}
summon minecraft:item 1214.5 -60.0 126.5 {Item:{id:"minecraft:sand",Count:64b}}
summon minecraft:item 1217.5 -60.0 126.5 {Item:{id:"minecraft:gravel",Count:64b}}
summon minecraft:item 1220.5 -60.0 126.5 {Item:{id:"minecraft:andesite",Count:64b}}
summon minecraft:item 1223.5 -60.0 126.5 {Item:{id:"minecraft:diorite",Count:64b}}
summon minecraft:item 1226.5 -60.0 126.5 {Item:{id:"minecraft:cobblestone",Count:64b}}
summon minecraft:item 1229.5 -60.0 126.5 {Item:{id:"minecraft:dirt",Count:64b}}
summon minecraft:item 1232.5 -60.0 126.5 {Item:{id:"minecraft:oak_planks",Count:64b}}
summon minecraft:item 1235.5 -60.0 126.5 {Item:{id:"minecraft:stone",Count:64b}}
summon minecraft:item 1202.5 -60.0 128.5 {Item:{id:"minecraft:cobblestone",Count:64b}}
summon minecraft:item 1205.5 -60.0 128.5 {Item:{id:"minecraft:dirt",Count:64b}}
summon minecraft:item 1208.5 -60.0 128.5 {Item:{id:"minecraft:oak_planks",Count:64b}}
summon minecraft:item 1211.5 -60.0 128.5 {Item:{id:"minecraft:stone",Count:64b}}
summon minecraft:item 1214.5 -60.0 128.5 {Item:{id:"minecraft:sand",Count:64b}}
summon minecraft:item 1217.5 -60.0 128.5 {Item:{id:"minecraft:gravel",Count:64b}}
summon minecraft:item 1220.5 -60.0 128.5 {Item:{id:"minecraft:andesite",Count:64b}}
summon minecraft:item 1223.5 -60.0 128.5 {Item:{id:"minecraft:diorite",Count:64b}}
summon minecraft:item 1226.5 -60.0 128.5 {Item:{id:"minecraft:cobblestone",Count:64b}}
summon minecraft:item 1229.5 -60.0 128.5 {Item:{id:"minecraft:dirt",Count:64b}}
summon minecraft:item 1232.5 -60.0 128.5 {Item:{id:"minecraft:oak_planks",Count:64b}}
summon minecraft:item 1235.5 -60.0 128.5 {Item:{id:"minecraft:stone",Count:64b}}
summon minecraft:item 1202.5 -60.0 130.5 {Item:{id:"minecraft:cobblestone",Count:64b}}
summon minecraft:item 1205.5 -60.0 130.5 {Item:{id:"minecraft:dirt",Count:64b}}
summon minecraft:item 1208.5 -60.0 130.5 {Item:{id:"minecraft:oak_planks",Count:64b}}
summon minecraft:item 1211.5 -60.0 130.5 {Item:{id:"minecraft:stone",Count:64b}}
summon minecraft:item 1214.5 -60.0 130.5 {Item:{id:"minecraft:sand",Count:64b}}
summon minecraft:item 1217.5 -60.0 130.5 {Item:{id:"minecraft:gravel",Count:64b}}
summon minecraft:item 1220.5 -60.0 130.5 {Item:{id:"minecraft:andesite",Count:64b}}
summon minecraft:item 1223.5 -60.0 130.5 {Item:{id:"minecraft:diorite",Count:64b}}
summon minecraft:item 1226.5 -60.0 130.5 {Item:{id:"minecraft:cobblestone",Count:64b}}
summon minecraft:item 1229.5 -60.0 130.5 {Item:{id:"minecraft:dirt",Count:64b}}
summon minecraft:item 1232.5 -60.0 130.5 {Item:{id:"minecraft:oak_planks",Count:64b}}
summon minecraft:item 1235.5 -60.0 130.5 {Item:{id:"minecraft:stone",Count:64b}}
summon minecraft:item 1202.5 -60.0 132.5 {Item:{id:"minecraft:cobblestone",Count:64b}}
summon minecraft:item 1205.5 -60.0 132.5 {Item:{id:"minecraft:dirt",Count:64b}}
summon minecraft:item 1208.5 -60.0 132.5 {Item:{id:"minecraft:oak_planks",Count:64b}}
summon minecraft:item 1211.5 -60.0 132.5 {Item:{id:"minecraft:stone",Count:64b}}
summon minecraft:item 1214.5 -60.0 132.5 {Item:{id:"minecraft:sand",Count:64b}}
summon minecraft:item 1217.5 -60.0 132.5 {Item:{id:"minecraft:gravel",Count:64b}}
summon minecraft:item 1220.5 -60.0 132.5 {Item:{id:"minecraft:andesite",Count:64b}}
summon minecraft:item 1223.5 -60.0 132.5 {Item:{id:"minecraft:diorite",Count:64b}}
summon minecraft:item 1226.5 -60.0 132.5 {Item:{id:"minecraft:cobblestone",Count:64b}}
summon minecraft:item 1229.5 -60.0 132.5 {Item:{id:"minecraft:dirt",Count:64b}}
summon minecraft:item 1232.5 -60.0 132.5 {Item:{id:"minecraft:oak_planks",Count:64b}}
summon minecraft:item 1235.5 -60.0 132.5 {Item:{id:"minecraft:stone",Count:64b}}
schedule function sleep:itemwake_lava0 150t
schedule function sleep:itemwake_lava1 230t
schedule function sleep:itemwake_lava2 310t
