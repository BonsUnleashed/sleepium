# sleep:lag_createfactory - Create factory lines (needs the Create mod). 16 lines: a chest feeds a hopper and a chute
# onto a 16-long belt (creative motor); a mechanical press over the belt (ingots -> sheets); an encased fan with water
# blowing across it (washing); at the end the items drop into a hopper and a chest. Plus 12 water wheels on flowing
# water, each driving a line of shafts. area x 1600..1660, z 100..204
gamerule doDaylightCycle false
gamerule doWeatherCycle false
gamerule doMobSpawning false
gamerule doTraderSpawning false
gamerule doPatrolSpawning false
gamerule doInsomnia false
gamerule randomTickSpeed 3
time set noon
weather clear
forceload add 1600 100 1660 204
kill @e[type=!minecraft:player,x=1600,y=-63,z=100,dx=60,dy=12,dz=104]
fill 1600 -60 100 1623 -52 204 minecraft:air
fill 1624 -60 100 1647 -52 204 minecraft:air
fill 1648 -60 100 1660 -52 204 minecraft:air
fill 1600 -61 100 1660 -61 204 minecraft:stone
fill 1600 -62 100 1660 -62 204 minecraft:dirt
setblock 1602 -60 103 create:shaft[axis=z]
setblock 1617 -60 103 create:shaft[axis=z]
sleep createbelt 1602 -60 103 1617 -60 103
setblock 1602 -60 104 create:creative_motor[facing=north]
setblock 1602 -59 103 create:chute
setblock 1602 -58 103 minecraft:hopper[facing=down]
setblock 1602 -57 103 minecraft:chest{Items:[{Slot:0b,id:"minecraft:iron_ingot",count:64},{Slot:1b,id:"minecraft:iron_ingot",count:64},{Slot:2b,id:"minecraft:iron_ingot",count:64},{Slot:3b,id:"minecraft:iron_ingot",count:64},{Slot:4b,id:"minecraft:iron_ingot",count:64},{Slot:5b,id:"minecraft:iron_ingot",count:64},{Slot:6b,id:"minecraft:iron_ingot",count:64},{Slot:7b,id:"minecraft:iron_ingot",count:64},{Slot:8b,id:"minecraft:iron_ingot",count:64},{Slot:9b,id:"minecraft:iron_ingot",count:64},{Slot:10b,id:"minecraft:iron_ingot",count:64},{Slot:11b,id:"minecraft:iron_ingot",count:64},{Slot:12b,id:"minecraft:iron_ingot",count:64},{Slot:13b,id:"minecraft:iron_ingot",count:64},{Slot:14b,id:"minecraft:iron_ingot",count:64},{Slot:15b,id:"minecraft:iron_ingot",count:64},{Slot:16b,id:"minecraft:iron_ingot",count:64},{Slot:17b,id:"minecraft:iron_ingot",count:64},{Slot:18b,id:"minecraft:iron_ingot",count:64},{Slot:19b,id:"minecraft:iron_ingot",count:64},{Slot:20b,id:"minecraft:iron_ingot",count:64},{Slot:21b,id:"minecraft:iron_ingot",count:64},{Slot:22b,id:"minecraft:iron_ingot",count:64},{Slot:23b,id:"minecraft:iron_ingot",count:64},{Slot:24b,id:"minecraft:iron_ingot",count:64},{Slot:25b,id:"minecraft:iron_ingot",count:64},{Slot:26b,id:"minecraft:iron_ingot",count:64}]}
setblock 1608 -58 103 create:mechanical_press[facing=east]
setblock 1609 -58 103 create:creative_motor[facing=west]
setblock 1612 -60 104 minecraft:water
setblock 1612 -60 105 create:encased_fan[facing=north]
setblock 1612 -60 106 create:creative_motor[facing=north]
setblock 1618 -61 103 minecraft:hopper[facing=down]
setblock 1618 -62 103 minecraft:chest
setblock 1602 -60 109 create:shaft[axis=z]
setblock 1617 -60 109 create:shaft[axis=z]
sleep createbelt 1602 -60 109 1617 -60 109
setblock 1602 -60 110 create:creative_motor[facing=north]
setblock 1602 -59 109 create:chute
setblock 1602 -58 109 minecraft:hopper[facing=down]
setblock 1602 -57 109 minecraft:chest{Items:[{Slot:0b,id:"minecraft:gravel",count:64},{Slot:1b,id:"minecraft:gravel",count:64},{Slot:2b,id:"minecraft:gravel",count:64},{Slot:3b,id:"minecraft:gravel",count:64},{Slot:4b,id:"minecraft:gravel",count:64},{Slot:5b,id:"minecraft:gravel",count:64},{Slot:6b,id:"minecraft:gravel",count:64},{Slot:7b,id:"minecraft:gravel",count:64},{Slot:8b,id:"minecraft:gravel",count:64},{Slot:9b,id:"minecraft:gravel",count:64},{Slot:10b,id:"minecraft:gravel",count:64},{Slot:11b,id:"minecraft:gravel",count:64},{Slot:12b,id:"minecraft:gravel",count:64},{Slot:13b,id:"minecraft:gravel",count:64},{Slot:14b,id:"minecraft:gravel",count:64},{Slot:15b,id:"minecraft:gravel",count:64},{Slot:16b,id:"minecraft:gravel",count:64},{Slot:17b,id:"minecraft:gravel",count:64},{Slot:18b,id:"minecraft:gravel",count:64},{Slot:19b,id:"minecraft:gravel",count:64},{Slot:20b,id:"minecraft:gravel",count:64},{Slot:21b,id:"minecraft:gravel",count:64},{Slot:22b,id:"minecraft:gravel",count:64},{Slot:23b,id:"minecraft:gravel",count:64},{Slot:24b,id:"minecraft:gravel",count:64},{Slot:25b,id:"minecraft:gravel",count:64},{Slot:26b,id:"minecraft:gravel",count:64}]}
setblock 1608 -58 109 create:mechanical_press[facing=east]
setblock 1609 -58 109 create:creative_motor[facing=west]
setblock 1612 -60 110 minecraft:water
setblock 1612 -60 111 create:encased_fan[facing=north]
setblock 1612 -60 112 create:creative_motor[facing=north]
setblock 1618 -61 109 minecraft:hopper[facing=down]
setblock 1618 -62 109 minecraft:chest
setblock 1602 -60 115 create:shaft[axis=z]
setblock 1617 -60 115 create:shaft[axis=z]
sleep createbelt 1602 -60 115 1617 -60 115
setblock 1602 -60 116 create:creative_motor[facing=north]
setblock 1602 -59 115 create:chute
setblock 1602 -58 115 minecraft:hopper[facing=down]
setblock 1602 -57 115 minecraft:chest{Items:[{Slot:0b,id:"minecraft:cobblestone",count:64},{Slot:1b,id:"minecraft:cobblestone",count:64},{Slot:2b,id:"minecraft:cobblestone",count:64},{Slot:3b,id:"minecraft:cobblestone",count:64},{Slot:4b,id:"minecraft:cobblestone",count:64},{Slot:5b,id:"minecraft:cobblestone",count:64},{Slot:6b,id:"minecraft:cobblestone",count:64},{Slot:7b,id:"minecraft:cobblestone",count:64},{Slot:8b,id:"minecraft:cobblestone",count:64},{Slot:9b,id:"minecraft:cobblestone",count:64},{Slot:10b,id:"minecraft:cobblestone",count:64},{Slot:11b,id:"minecraft:cobblestone",count:64},{Slot:12b,id:"minecraft:cobblestone",count:64},{Slot:13b,id:"minecraft:cobblestone",count:64},{Slot:14b,id:"minecraft:cobblestone",count:64},{Slot:15b,id:"minecraft:cobblestone",count:64},{Slot:16b,id:"minecraft:cobblestone",count:64},{Slot:17b,id:"minecraft:cobblestone",count:64},{Slot:18b,id:"minecraft:cobblestone",count:64},{Slot:19b,id:"minecraft:cobblestone",count:64},{Slot:20b,id:"minecraft:cobblestone",count:64},{Slot:21b,id:"minecraft:cobblestone",count:64},{Slot:22b,id:"minecraft:cobblestone",count:64},{Slot:23b,id:"minecraft:cobblestone",count:64},{Slot:24b,id:"minecraft:cobblestone",count:64},{Slot:25b,id:"minecraft:cobblestone",count:64},{Slot:26b,id:"minecraft:cobblestone",count:64}]}
setblock 1608 -58 115 create:mechanical_press[facing=east]
setblock 1609 -58 115 create:creative_motor[facing=west]
setblock 1612 -60 116 minecraft:water
setblock 1612 -60 117 create:encased_fan[facing=north]
setblock 1612 -60 118 create:creative_motor[facing=north]
setblock 1618 -61 115 minecraft:hopper[facing=down]
setblock 1618 -62 115 minecraft:chest
setblock 1602 -60 121 create:shaft[axis=z]
setblock 1617 -60 121 create:shaft[axis=z]
sleep createbelt 1602 -60 121 1617 -60 121
setblock 1602 -60 122 create:creative_motor[facing=north]
setblock 1602 -59 121 create:chute
setblock 1602 -58 121 minecraft:hopper[facing=down]
setblock 1602 -57 121 minecraft:chest{Items:[{Slot:0b,id:"minecraft:copper_ingot",count:64},{Slot:1b,id:"minecraft:copper_ingot",count:64},{Slot:2b,id:"minecraft:copper_ingot",count:64},{Slot:3b,id:"minecraft:copper_ingot",count:64},{Slot:4b,id:"minecraft:copper_ingot",count:64},{Slot:5b,id:"minecraft:copper_ingot",count:64},{Slot:6b,id:"minecraft:copper_ingot",count:64},{Slot:7b,id:"minecraft:copper_ingot",count:64},{Slot:8b,id:"minecraft:copper_ingot",count:64},{Slot:9b,id:"minecraft:copper_ingot",count:64},{Slot:10b,id:"minecraft:copper_ingot",count:64},{Slot:11b,id:"minecraft:copper_ingot",count:64},{Slot:12b,id:"minecraft:copper_ingot",count:64},{Slot:13b,id:"minecraft:copper_ingot",count:64},{Slot:14b,id:"minecraft:copper_ingot",count:64},{Slot:15b,id:"minecraft:copper_ingot",count:64},{Slot:16b,id:"minecraft:copper_ingot",count:64},{Slot:17b,id:"minecraft:copper_ingot",count:64},{Slot:18b,id:"minecraft:copper_ingot",count:64},{Slot:19b,id:"minecraft:copper_ingot",count:64},{Slot:20b,id:"minecraft:copper_ingot",count:64},{Slot:21b,id:"minecraft:copper_ingot",count:64},{Slot:22b,id:"minecraft:copper_ingot",count:64},{Slot:23b,id:"minecraft:copper_ingot",count:64},{Slot:24b,id:"minecraft:copper_ingot",count:64},{Slot:25b,id:"minecraft:copper_ingot",count:64},{Slot:26b,id:"minecraft:copper_ingot",count:64}]}
setblock 1608 -58 121 create:mechanical_press[facing=east]
setblock 1609 -58 121 create:creative_motor[facing=west]
setblock 1612 -60 122 minecraft:water
setblock 1612 -60 123 create:encased_fan[facing=north]
setblock 1612 -60 124 create:creative_motor[facing=north]
setblock 1618 -61 121 minecraft:hopper[facing=down]
setblock 1618 -62 121 minecraft:chest
setblock 1602 -60 127 create:shaft[axis=z]
setblock 1617 -60 127 create:shaft[axis=z]
sleep createbelt 1602 -60 127 1617 -60 127
setblock 1602 -60 128 create:creative_motor[facing=north]
setblock 1602 -59 127 create:chute
setblock 1602 -58 127 minecraft:hopper[facing=down]
setblock 1602 -57 127 minecraft:chest{Items:[{Slot:0b,id:"minecraft:iron_ingot",count:64},{Slot:1b,id:"minecraft:iron_ingot",count:64},{Slot:2b,id:"minecraft:iron_ingot",count:64},{Slot:3b,id:"minecraft:iron_ingot",count:64},{Slot:4b,id:"minecraft:iron_ingot",count:64},{Slot:5b,id:"minecraft:iron_ingot",count:64},{Slot:6b,id:"minecraft:iron_ingot",count:64},{Slot:7b,id:"minecraft:iron_ingot",count:64},{Slot:8b,id:"minecraft:iron_ingot",count:64},{Slot:9b,id:"minecraft:iron_ingot",count:64},{Slot:10b,id:"minecraft:iron_ingot",count:64},{Slot:11b,id:"minecraft:iron_ingot",count:64},{Slot:12b,id:"minecraft:iron_ingot",count:64},{Slot:13b,id:"minecraft:iron_ingot",count:64},{Slot:14b,id:"minecraft:iron_ingot",count:64},{Slot:15b,id:"minecraft:iron_ingot",count:64},{Slot:16b,id:"minecraft:iron_ingot",count:64},{Slot:17b,id:"minecraft:iron_ingot",count:64},{Slot:18b,id:"minecraft:iron_ingot",count:64},{Slot:19b,id:"minecraft:iron_ingot",count:64},{Slot:20b,id:"minecraft:iron_ingot",count:64},{Slot:21b,id:"minecraft:iron_ingot",count:64},{Slot:22b,id:"minecraft:iron_ingot",count:64},{Slot:23b,id:"minecraft:iron_ingot",count:64},{Slot:24b,id:"minecraft:iron_ingot",count:64},{Slot:25b,id:"minecraft:iron_ingot",count:64},{Slot:26b,id:"minecraft:iron_ingot",count:64}]}
setblock 1608 -58 127 create:mechanical_press[facing=east]
setblock 1609 -58 127 create:creative_motor[facing=west]
setblock 1612 -60 128 minecraft:water
setblock 1612 -60 129 create:encased_fan[facing=north]
setblock 1612 -60 130 create:creative_motor[facing=north]
setblock 1618 -61 127 minecraft:hopper[facing=down]
setblock 1618 -62 127 minecraft:chest
setblock 1602 -60 133 create:shaft[axis=z]
setblock 1617 -60 133 create:shaft[axis=z]
sleep createbelt 1602 -60 133 1617 -60 133
setblock 1602 -60 134 create:creative_motor[facing=north]
setblock 1602 -59 133 create:chute
setblock 1602 -58 133 minecraft:hopper[facing=down]
setblock 1602 -57 133 minecraft:chest{Items:[{Slot:0b,id:"minecraft:gravel",count:64},{Slot:1b,id:"minecraft:gravel",count:64},{Slot:2b,id:"minecraft:gravel",count:64},{Slot:3b,id:"minecraft:gravel",count:64},{Slot:4b,id:"minecraft:gravel",count:64},{Slot:5b,id:"minecraft:gravel",count:64},{Slot:6b,id:"minecraft:gravel",count:64},{Slot:7b,id:"minecraft:gravel",count:64},{Slot:8b,id:"minecraft:gravel",count:64},{Slot:9b,id:"minecraft:gravel",count:64},{Slot:10b,id:"minecraft:gravel",count:64},{Slot:11b,id:"minecraft:gravel",count:64},{Slot:12b,id:"minecraft:gravel",count:64},{Slot:13b,id:"minecraft:gravel",count:64},{Slot:14b,id:"minecraft:gravel",count:64},{Slot:15b,id:"minecraft:gravel",count:64},{Slot:16b,id:"minecraft:gravel",count:64},{Slot:17b,id:"minecraft:gravel",count:64},{Slot:18b,id:"minecraft:gravel",count:64},{Slot:19b,id:"minecraft:gravel",count:64},{Slot:20b,id:"minecraft:gravel",count:64},{Slot:21b,id:"minecraft:gravel",count:64},{Slot:22b,id:"minecraft:gravel",count:64},{Slot:23b,id:"minecraft:gravel",count:64},{Slot:24b,id:"minecraft:gravel",count:64},{Slot:25b,id:"minecraft:gravel",count:64},{Slot:26b,id:"minecraft:gravel",count:64}]}
setblock 1608 -58 133 create:mechanical_press[facing=east]
setblock 1609 -58 133 create:creative_motor[facing=west]
setblock 1612 -60 134 minecraft:water
setblock 1612 -60 135 create:encased_fan[facing=north]
setblock 1612 -60 136 create:creative_motor[facing=north]
setblock 1618 -61 133 minecraft:hopper[facing=down]
setblock 1618 -62 133 minecraft:chest
setblock 1602 -60 139 create:shaft[axis=z]
setblock 1617 -60 139 create:shaft[axis=z]
sleep createbelt 1602 -60 139 1617 -60 139
setblock 1602 -60 140 create:creative_motor[facing=north]
setblock 1602 -59 139 create:chute
setblock 1602 -58 139 minecraft:hopper[facing=down]
setblock 1602 -57 139 minecraft:chest{Items:[{Slot:0b,id:"minecraft:cobblestone",count:64},{Slot:1b,id:"minecraft:cobblestone",count:64},{Slot:2b,id:"minecraft:cobblestone",count:64},{Slot:3b,id:"minecraft:cobblestone",count:64},{Slot:4b,id:"minecraft:cobblestone",count:64},{Slot:5b,id:"minecraft:cobblestone",count:64},{Slot:6b,id:"minecraft:cobblestone",count:64},{Slot:7b,id:"minecraft:cobblestone",count:64},{Slot:8b,id:"minecraft:cobblestone",count:64},{Slot:9b,id:"minecraft:cobblestone",count:64},{Slot:10b,id:"minecraft:cobblestone",count:64},{Slot:11b,id:"minecraft:cobblestone",count:64},{Slot:12b,id:"minecraft:cobblestone",count:64},{Slot:13b,id:"minecraft:cobblestone",count:64},{Slot:14b,id:"minecraft:cobblestone",count:64},{Slot:15b,id:"minecraft:cobblestone",count:64},{Slot:16b,id:"minecraft:cobblestone",count:64},{Slot:17b,id:"minecraft:cobblestone",count:64},{Slot:18b,id:"minecraft:cobblestone",count:64},{Slot:19b,id:"minecraft:cobblestone",count:64},{Slot:20b,id:"minecraft:cobblestone",count:64},{Slot:21b,id:"minecraft:cobblestone",count:64},{Slot:22b,id:"minecraft:cobblestone",count:64},{Slot:23b,id:"minecraft:cobblestone",count:64},{Slot:24b,id:"minecraft:cobblestone",count:64},{Slot:25b,id:"minecraft:cobblestone",count:64},{Slot:26b,id:"minecraft:cobblestone",count:64}]}
setblock 1608 -58 139 create:mechanical_press[facing=east]
setblock 1609 -58 139 create:creative_motor[facing=west]
setblock 1612 -60 140 minecraft:water
setblock 1612 -60 141 create:encased_fan[facing=north]
setblock 1612 -60 142 create:creative_motor[facing=north]
setblock 1618 -61 139 minecraft:hopper[facing=down]
setblock 1618 -62 139 minecraft:chest
setblock 1602 -60 145 create:shaft[axis=z]
setblock 1617 -60 145 create:shaft[axis=z]
sleep createbelt 1602 -60 145 1617 -60 145
setblock 1602 -60 146 create:creative_motor[facing=north]
setblock 1602 -59 145 create:chute
setblock 1602 -58 145 minecraft:hopper[facing=down]
setblock 1602 -57 145 minecraft:chest{Items:[{Slot:0b,id:"minecraft:copper_ingot",count:64},{Slot:1b,id:"minecraft:copper_ingot",count:64},{Slot:2b,id:"minecraft:copper_ingot",count:64},{Slot:3b,id:"minecraft:copper_ingot",count:64},{Slot:4b,id:"minecraft:copper_ingot",count:64},{Slot:5b,id:"minecraft:copper_ingot",count:64},{Slot:6b,id:"minecraft:copper_ingot",count:64},{Slot:7b,id:"minecraft:copper_ingot",count:64},{Slot:8b,id:"minecraft:copper_ingot",count:64},{Slot:9b,id:"minecraft:copper_ingot",count:64},{Slot:10b,id:"minecraft:copper_ingot",count:64},{Slot:11b,id:"minecraft:copper_ingot",count:64},{Slot:12b,id:"minecraft:copper_ingot",count:64},{Slot:13b,id:"minecraft:copper_ingot",count:64},{Slot:14b,id:"minecraft:copper_ingot",count:64},{Slot:15b,id:"minecraft:copper_ingot",count:64},{Slot:16b,id:"minecraft:copper_ingot",count:64},{Slot:17b,id:"minecraft:copper_ingot",count:64},{Slot:18b,id:"minecraft:copper_ingot",count:64},{Slot:19b,id:"minecraft:copper_ingot",count:64},{Slot:20b,id:"minecraft:copper_ingot",count:64},{Slot:21b,id:"minecraft:copper_ingot",count:64},{Slot:22b,id:"minecraft:copper_ingot",count:64},{Slot:23b,id:"minecraft:copper_ingot",count:64},{Slot:24b,id:"minecraft:copper_ingot",count:64},{Slot:25b,id:"minecraft:copper_ingot",count:64},{Slot:26b,id:"minecraft:copper_ingot",count:64}]}
setblock 1608 -58 145 create:mechanical_press[facing=east]
setblock 1609 -58 145 create:creative_motor[facing=west]
setblock 1612 -60 146 minecraft:water
setblock 1612 -60 147 create:encased_fan[facing=north]
setblock 1612 -60 148 create:creative_motor[facing=north]
setblock 1618 -61 145 minecraft:hopper[facing=down]
setblock 1618 -62 145 minecraft:chest
setblock 1602 -60 151 create:shaft[axis=z]
setblock 1617 -60 151 create:shaft[axis=z]
sleep createbelt 1602 -60 151 1617 -60 151
setblock 1602 -60 152 create:creative_motor[facing=north]
setblock 1602 -59 151 create:chute
setblock 1602 -58 151 minecraft:hopper[facing=down]
setblock 1602 -57 151 minecraft:chest{Items:[{Slot:0b,id:"minecraft:iron_ingot",count:64},{Slot:1b,id:"minecraft:iron_ingot",count:64},{Slot:2b,id:"minecraft:iron_ingot",count:64},{Slot:3b,id:"minecraft:iron_ingot",count:64},{Slot:4b,id:"minecraft:iron_ingot",count:64},{Slot:5b,id:"minecraft:iron_ingot",count:64},{Slot:6b,id:"minecraft:iron_ingot",count:64},{Slot:7b,id:"minecraft:iron_ingot",count:64},{Slot:8b,id:"minecraft:iron_ingot",count:64},{Slot:9b,id:"minecraft:iron_ingot",count:64},{Slot:10b,id:"minecraft:iron_ingot",count:64},{Slot:11b,id:"minecraft:iron_ingot",count:64},{Slot:12b,id:"minecraft:iron_ingot",count:64},{Slot:13b,id:"minecraft:iron_ingot",count:64},{Slot:14b,id:"minecraft:iron_ingot",count:64},{Slot:15b,id:"minecraft:iron_ingot",count:64},{Slot:16b,id:"minecraft:iron_ingot",count:64},{Slot:17b,id:"minecraft:iron_ingot",count:64},{Slot:18b,id:"minecraft:iron_ingot",count:64},{Slot:19b,id:"minecraft:iron_ingot",count:64},{Slot:20b,id:"minecraft:iron_ingot",count:64},{Slot:21b,id:"minecraft:iron_ingot",count:64},{Slot:22b,id:"minecraft:iron_ingot",count:64},{Slot:23b,id:"minecraft:iron_ingot",count:64},{Slot:24b,id:"minecraft:iron_ingot",count:64},{Slot:25b,id:"minecraft:iron_ingot",count:64},{Slot:26b,id:"minecraft:iron_ingot",count:64}]}
setblock 1608 -58 151 create:mechanical_press[facing=east]
setblock 1609 -58 151 create:creative_motor[facing=west]
setblock 1612 -60 152 minecraft:water
setblock 1612 -60 153 create:encased_fan[facing=north]
setblock 1612 -60 154 create:creative_motor[facing=north]
setblock 1618 -61 151 minecraft:hopper[facing=down]
setblock 1618 -62 151 minecraft:chest
setblock 1602 -60 157 create:shaft[axis=z]
setblock 1617 -60 157 create:shaft[axis=z]
sleep createbelt 1602 -60 157 1617 -60 157
setblock 1602 -60 158 create:creative_motor[facing=north]
setblock 1602 -59 157 create:chute
setblock 1602 -58 157 minecraft:hopper[facing=down]
setblock 1602 -57 157 minecraft:chest{Items:[{Slot:0b,id:"minecraft:gravel",count:64},{Slot:1b,id:"minecraft:gravel",count:64},{Slot:2b,id:"minecraft:gravel",count:64},{Slot:3b,id:"minecraft:gravel",count:64},{Slot:4b,id:"minecraft:gravel",count:64},{Slot:5b,id:"minecraft:gravel",count:64},{Slot:6b,id:"minecraft:gravel",count:64},{Slot:7b,id:"minecraft:gravel",count:64},{Slot:8b,id:"minecraft:gravel",count:64},{Slot:9b,id:"minecraft:gravel",count:64},{Slot:10b,id:"minecraft:gravel",count:64},{Slot:11b,id:"minecraft:gravel",count:64},{Slot:12b,id:"minecraft:gravel",count:64},{Slot:13b,id:"minecraft:gravel",count:64},{Slot:14b,id:"minecraft:gravel",count:64},{Slot:15b,id:"minecraft:gravel",count:64},{Slot:16b,id:"minecraft:gravel",count:64},{Slot:17b,id:"minecraft:gravel",count:64},{Slot:18b,id:"minecraft:gravel",count:64},{Slot:19b,id:"minecraft:gravel",count:64},{Slot:20b,id:"minecraft:gravel",count:64},{Slot:21b,id:"minecraft:gravel",count:64},{Slot:22b,id:"minecraft:gravel",count:64},{Slot:23b,id:"minecraft:gravel",count:64},{Slot:24b,id:"minecraft:gravel",count:64},{Slot:25b,id:"minecraft:gravel",count:64},{Slot:26b,id:"minecraft:gravel",count:64}]}
setblock 1608 -58 157 create:mechanical_press[facing=east]
setblock 1609 -58 157 create:creative_motor[facing=west]
setblock 1612 -60 158 minecraft:water
setblock 1612 -60 159 create:encased_fan[facing=north]
setblock 1612 -60 160 create:creative_motor[facing=north]
setblock 1618 -61 157 minecraft:hopper[facing=down]
setblock 1618 -62 157 minecraft:chest
setblock 1602 -60 163 create:shaft[axis=z]
setblock 1617 -60 163 create:shaft[axis=z]
sleep createbelt 1602 -60 163 1617 -60 163
setblock 1602 -60 164 create:creative_motor[facing=north]
setblock 1602 -59 163 create:chute
setblock 1602 -58 163 minecraft:hopper[facing=down]
setblock 1602 -57 163 minecraft:chest{Items:[{Slot:0b,id:"minecraft:cobblestone",count:64},{Slot:1b,id:"minecraft:cobblestone",count:64},{Slot:2b,id:"minecraft:cobblestone",count:64},{Slot:3b,id:"minecraft:cobblestone",count:64},{Slot:4b,id:"minecraft:cobblestone",count:64},{Slot:5b,id:"minecraft:cobblestone",count:64},{Slot:6b,id:"minecraft:cobblestone",count:64},{Slot:7b,id:"minecraft:cobblestone",count:64},{Slot:8b,id:"minecraft:cobblestone",count:64},{Slot:9b,id:"minecraft:cobblestone",count:64},{Slot:10b,id:"minecraft:cobblestone",count:64},{Slot:11b,id:"minecraft:cobblestone",count:64},{Slot:12b,id:"minecraft:cobblestone",count:64},{Slot:13b,id:"minecraft:cobblestone",count:64},{Slot:14b,id:"minecraft:cobblestone",count:64},{Slot:15b,id:"minecraft:cobblestone",count:64},{Slot:16b,id:"minecraft:cobblestone",count:64},{Slot:17b,id:"minecraft:cobblestone",count:64},{Slot:18b,id:"minecraft:cobblestone",count:64},{Slot:19b,id:"minecraft:cobblestone",count:64},{Slot:20b,id:"minecraft:cobblestone",count:64},{Slot:21b,id:"minecraft:cobblestone",count:64},{Slot:22b,id:"minecraft:cobblestone",count:64},{Slot:23b,id:"minecraft:cobblestone",count:64},{Slot:24b,id:"minecraft:cobblestone",count:64},{Slot:25b,id:"minecraft:cobblestone",count:64},{Slot:26b,id:"minecraft:cobblestone",count:64}]}
setblock 1608 -58 163 create:mechanical_press[facing=east]
setblock 1609 -58 163 create:creative_motor[facing=west]
setblock 1612 -60 164 minecraft:water
setblock 1612 -60 165 create:encased_fan[facing=north]
setblock 1612 -60 166 create:creative_motor[facing=north]
setblock 1618 -61 163 minecraft:hopper[facing=down]
setblock 1618 -62 163 minecraft:chest
setblock 1602 -60 169 create:shaft[axis=z]
setblock 1617 -60 169 create:shaft[axis=z]
sleep createbelt 1602 -60 169 1617 -60 169
setblock 1602 -60 170 create:creative_motor[facing=north]
setblock 1602 -59 169 create:chute
setblock 1602 -58 169 minecraft:hopper[facing=down]
setblock 1602 -57 169 minecraft:chest{Items:[{Slot:0b,id:"minecraft:copper_ingot",count:64},{Slot:1b,id:"minecraft:copper_ingot",count:64},{Slot:2b,id:"minecraft:copper_ingot",count:64},{Slot:3b,id:"minecraft:copper_ingot",count:64},{Slot:4b,id:"minecraft:copper_ingot",count:64},{Slot:5b,id:"minecraft:copper_ingot",count:64},{Slot:6b,id:"minecraft:copper_ingot",count:64},{Slot:7b,id:"minecraft:copper_ingot",count:64},{Slot:8b,id:"minecraft:copper_ingot",count:64},{Slot:9b,id:"minecraft:copper_ingot",count:64},{Slot:10b,id:"minecraft:copper_ingot",count:64},{Slot:11b,id:"minecraft:copper_ingot",count:64},{Slot:12b,id:"minecraft:copper_ingot",count:64},{Slot:13b,id:"minecraft:copper_ingot",count:64},{Slot:14b,id:"minecraft:copper_ingot",count:64},{Slot:15b,id:"minecraft:copper_ingot",count:64},{Slot:16b,id:"minecraft:copper_ingot",count:64},{Slot:17b,id:"minecraft:copper_ingot",count:64},{Slot:18b,id:"minecraft:copper_ingot",count:64},{Slot:19b,id:"minecraft:copper_ingot",count:64},{Slot:20b,id:"minecraft:copper_ingot",count:64},{Slot:21b,id:"minecraft:copper_ingot",count:64},{Slot:22b,id:"minecraft:copper_ingot",count:64},{Slot:23b,id:"minecraft:copper_ingot",count:64},{Slot:24b,id:"minecraft:copper_ingot",count:64},{Slot:25b,id:"minecraft:copper_ingot",count:64},{Slot:26b,id:"minecraft:copper_ingot",count:64}]}
setblock 1608 -58 169 create:mechanical_press[facing=east]
setblock 1609 -58 169 create:creative_motor[facing=west]
setblock 1612 -60 170 minecraft:water
setblock 1612 -60 171 create:encased_fan[facing=north]
setblock 1612 -60 172 create:creative_motor[facing=north]
setblock 1618 -61 169 minecraft:hopper[facing=down]
setblock 1618 -62 169 minecraft:chest
setblock 1602 -60 175 create:shaft[axis=z]
setblock 1617 -60 175 create:shaft[axis=z]
sleep createbelt 1602 -60 175 1617 -60 175
setblock 1602 -60 176 create:creative_motor[facing=north]
setblock 1602 -59 175 create:chute
setblock 1602 -58 175 minecraft:hopper[facing=down]
setblock 1602 -57 175 minecraft:chest{Items:[{Slot:0b,id:"minecraft:iron_ingot",count:64},{Slot:1b,id:"minecraft:iron_ingot",count:64},{Slot:2b,id:"minecraft:iron_ingot",count:64},{Slot:3b,id:"minecraft:iron_ingot",count:64},{Slot:4b,id:"minecraft:iron_ingot",count:64},{Slot:5b,id:"minecraft:iron_ingot",count:64},{Slot:6b,id:"minecraft:iron_ingot",count:64},{Slot:7b,id:"minecraft:iron_ingot",count:64},{Slot:8b,id:"minecraft:iron_ingot",count:64},{Slot:9b,id:"minecraft:iron_ingot",count:64},{Slot:10b,id:"minecraft:iron_ingot",count:64},{Slot:11b,id:"minecraft:iron_ingot",count:64},{Slot:12b,id:"minecraft:iron_ingot",count:64},{Slot:13b,id:"minecraft:iron_ingot",count:64},{Slot:14b,id:"minecraft:iron_ingot",count:64},{Slot:15b,id:"minecraft:iron_ingot",count:64},{Slot:16b,id:"minecraft:iron_ingot",count:64},{Slot:17b,id:"minecraft:iron_ingot",count:64},{Slot:18b,id:"minecraft:iron_ingot",count:64},{Slot:19b,id:"minecraft:iron_ingot",count:64},{Slot:20b,id:"minecraft:iron_ingot",count:64},{Slot:21b,id:"minecraft:iron_ingot",count:64},{Slot:22b,id:"minecraft:iron_ingot",count:64},{Slot:23b,id:"minecraft:iron_ingot",count:64},{Slot:24b,id:"minecraft:iron_ingot",count:64},{Slot:25b,id:"minecraft:iron_ingot",count:64},{Slot:26b,id:"minecraft:iron_ingot",count:64}]}
setblock 1608 -58 175 create:mechanical_press[facing=east]
setblock 1609 -58 175 create:creative_motor[facing=west]
setblock 1612 -60 176 minecraft:water
setblock 1612 -60 177 create:encased_fan[facing=north]
setblock 1612 -60 178 create:creative_motor[facing=north]
setblock 1618 -61 175 minecraft:hopper[facing=down]
setblock 1618 -62 175 minecraft:chest
setblock 1602 -60 181 create:shaft[axis=z]
setblock 1617 -60 181 create:shaft[axis=z]
sleep createbelt 1602 -60 181 1617 -60 181
setblock 1602 -60 182 create:creative_motor[facing=north]
setblock 1602 -59 181 create:chute
setblock 1602 -58 181 minecraft:hopper[facing=down]
setblock 1602 -57 181 minecraft:chest{Items:[{Slot:0b,id:"minecraft:gravel",count:64},{Slot:1b,id:"minecraft:gravel",count:64},{Slot:2b,id:"minecraft:gravel",count:64},{Slot:3b,id:"minecraft:gravel",count:64},{Slot:4b,id:"minecraft:gravel",count:64},{Slot:5b,id:"minecraft:gravel",count:64},{Slot:6b,id:"minecraft:gravel",count:64},{Slot:7b,id:"minecraft:gravel",count:64},{Slot:8b,id:"minecraft:gravel",count:64},{Slot:9b,id:"minecraft:gravel",count:64},{Slot:10b,id:"minecraft:gravel",count:64},{Slot:11b,id:"minecraft:gravel",count:64},{Slot:12b,id:"minecraft:gravel",count:64},{Slot:13b,id:"minecraft:gravel",count:64},{Slot:14b,id:"minecraft:gravel",count:64},{Slot:15b,id:"minecraft:gravel",count:64},{Slot:16b,id:"minecraft:gravel",count:64},{Slot:17b,id:"minecraft:gravel",count:64},{Slot:18b,id:"minecraft:gravel",count:64},{Slot:19b,id:"minecraft:gravel",count:64},{Slot:20b,id:"minecraft:gravel",count:64},{Slot:21b,id:"minecraft:gravel",count:64},{Slot:22b,id:"minecraft:gravel",count:64},{Slot:23b,id:"minecraft:gravel",count:64},{Slot:24b,id:"minecraft:gravel",count:64},{Slot:25b,id:"minecraft:gravel",count:64},{Slot:26b,id:"minecraft:gravel",count:64}]}
setblock 1608 -58 181 create:mechanical_press[facing=east]
setblock 1609 -58 181 create:creative_motor[facing=west]
setblock 1612 -60 182 minecraft:water
setblock 1612 -60 183 create:encased_fan[facing=north]
setblock 1612 -60 184 create:creative_motor[facing=north]
setblock 1618 -61 181 minecraft:hopper[facing=down]
setblock 1618 -62 181 minecraft:chest
setblock 1602 -60 187 create:shaft[axis=z]
setblock 1617 -60 187 create:shaft[axis=z]
sleep createbelt 1602 -60 187 1617 -60 187
setblock 1602 -60 188 create:creative_motor[facing=north]
setblock 1602 -59 187 create:chute
setblock 1602 -58 187 minecraft:hopper[facing=down]
setblock 1602 -57 187 minecraft:chest{Items:[{Slot:0b,id:"minecraft:cobblestone",count:64},{Slot:1b,id:"minecraft:cobblestone",count:64},{Slot:2b,id:"minecraft:cobblestone",count:64},{Slot:3b,id:"minecraft:cobblestone",count:64},{Slot:4b,id:"minecraft:cobblestone",count:64},{Slot:5b,id:"minecraft:cobblestone",count:64},{Slot:6b,id:"minecraft:cobblestone",count:64},{Slot:7b,id:"minecraft:cobblestone",count:64},{Slot:8b,id:"minecraft:cobblestone",count:64},{Slot:9b,id:"minecraft:cobblestone",count:64},{Slot:10b,id:"minecraft:cobblestone",count:64},{Slot:11b,id:"minecraft:cobblestone",count:64},{Slot:12b,id:"minecraft:cobblestone",count:64},{Slot:13b,id:"minecraft:cobblestone",count:64},{Slot:14b,id:"minecraft:cobblestone",count:64},{Slot:15b,id:"minecraft:cobblestone",count:64},{Slot:16b,id:"minecraft:cobblestone",count:64},{Slot:17b,id:"minecraft:cobblestone",count:64},{Slot:18b,id:"minecraft:cobblestone",count:64},{Slot:19b,id:"minecraft:cobblestone",count:64},{Slot:20b,id:"minecraft:cobblestone",count:64},{Slot:21b,id:"minecraft:cobblestone",count:64},{Slot:22b,id:"minecraft:cobblestone",count:64},{Slot:23b,id:"minecraft:cobblestone",count:64},{Slot:24b,id:"minecraft:cobblestone",count:64},{Slot:25b,id:"minecraft:cobblestone",count:64},{Slot:26b,id:"minecraft:cobblestone",count:64}]}
setblock 1608 -58 187 create:mechanical_press[facing=east]
setblock 1609 -58 187 create:creative_motor[facing=west]
setblock 1612 -60 188 minecraft:water
setblock 1612 -60 189 create:encased_fan[facing=north]
setblock 1612 -60 190 create:creative_motor[facing=north]
setblock 1618 -61 187 minecraft:hopper[facing=down]
setblock 1618 -62 187 minecraft:chest
setblock 1602 -60 193 create:shaft[axis=z]
setblock 1617 -60 193 create:shaft[axis=z]
sleep createbelt 1602 -60 193 1617 -60 193
setblock 1602 -60 194 create:creative_motor[facing=north]
setblock 1602 -59 193 create:chute
setblock 1602 -58 193 minecraft:hopper[facing=down]
setblock 1602 -57 193 minecraft:chest{Items:[{Slot:0b,id:"minecraft:copper_ingot",count:64},{Slot:1b,id:"minecraft:copper_ingot",count:64},{Slot:2b,id:"minecraft:copper_ingot",count:64},{Slot:3b,id:"minecraft:copper_ingot",count:64},{Slot:4b,id:"minecraft:copper_ingot",count:64},{Slot:5b,id:"minecraft:copper_ingot",count:64},{Slot:6b,id:"minecraft:copper_ingot",count:64},{Slot:7b,id:"minecraft:copper_ingot",count:64},{Slot:8b,id:"minecraft:copper_ingot",count:64},{Slot:9b,id:"minecraft:copper_ingot",count:64},{Slot:10b,id:"minecraft:copper_ingot",count:64},{Slot:11b,id:"minecraft:copper_ingot",count:64},{Slot:12b,id:"minecraft:copper_ingot",count:64},{Slot:13b,id:"minecraft:copper_ingot",count:64},{Slot:14b,id:"minecraft:copper_ingot",count:64},{Slot:15b,id:"minecraft:copper_ingot",count:64},{Slot:16b,id:"minecraft:copper_ingot",count:64},{Slot:17b,id:"minecraft:copper_ingot",count:64},{Slot:18b,id:"minecraft:copper_ingot",count:64},{Slot:19b,id:"minecraft:copper_ingot",count:64},{Slot:20b,id:"minecraft:copper_ingot",count:64},{Slot:21b,id:"minecraft:copper_ingot",count:64},{Slot:22b,id:"minecraft:copper_ingot",count:64},{Slot:23b,id:"minecraft:copper_ingot",count:64},{Slot:24b,id:"minecraft:copper_ingot",count:64},{Slot:25b,id:"minecraft:copper_ingot",count:64},{Slot:26b,id:"minecraft:copper_ingot",count:64}]}
setblock 1608 -58 193 create:mechanical_press[facing=east]
setblock 1609 -58 193 create:creative_motor[facing=west]
setblock 1612 -60 194 minecraft:water
setblock 1612 -60 195 create:encased_fan[facing=north]
setblock 1612 -60 196 create:creative_motor[facing=north]
setblock 1618 -61 193 minecraft:hopper[facing=down]
setblock 1618 -62 193 minecraft:chest
setblock 1626 -59 103 create:water_wheel[facing=east]
fill 1627 -59 103 1656 -59 103 create:shaft[axis=x]
setblock 1626 -60 105 minecraft:water
setblock 1626 -59 109 create:water_wheel[facing=east]
fill 1627 -59 109 1656 -59 109 create:shaft[axis=x]
setblock 1626 -60 111 minecraft:water
setblock 1626 -59 115 create:water_wheel[facing=east]
fill 1627 -59 115 1656 -59 115 create:shaft[axis=x]
setblock 1626 -60 117 minecraft:water
setblock 1626 -59 121 create:water_wheel[facing=east]
fill 1627 -59 121 1656 -59 121 create:shaft[axis=x]
setblock 1626 -60 123 minecraft:water
setblock 1626 -59 127 create:water_wheel[facing=east]
fill 1627 -59 127 1656 -59 127 create:shaft[axis=x]
setblock 1626 -60 129 minecraft:water
setblock 1626 -59 133 create:water_wheel[facing=east]
fill 1627 -59 133 1656 -59 133 create:shaft[axis=x]
setblock 1626 -60 135 minecraft:water
setblock 1626 -59 139 create:water_wheel[facing=east]
fill 1627 -59 139 1656 -59 139 create:shaft[axis=x]
setblock 1626 -60 141 minecraft:water
setblock 1626 -59 145 create:water_wheel[facing=east]
fill 1627 -59 145 1656 -59 145 create:shaft[axis=x]
setblock 1626 -60 147 minecraft:water
setblock 1626 -59 151 create:water_wheel[facing=east]
fill 1627 -59 151 1656 -59 151 create:shaft[axis=x]
setblock 1626 -60 153 minecraft:water
setblock 1626 -59 157 create:water_wheel[facing=east]
fill 1627 -59 157 1656 -59 157 create:shaft[axis=x]
setblock 1626 -60 159 minecraft:water
setblock 1626 -59 163 create:water_wheel[facing=east]
fill 1627 -59 163 1656 -59 163 create:shaft[axis=x]
setblock 1626 -60 165 minecraft:water
setblock 1626 -59 169 create:water_wheel[facing=east]
fill 1627 -59 169 1656 -59 169 create:shaft[axis=x]
setblock 1626 -60 171 minecraft:water
