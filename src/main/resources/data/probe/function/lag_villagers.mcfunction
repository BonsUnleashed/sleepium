# probe:lag_villagers - 120 villagers with 64 workstations in a 30x30 fenced pen (brain, sensors, POI, pathfinding)
# area x 800..832, z 100..132
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
forceload add 800 100 832 132
kill @e[type=!minecraft:player,x=800,y=-61,z=100,dx=32,dy=10,dz=32]
fill 800 -60 100 832 -52 132 minecraft:air
fill 800 -61 100 832 -61 132 minecraft:grass_block
fill 800 -60 100 832 -60 100 minecraft:oak_fence
fill 800 -60 132 832 -60 132 minecraft:oak_fence
fill 800 -60 100 800 -60 132 minecraft:oak_fence
fill 832 -60 100 832 -60 132 minecraft:oak_fence
setblock 803 -60 103 minecraft:lectern
setblock 803 -60 107 minecraft:composter
setblock 803 -60 111 minecraft:lectern
setblock 803 -60 115 minecraft:composter
setblock 803 -60 119 minecraft:lectern
setblock 803 -60 123 minecraft:composter
setblock 803 -60 127 minecraft:lectern
setblock 803 -60 131 minecraft:composter
setblock 807 -60 103 minecraft:composter
setblock 807 -60 107 minecraft:lectern
setblock 807 -60 111 minecraft:composter
setblock 807 -60 115 minecraft:lectern
setblock 807 -60 119 minecraft:composter
setblock 807 -60 123 minecraft:lectern
setblock 807 -60 127 minecraft:composter
setblock 807 -60 131 minecraft:lectern
setblock 811 -60 103 minecraft:lectern
setblock 811 -60 107 minecraft:composter
setblock 811 -60 111 minecraft:lectern
setblock 811 -60 115 minecraft:composter
setblock 811 -60 119 minecraft:lectern
setblock 811 -60 123 minecraft:composter
setblock 811 -60 127 minecraft:lectern
setblock 811 -60 131 minecraft:composter
setblock 815 -60 103 minecraft:composter
setblock 815 -60 107 minecraft:lectern
setblock 815 -60 111 minecraft:composter
setblock 815 -60 115 minecraft:lectern
setblock 815 -60 119 minecraft:composter
setblock 815 -60 123 minecraft:lectern
setblock 815 -60 127 minecraft:composter
setblock 815 -60 131 minecraft:lectern
setblock 819 -60 103 minecraft:lectern
setblock 819 -60 107 minecraft:composter
setblock 819 -60 111 minecraft:lectern
setblock 819 -60 115 minecraft:composter
setblock 819 -60 119 minecraft:lectern
setblock 819 -60 123 minecraft:composter
setblock 819 -60 127 minecraft:lectern
setblock 819 -60 131 minecraft:composter
setblock 823 -60 103 minecraft:composter
setblock 823 -60 107 minecraft:lectern
setblock 823 -60 111 minecraft:composter
setblock 823 -60 115 minecraft:lectern
setblock 823 -60 119 minecraft:composter
setblock 823 -60 123 minecraft:lectern
setblock 823 -60 127 minecraft:composter
setblock 823 -60 131 minecraft:lectern
setblock 827 -60 103 minecraft:lectern
setblock 827 -60 107 minecraft:composter
setblock 827 -60 111 minecraft:lectern
setblock 827 -60 115 minecraft:composter
setblock 827 -60 119 minecraft:lectern
setblock 827 -60 123 minecraft:composter
setblock 827 -60 127 minecraft:lectern
setblock 827 -60 131 minecraft:composter
setblock 831 -60 103 minecraft:composter
setblock 831 -60 107 minecraft:lectern
setblock 831 -60 111 minecraft:composter
setblock 831 -60 115 minecraft:lectern
setblock 831 -60 119 minecraft:composter
setblock 831 -60 123 minecraft:lectern
setblock 831 -60 127 minecraft:composter
setblock 831 -60 131 minecraft:lectern
summon minecraft:villager 802.5 -60 102.5 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 802.5 -60 105.3 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 802.5 -60 108.1 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 802.5 -60 110.9 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 802.5 -60 113.7 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 802.5 -60 116.5 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 802.5 -60 119.3 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 802.5 -60 122.1 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 802.5 -60 124.9 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 802.5 -60 127.7 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 804.9 -60 102.5 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 804.9 -60 105.3 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 804.9 -60 108.1 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 804.9 -60 110.9 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 804.9 -60 113.7 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 804.9 -60 116.5 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 804.9 -60 119.3 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 804.9 -60 122.1 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 804.9 -60 124.9 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 804.9 -60 127.7 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 807.3 -60 102.5 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 807.3 -60 105.3 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 807.3 -60 108.1 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 807.3 -60 110.9 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 807.3 -60 113.7 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 807.3 -60 116.5 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 807.3 -60 119.3 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 807.3 -60 122.1 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 807.3 -60 124.9 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 807.3 -60 127.7 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 809.7 -60 102.5 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 809.7 -60 105.3 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 809.7 -60 108.1 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 809.7 -60 110.9 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 809.7 -60 113.7 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 809.7 -60 116.5 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 809.7 -60 119.3 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 809.7 -60 122.1 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 809.7 -60 124.9 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 809.7 -60 127.7 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 812.1 -60 102.5 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 812.1 -60 105.3 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 812.1 -60 108.1 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 812.1 -60 110.9 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 812.1 -60 113.7 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 812.1 -60 116.5 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 812.1 -60 119.3 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 812.1 -60 122.1 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 812.1 -60 124.9 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 812.1 -60 127.7 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 814.5 -60 102.5 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 814.5 -60 105.3 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 814.5 -60 108.1 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 814.5 -60 110.9 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 814.5 -60 113.7 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 814.5 -60 116.5 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 814.5 -60 119.3 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 814.5 -60 122.1 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 814.5 -60 124.9 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 814.5 -60 127.7 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 816.9 -60 102.5 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 816.9 -60 105.3 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 816.9 -60 108.1 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 816.9 -60 110.9 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 816.9 -60 113.7 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 816.9 -60 116.5 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 816.9 -60 119.3 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 816.9 -60 122.1 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 816.9 -60 124.9 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 816.9 -60 127.7 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 819.3 -60 102.5 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 819.3 -60 105.3 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 819.3 -60 108.1 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 819.3 -60 110.9 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 819.3 -60 113.7 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 819.3 -60 116.5 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 819.3 -60 119.3 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 819.3 -60 122.1 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 819.3 -60 124.9 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 819.3 -60 127.7 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 821.7 -60 102.5 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 821.7 -60 105.3 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 821.7 -60 108.1 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 821.7 -60 110.9 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 821.7 -60 113.7 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 821.7 -60 116.5 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 821.7 -60 119.3 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 821.7 -60 122.1 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 821.7 -60 124.9 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 821.7 -60 127.7 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 824.1 -60 102.5 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 824.1 -60 105.3 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 824.1 -60 108.1 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 824.1 -60 110.9 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 824.1 -60 113.7 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 824.1 -60 116.5 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 824.1 -60 119.3 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 824.1 -60 122.1 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 824.1 -60 124.9 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 824.1 -60 127.7 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 826.5 -60 102.5 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 826.5 -60 105.3 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 826.5 -60 108.1 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 826.5 -60 110.9 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 826.5 -60 113.7 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 826.5 -60 116.5 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 826.5 -60 119.3 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 826.5 -60 122.1 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 826.5 -60 124.9 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 826.5 -60 127.7 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 828.9 -60 102.5 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 828.9 -60 105.3 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 828.9 -60 108.1 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 828.9 -60 110.9 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 828.9 -60 113.7 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 828.9 -60 116.5 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 828.9 -60 119.3 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 828.9 -60 122.1 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 828.9 -60 124.9 {NoAI:0b,PersistenceRequired:1b}
summon minecraft:villager 828.9 -60 127.7 {NoAI:0b,PersistenceRequired:1b}
tellraw @a {"text":"probe: lag_villagers built","color":"yellow"}
