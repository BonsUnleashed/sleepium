# probe:lag_itemwake - resting items woken by the world (entity sleep test). Items settle and fall asleep; then
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
summon minecraft:item 1204.5 -60.0 101.5 {Item:{id:"minecraft:cobblestone",count:1},Age:-32768s,PickupDelay:40s}
summon minecraft:item 1206.5 -60.0 101.5 {Item:{id:"minecraft:dirt",count:2},PickupDelay:50s}
summon minecraft:item 1208.5 -60.0 101.5 {Item:{id:"minecraft:oak_planks",count:3},Age:-32768s,PickupDelay:60s}
summon minecraft:item 1210.5 -60.0 101.5 {Item:{id:"minecraft:stone",count:4},PickupDelay:70s}
summon minecraft:item 1212.5 -60.0 101.5 {Item:{id:"minecraft:sand",count:5},Age:-32768s,PickupDelay:80s}
summon minecraft:item 1214.5 -60.0 101.5 {Item:{id:"minecraft:gravel",count:6},PickupDelay:90s}
summon minecraft:item 1216.5 -60.0 101.5 {Item:{id:"minecraft:andesite",count:7},Age:-32768s,PickupDelay:100s}
summon minecraft:item 1218.5 -60.0 101.5 {Item:{id:"minecraft:diorite",count:8},PickupDelay:110s}
summon minecraft:item 1204.5 -60.0 103.5 {Item:{id:"minecraft:dirt",count:8},Age:-32768s}
summon minecraft:item 1206.5 -60.0 103.5 {Item:{id:"minecraft:oak_planks",count:9}}
summon minecraft:item 1208.5 -60.0 103.5 {Item:{id:"minecraft:stone",count:10},Age:-32768s}
summon minecraft:item 1210.5 -60.0 103.5 {Item:{id:"minecraft:sand",count:11}}
summon minecraft:item 1212.5 -60.0 103.5 {Item:{id:"minecraft:gravel",count:12},Age:-32768s}
summon minecraft:item 1214.5 -60.0 103.5 {Item:{id:"minecraft:andesite",count:13}}
summon minecraft:item 1216.5 -60.0 103.5 {Item:{id:"minecraft:diorite",count:14},Age:-32768s}
summon minecraft:item 1218.5 -60.0 103.5 {Item:{id:"minecraft:cobblestone",count:15}}
summon minecraft:item 1204.5 -60.0 105.5 {Item:{id:"minecraft:oak_planks",count:15},Age:-32768s}
summon minecraft:item 1206.5 -60.0 105.5 {Item:{id:"minecraft:stone",count:16}}
summon minecraft:item 1208.5 -60.0 105.5 {Item:{id:"minecraft:sand",count:17},Age:-32768s}
summon minecraft:item 1210.5 -60.0 105.5 {Item:{id:"minecraft:gravel",count:18}}
summon minecraft:item 1212.5 -60.0 105.5 {Item:{id:"minecraft:andesite",count:19},Age:-32768s}
summon minecraft:item 1214.5 -60.0 105.5 {Item:{id:"minecraft:diorite",count:20}}
summon minecraft:item 1216.5 -60.0 105.5 {Item:{id:"minecraft:cobblestone",count:21},Age:-32768s}
summon minecraft:item 1218.5 -60.0 105.5 {Item:{id:"minecraft:dirt",count:22}}
summon minecraft:item 1204.5 -60.0 107.5 {Item:{id:"minecraft:stone",count:22},Age:-32768s,PickupDelay:40s}
summon minecraft:item 1206.5 -60.0 107.5 {Item:{id:"minecraft:sand",count:23},PickupDelay:50s}
summon minecraft:item 1208.5 -60.0 107.5 {Item:{id:"minecraft:gravel",count:24},Age:-32768s,PickupDelay:60s}
summon minecraft:item 1210.5 -60.0 107.5 {Item:{id:"minecraft:andesite",count:25},PickupDelay:70s}
summon minecraft:item 1212.5 -60.0 107.5 {Item:{id:"minecraft:diorite",count:26},Age:-32768s,PickupDelay:80s}
summon minecraft:item 1214.5 -60.0 107.5 {Item:{id:"minecraft:cobblestone",count:27},PickupDelay:90s}
summon minecraft:item 1216.5 -60.0 107.5 {Item:{id:"minecraft:dirt",count:28},Age:-32768s,PickupDelay:100s}
summon minecraft:item 1218.5 -60.0 107.5 {Item:{id:"minecraft:oak_planks",count:29},PickupDelay:110s}
summon minecraft:item 1204.5 -60.0 109.5 {Item:{id:"minecraft:sand",count:29},Age:-32768s}
summon minecraft:item 1206.5 -60.0 109.5 {Item:{id:"minecraft:gravel",count:30}}
summon minecraft:item 1208.5 -60.0 109.5 {Item:{id:"minecraft:andesite",count:31},Age:-32768s}
summon minecraft:item 1210.5 -60.0 109.5 {Item:{id:"minecraft:diorite",count:32}}
summon minecraft:item 1212.5 -60.0 109.5 {Item:{id:"minecraft:cobblestone",count:33},Age:-32768s}
summon minecraft:item 1214.5 -60.0 109.5 {Item:{id:"minecraft:dirt",count:34}}
summon minecraft:item 1216.5 -60.0 109.5 {Item:{id:"minecraft:oak_planks",count:35},Age:-32768s}
summon minecraft:item 1218.5 -60.0 109.5 {Item:{id:"minecraft:stone",count:36}}
summon minecraft:item 1204.5 -60.0 111.5 {Item:{id:"minecraft:gravel",count:36},Age:-32768s}
summon minecraft:item 1206.5 -60.0 111.5 {Item:{id:"minecraft:andesite",count:37}}
summon minecraft:item 1208.5 -60.0 111.5 {Item:{id:"minecraft:diorite",count:38},Age:-32768s}
summon minecraft:item 1210.5 -60.0 111.5 {Item:{id:"minecraft:cobblestone",count:39}}
summon minecraft:item 1212.5 -60.0 111.5 {Item:{id:"minecraft:dirt",count:40},Age:-32768s}
summon minecraft:item 1214.5 -60.0 111.5 {Item:{id:"minecraft:oak_planks",count:41}}
summon minecraft:item 1216.5 -60.0 111.5 {Item:{id:"minecraft:stone",count:42},Age:-32768s}
summon minecraft:item 1218.5 -60.0 111.5 {Item:{id:"minecraft:sand",count:43}}
summon minecraft:item 1204.5 -60.0 113.5 {Item:{id:"minecraft:andesite",count:43},Age:-32768s,PickupDelay:40s}
summon minecraft:item 1206.5 -60.0 113.5 {Item:{id:"minecraft:diorite",count:44},PickupDelay:50s}
summon minecraft:item 1208.5 -60.0 113.5 {Item:{id:"minecraft:cobblestone",count:45},Age:-32768s,PickupDelay:60s}
summon minecraft:item 1210.5 -60.0 113.5 {Item:{id:"minecraft:dirt",count:46},PickupDelay:70s}
summon minecraft:item 1212.5 -60.0 113.5 {Item:{id:"minecraft:oak_planks",count:47},Age:-32768s,PickupDelay:80s}
summon minecraft:item 1214.5 -60.0 113.5 {Item:{id:"minecraft:stone",count:48},PickupDelay:90s}
summon minecraft:item 1216.5 -60.0 113.5 {Item:{id:"minecraft:sand",count:49},Age:-32768s,PickupDelay:100s}
summon minecraft:item 1218.5 -60.0 113.5 {Item:{id:"minecraft:gravel",count:50},PickupDelay:110s}
summon minecraft:item 1204.5 -60.0 115.5 {Item:{id:"minecraft:diorite",count:50},Age:-32768s}
summon minecraft:item 1206.5 -60.0 115.5 {Item:{id:"minecraft:cobblestone",count:51}}
summon minecraft:item 1208.5 -60.0 115.5 {Item:{id:"minecraft:dirt",count:52},Age:-32768s}
summon minecraft:item 1210.5 -60.0 115.5 {Item:{id:"minecraft:oak_planks",count:53}}
summon minecraft:item 1212.5 -60.0 115.5 {Item:{id:"minecraft:stone",count:54},Age:-32768s}
summon minecraft:item 1214.5 -60.0 115.5 {Item:{id:"minecraft:sand",count:55}}
summon minecraft:item 1216.5 -60.0 115.5 {Item:{id:"minecraft:gravel",count:56},Age:-32768s}
summon minecraft:item 1218.5 -60.0 115.5 {Item:{id:"minecraft:andesite",count:57}}
summon minecraft:item 1204.5 -60.0 117.5 {Item:{id:"minecraft:cobblestone",count:57},Age:-32768s}
summon minecraft:item 1206.5 -60.0 117.5 {Item:{id:"minecraft:dirt",count:58}}
summon minecraft:item 1208.5 -60.0 117.5 {Item:{id:"minecraft:oak_planks",count:59},Age:-32768s}
summon minecraft:item 1210.5 -60.0 117.5 {Item:{id:"minecraft:stone",count:60}}
summon minecraft:item 1212.5 -60.0 117.5 {Item:{id:"minecraft:sand",count:61},Age:-32768s}
summon minecraft:item 1214.5 -60.0 117.5 {Item:{id:"minecraft:gravel",count:62}}
summon minecraft:item 1216.5 -60.0 117.5 {Item:{id:"minecraft:andesite",count:63},Age:-32768s}
summon minecraft:item 1218.5 -60.0 117.5 {Item:{id:"minecraft:diorite",count:64}}
summon minecraft:item 1204.5 -60.0 119.5 {Item:{id:"minecraft:dirt",count:64},Age:-32768s,PickupDelay:40s}
summon minecraft:item 1206.5 -60.0 119.5 {Item:{id:"minecraft:oak_planks",count:1},PickupDelay:50s}
summon minecraft:item 1208.5 -60.0 119.5 {Item:{id:"minecraft:stone",count:2},Age:-32768s,PickupDelay:60s}
summon minecraft:item 1210.5 -60.0 119.5 {Item:{id:"minecraft:sand",count:3},PickupDelay:70s}
summon minecraft:item 1212.5 -60.0 119.5 {Item:{id:"minecraft:gravel",count:4},Age:-32768s,PickupDelay:80s}
summon minecraft:item 1214.5 -60.0 119.5 {Item:{id:"minecraft:andesite",count:5},PickupDelay:90s}
summon minecraft:item 1216.5 -60.0 119.5 {Item:{id:"minecraft:diorite",count:6},Age:-32768s,PickupDelay:100s}
summon minecraft:item 1218.5 -60.0 119.5 {Item:{id:"minecraft:cobblestone",count:7},PickupDelay:110s}
summon minecraft:item 1204.5 -60.0 121.5 {Item:{id:"minecraft:oak_planks",count:7},Age:-32768s}
summon minecraft:item 1206.5 -60.0 121.5 {Item:{id:"minecraft:stone",count:8}}
summon minecraft:item 1208.5 -60.0 121.5 {Item:{id:"minecraft:sand",count:9},Age:-32768s}
summon minecraft:item 1210.5 -60.0 121.5 {Item:{id:"minecraft:gravel",count:10}}
summon minecraft:item 1212.5 -60.0 121.5 {Item:{id:"minecraft:andesite",count:11},Age:-32768s}
summon minecraft:item 1214.5 -60.0 121.5 {Item:{id:"minecraft:diorite",count:12}}
summon minecraft:item 1216.5 -60.0 121.5 {Item:{id:"minecraft:cobblestone",count:13},Age:-32768s}
summon minecraft:item 1218.5 -60.0 121.5 {Item:{id:"minecraft:dirt",count:14}}
summon minecraft:item 1204.5 -60.0 123.5 {Item:{id:"minecraft:stone",count:14},Age:-32768s}
summon minecraft:item 1206.5 -60.0 123.5 {Item:{id:"minecraft:sand",count:15}}
summon minecraft:item 1208.5 -60.0 123.5 {Item:{id:"minecraft:gravel",count:16},Age:-32768s}
summon minecraft:item 1210.5 -60.0 123.5 {Item:{id:"minecraft:andesite",count:17}}
summon minecraft:item 1212.5 -60.0 123.5 {Item:{id:"minecraft:diorite",count:18},Age:-32768s}
summon minecraft:item 1214.5 -60.0 123.5 {Item:{id:"minecraft:cobblestone",count:19}}
summon minecraft:item 1216.5 -60.0 123.5 {Item:{id:"minecraft:dirt",count:20},Age:-32768s}
summon minecraft:item 1218.5 -60.0 123.5 {Item:{id:"minecraft:oak_planks",count:21}}
setblock 1224 -61 101 minecraft:chest
setblock 1224 -60 101 minecraft:hopper[facing=down]
summon minecraft:item 1224.5 -58.9 101.5 {Item:{id:"minecraft:cobblestone",count:3},PickupDelay:60s}
setblock 1226 -61 101 minecraft:chest
setblock 1226 -60 101 minecraft:hopper[facing=down]
summon minecraft:item 1226.5 -58.9 101.5 {Item:{id:"minecraft:dirt",count:8},PickupDelay:60s}
setblock 1228 -61 101 minecraft:chest
setblock 1228 -60 101 minecraft:hopper[facing=down]
summon minecraft:item 1228.5 -58.9 101.5 {Item:{id:"minecraft:oak_planks",count:13},PickupDelay:60s}
setblock 1230 -61 101 minecraft:chest
setblock 1230 -60 101 minecraft:hopper[facing=down]
summon minecraft:item 1230.5 -58.9 101.5 {Item:{id:"minecraft:stone",count:18},PickupDelay:60s}
setblock 1232 -61 101 minecraft:chest
setblock 1232 -60 101 minecraft:hopper[facing=down]
summon minecraft:item 1232.5 -58.9 101.5 {Item:{id:"minecraft:sand",count:23},PickupDelay:60s}
setblock 1234 -61 101 minecraft:chest
setblock 1234 -60 101 minecraft:hopper[facing=down]
summon minecraft:item 1234.5 -58.9 101.5 {Item:{id:"minecraft:gravel",count:28},PickupDelay:60s}
setblock 1236 -61 101 minecraft:chest
setblock 1236 -60 101 minecraft:hopper[facing=down]
summon minecraft:item 1236.5 -58.9 101.5 {Item:{id:"minecraft:andesite",count:33},PickupDelay:60s}
setblock 1224 -61 103 minecraft:chest
setblock 1224 -60 103 minecraft:hopper[facing=down]
summon minecraft:item 1224.5 -58.9 103.5 {Item:{id:"minecraft:dirt",count:3},PickupDelay:80s}
setblock 1226 -61 103 minecraft:chest
setblock 1226 -60 103 minecraft:hopper[facing=down]
summon minecraft:item 1226.5 -58.9 103.5 {Item:{id:"minecraft:oak_planks",count:8},PickupDelay:80s}
setblock 1228 -61 103 minecraft:chest
setblock 1228 -60 103 minecraft:hopper[facing=down]
summon minecraft:item 1228.5 -58.9 103.5 {Item:{id:"minecraft:stone",count:13},PickupDelay:80s}
setblock 1230 -61 103 minecraft:chest
setblock 1230 -60 103 minecraft:hopper[facing=down]
summon minecraft:item 1230.5 -58.9 103.5 {Item:{id:"minecraft:sand",count:18},PickupDelay:80s}
setblock 1232 -61 103 minecraft:chest
setblock 1232 -60 103 minecraft:hopper[facing=down]
summon minecraft:item 1232.5 -58.9 103.5 {Item:{id:"minecraft:gravel",count:23},PickupDelay:80s}
setblock 1234 -61 103 minecraft:chest
setblock 1234 -60 103 minecraft:hopper[facing=down]
summon minecraft:item 1234.5 -58.9 103.5 {Item:{id:"minecraft:andesite",count:28},PickupDelay:80s}
setblock 1236 -61 103 minecraft:chest
setblock 1236 -60 103 minecraft:hopper[facing=down]
summon minecraft:item 1236.5 -58.9 103.5 {Item:{id:"minecraft:diorite",count:33},PickupDelay:80s}
setblock 1224 -61 105 minecraft:chest
setblock 1224 -60 105 minecraft:hopper[facing=down]
summon minecraft:item 1224.5 -58.9 105.5 {Item:{id:"minecraft:oak_planks",count:3},PickupDelay:100s}
setblock 1226 -61 105 minecraft:chest
setblock 1226 -60 105 minecraft:hopper[facing=down]
summon minecraft:item 1226.5 -58.9 105.5 {Item:{id:"minecraft:stone",count:8},PickupDelay:100s}
setblock 1228 -61 105 minecraft:chest
setblock 1228 -60 105 minecraft:hopper[facing=down]
summon minecraft:item 1228.5 -58.9 105.5 {Item:{id:"minecraft:sand",count:13},PickupDelay:100s}
setblock 1230 -61 105 minecraft:chest
setblock 1230 -60 105 minecraft:hopper[facing=down]
summon minecraft:item 1230.5 -58.9 105.5 {Item:{id:"minecraft:gravel",count:18},PickupDelay:100s}
setblock 1232 -61 105 minecraft:chest
setblock 1232 -60 105 minecraft:hopper[facing=down]
summon minecraft:item 1232.5 -58.9 105.5 {Item:{id:"minecraft:andesite",count:23},PickupDelay:100s}
setblock 1234 -61 105 minecraft:chest
setblock 1234 -60 105 minecraft:hopper[facing=down]
summon minecraft:item 1234.5 -58.9 105.5 {Item:{id:"minecraft:diorite",count:28},PickupDelay:100s}
setblock 1236 -61 105 minecraft:chest
setblock 1236 -60 105 minecraft:hopper[facing=down]
summon minecraft:item 1236.5 -58.9 105.5 {Item:{id:"minecraft:cobblestone",count:33},PickupDelay:100s}
setblock 1224 -61 107 minecraft:chest
setblock 1224 -60 107 minecraft:hopper[facing=down]
summon minecraft:item 1224.5 -58.9 107.5 {Item:{id:"minecraft:stone",count:3},PickupDelay:120s}
setblock 1226 -61 107 minecraft:chest
setblock 1226 -60 107 minecraft:hopper[facing=down]
summon minecraft:item 1226.5 -58.9 107.5 {Item:{id:"minecraft:sand",count:8},PickupDelay:120s}
setblock 1228 -61 107 minecraft:chest
setblock 1228 -60 107 minecraft:hopper[facing=down]
summon minecraft:item 1228.5 -58.9 107.5 {Item:{id:"minecraft:gravel",count:13},PickupDelay:120s}
setblock 1230 -61 107 minecraft:chest
setblock 1230 -60 107 minecraft:hopper[facing=down]
summon minecraft:item 1230.5 -58.9 107.5 {Item:{id:"minecraft:andesite",count:18},PickupDelay:120s}
setblock 1232 -61 107 minecraft:chest
setblock 1232 -60 107 minecraft:hopper[facing=down]
summon minecraft:item 1232.5 -58.9 107.5 {Item:{id:"minecraft:diorite",count:23},PickupDelay:120s}
setblock 1234 -61 107 minecraft:chest
setblock 1234 -60 107 minecraft:hopper[facing=down]
summon minecraft:item 1234.5 -58.9 107.5 {Item:{id:"minecraft:cobblestone",count:28},PickupDelay:120s}
setblock 1236 -61 107 minecraft:chest
setblock 1236 -60 107 minecraft:hopper[facing=down]
summon minecraft:item 1236.5 -58.9 107.5 {Item:{id:"minecraft:dirt",count:33},PickupDelay:120s}
setblock 1224 -61 109 minecraft:chest
setblock 1224 -60 109 minecraft:hopper[facing=down]
summon minecraft:item 1224.5 -58.9 109.5 {Item:{id:"minecraft:sand",count:3},PickupDelay:140s}
setblock 1226 -61 109 minecraft:chest
setblock 1226 -60 109 minecraft:hopper[facing=down]
summon minecraft:item 1226.5 -58.9 109.5 {Item:{id:"minecraft:gravel",count:8},PickupDelay:140s}
setblock 1228 -61 109 minecraft:chest
setblock 1228 -60 109 minecraft:hopper[facing=down]
summon minecraft:item 1228.5 -58.9 109.5 {Item:{id:"minecraft:andesite",count:13},PickupDelay:140s}
setblock 1230 -61 109 minecraft:chest
setblock 1230 -60 109 minecraft:hopper[facing=down]
summon minecraft:item 1230.5 -58.9 109.5 {Item:{id:"minecraft:diorite",count:18},PickupDelay:140s}
setblock 1232 -61 109 minecraft:chest
setblock 1232 -60 109 minecraft:hopper[facing=down]
summon minecraft:item 1232.5 -58.9 109.5 {Item:{id:"minecraft:cobblestone",count:23},PickupDelay:140s}
setblock 1234 -61 109 minecraft:chest
setblock 1234 -60 109 minecraft:hopper[facing=down]
summon minecraft:item 1234.5 -58.9 109.5 {Item:{id:"minecraft:dirt",count:28},PickupDelay:140s}
setblock 1236 -61 109 minecraft:chest
setblock 1236 -60 109 minecraft:hopper[facing=down]
summon minecraft:item 1236.5 -58.9 109.5 {Item:{id:"minecraft:oak_planks",count:33},PickupDelay:140s}
setblock 1224 -61 111 minecraft:chest
setblock 1224 -60 111 minecraft:hopper[facing=down]
summon minecraft:item 1224.5 -58.9 111.5 {Item:{id:"minecraft:gravel",count:3},PickupDelay:160s}
setblock 1226 -61 111 minecraft:chest
setblock 1226 -60 111 minecraft:hopper[facing=down]
summon minecraft:item 1226.5 -58.9 111.5 {Item:{id:"minecraft:andesite",count:8},PickupDelay:160s}
setblock 1228 -61 111 minecraft:chest
setblock 1228 -60 111 minecraft:hopper[facing=down]
summon minecraft:item 1228.5 -58.9 111.5 {Item:{id:"minecraft:diorite",count:13},PickupDelay:160s}
setblock 1230 -61 111 minecraft:chest
setblock 1230 -60 111 minecraft:hopper[facing=down]
summon minecraft:item 1230.5 -58.9 111.5 {Item:{id:"minecraft:cobblestone",count:18},PickupDelay:160s}
setblock 1232 -61 111 minecraft:chest
setblock 1232 -60 111 minecraft:hopper[facing=down]
summon minecraft:item 1232.5 -58.9 111.5 {Item:{id:"minecraft:dirt",count:23},PickupDelay:160s}
setblock 1234 -61 111 minecraft:chest
setblock 1234 -60 111 minecraft:hopper[facing=down]
summon minecraft:item 1234.5 -58.9 111.5 {Item:{id:"minecraft:oak_planks",count:28},PickupDelay:160s}
setblock 1236 -61 111 minecraft:chest
setblock 1236 -60 111 minecraft:hopper[facing=down]
summon minecraft:item 1236.5 -58.9 111.5 {Item:{id:"minecraft:stone",count:33},PickupDelay:160s}
setblock 1224 -61 113 minecraft:chest
setblock 1224 -60 113 minecraft:hopper[facing=down]
summon minecraft:item 1224.5 -58.9 113.5 {Item:{id:"minecraft:andesite",count:3},PickupDelay:180s}
setblock 1226 -61 113 minecraft:chest
setblock 1226 -60 113 minecraft:hopper[facing=down]
summon minecraft:item 1226.5 -58.9 113.5 {Item:{id:"minecraft:diorite",count:8},PickupDelay:180s}
setblock 1228 -61 113 minecraft:chest
setblock 1228 -60 113 minecraft:hopper[facing=down]
summon minecraft:item 1228.5 -58.9 113.5 {Item:{id:"minecraft:cobblestone",count:13},PickupDelay:180s}
setblock 1230 -61 113 minecraft:chest
setblock 1230 -60 113 minecraft:hopper[facing=down]
summon minecraft:item 1230.5 -58.9 113.5 {Item:{id:"minecraft:dirt",count:18},PickupDelay:180s}
setblock 1232 -61 113 minecraft:chest
setblock 1232 -60 113 minecraft:hopper[facing=down]
summon minecraft:item 1232.5 -58.9 113.5 {Item:{id:"minecraft:oak_planks",count:23},PickupDelay:180s}
setblock 1234 -61 113 minecraft:chest
setblock 1234 -60 113 minecraft:hopper[facing=down]
summon minecraft:item 1234.5 -58.9 113.5 {Item:{id:"minecraft:stone",count:28},PickupDelay:180s}
setblock 1236 -61 113 minecraft:chest
setblock 1236 -60 113 minecraft:hopper[facing=down]
summon minecraft:item 1236.5 -58.9 113.5 {Item:{id:"minecraft:sand",count:33},PickupDelay:180s}
setblock 1224 -61 115 minecraft:chest
setblock 1224 -60 115 minecraft:hopper[facing=down]
summon minecraft:item 1224.5 -58.9 115.5 {Item:{id:"minecraft:diorite",count:3},PickupDelay:200s}
setblock 1226 -61 115 minecraft:chest
setblock 1226 -60 115 minecraft:hopper[facing=down]
summon minecraft:item 1226.5 -58.9 115.5 {Item:{id:"minecraft:cobblestone",count:8},PickupDelay:200s}
setblock 1228 -61 115 minecraft:chest
setblock 1228 -60 115 minecraft:hopper[facing=down]
summon minecraft:item 1228.5 -58.9 115.5 {Item:{id:"minecraft:dirt",count:13},PickupDelay:200s}
setblock 1230 -61 115 minecraft:chest
setblock 1230 -60 115 minecraft:hopper[facing=down]
summon minecraft:item 1230.5 -58.9 115.5 {Item:{id:"minecraft:oak_planks",count:18},PickupDelay:200s}
setblock 1232 -61 115 minecraft:chest
setblock 1232 -60 115 minecraft:hopper[facing=down]
summon minecraft:item 1232.5 -58.9 115.5 {Item:{id:"minecraft:stone",count:23},PickupDelay:200s}
setblock 1234 -61 115 minecraft:chest
setblock 1234 -60 115 minecraft:hopper[facing=down]
summon minecraft:item 1234.5 -58.9 115.5 {Item:{id:"minecraft:sand",count:28},PickupDelay:200s}
setblock 1236 -61 115 minecraft:chest
setblock 1236 -60 115 minecraft:hopper[facing=down]
summon minecraft:item 1236.5 -58.9 115.5 {Item:{id:"minecraft:gravel",count:33},PickupDelay:200s}
summon minecraft:item 1202.5 -60.0 126.5 {Item:{id:"minecraft:cobblestone",count:64}}
summon minecraft:item 1205.5 -60.0 126.5 {Item:{id:"minecraft:dirt",count:64}}
summon minecraft:item 1208.5 -60.0 126.5 {Item:{id:"minecraft:oak_planks",count:64}}
summon minecraft:item 1211.5 -60.0 126.5 {Item:{id:"minecraft:stone",count:64}}
summon minecraft:item 1214.5 -60.0 126.5 {Item:{id:"minecraft:sand",count:64}}
summon minecraft:item 1217.5 -60.0 126.5 {Item:{id:"minecraft:gravel",count:64}}
summon minecraft:item 1220.5 -60.0 126.5 {Item:{id:"minecraft:andesite",count:64}}
summon minecraft:item 1223.5 -60.0 126.5 {Item:{id:"minecraft:diorite",count:64}}
summon minecraft:item 1226.5 -60.0 126.5 {Item:{id:"minecraft:cobblestone",count:64}}
summon minecraft:item 1229.5 -60.0 126.5 {Item:{id:"minecraft:dirt",count:64}}
summon minecraft:item 1232.5 -60.0 126.5 {Item:{id:"minecraft:oak_planks",count:64}}
summon minecraft:item 1235.5 -60.0 126.5 {Item:{id:"minecraft:stone",count:64}}
summon minecraft:item 1202.5 -60.0 128.5 {Item:{id:"minecraft:cobblestone",count:64}}
summon minecraft:item 1205.5 -60.0 128.5 {Item:{id:"minecraft:dirt",count:64}}
summon minecraft:item 1208.5 -60.0 128.5 {Item:{id:"minecraft:oak_planks",count:64}}
summon minecraft:item 1211.5 -60.0 128.5 {Item:{id:"minecraft:stone",count:64}}
summon minecraft:item 1214.5 -60.0 128.5 {Item:{id:"minecraft:sand",count:64}}
summon minecraft:item 1217.5 -60.0 128.5 {Item:{id:"minecraft:gravel",count:64}}
summon minecraft:item 1220.5 -60.0 128.5 {Item:{id:"minecraft:andesite",count:64}}
summon minecraft:item 1223.5 -60.0 128.5 {Item:{id:"minecraft:diorite",count:64}}
summon minecraft:item 1226.5 -60.0 128.5 {Item:{id:"minecraft:cobblestone",count:64}}
summon minecraft:item 1229.5 -60.0 128.5 {Item:{id:"minecraft:dirt",count:64}}
summon minecraft:item 1232.5 -60.0 128.5 {Item:{id:"minecraft:oak_planks",count:64}}
summon minecraft:item 1235.5 -60.0 128.5 {Item:{id:"minecraft:stone",count:64}}
summon minecraft:item 1202.5 -60.0 130.5 {Item:{id:"minecraft:cobblestone",count:64}}
summon minecraft:item 1205.5 -60.0 130.5 {Item:{id:"minecraft:dirt",count:64}}
summon minecraft:item 1208.5 -60.0 130.5 {Item:{id:"minecraft:oak_planks",count:64}}
summon minecraft:item 1211.5 -60.0 130.5 {Item:{id:"minecraft:stone",count:64}}
summon minecraft:item 1214.5 -60.0 130.5 {Item:{id:"minecraft:sand",count:64}}
summon minecraft:item 1217.5 -60.0 130.5 {Item:{id:"minecraft:gravel",count:64}}
summon minecraft:item 1220.5 -60.0 130.5 {Item:{id:"minecraft:andesite",count:64}}
summon minecraft:item 1223.5 -60.0 130.5 {Item:{id:"minecraft:diorite",count:64}}
summon minecraft:item 1226.5 -60.0 130.5 {Item:{id:"minecraft:cobblestone",count:64}}
summon minecraft:item 1229.5 -60.0 130.5 {Item:{id:"minecraft:dirt",count:64}}
summon minecraft:item 1232.5 -60.0 130.5 {Item:{id:"minecraft:oak_planks",count:64}}
summon minecraft:item 1235.5 -60.0 130.5 {Item:{id:"minecraft:stone",count:64}}
summon minecraft:item 1202.5 -60.0 132.5 {Item:{id:"minecraft:cobblestone",count:64}}
summon minecraft:item 1205.5 -60.0 132.5 {Item:{id:"minecraft:dirt",count:64}}
summon minecraft:item 1208.5 -60.0 132.5 {Item:{id:"minecraft:oak_planks",count:64}}
summon minecraft:item 1211.5 -60.0 132.5 {Item:{id:"minecraft:stone",count:64}}
summon minecraft:item 1214.5 -60.0 132.5 {Item:{id:"minecraft:sand",count:64}}
summon minecraft:item 1217.5 -60.0 132.5 {Item:{id:"minecraft:gravel",count:64}}
summon minecraft:item 1220.5 -60.0 132.5 {Item:{id:"minecraft:andesite",count:64}}
summon minecraft:item 1223.5 -60.0 132.5 {Item:{id:"minecraft:diorite",count:64}}
summon minecraft:item 1226.5 -60.0 132.5 {Item:{id:"minecraft:cobblestone",count:64}}
summon minecraft:item 1229.5 -60.0 132.5 {Item:{id:"minecraft:dirt",count:64}}
summon minecraft:item 1232.5 -60.0 132.5 {Item:{id:"minecraft:oak_planks",count:64}}
summon minecraft:item 1235.5 -60.0 132.5 {Item:{id:"minecraft:stone",count:64}}
schedule function probe:itemwake_lava0 150t
schedule function probe:itemwake_lava1 230t
schedule function probe:itemwake_lava2 310t
