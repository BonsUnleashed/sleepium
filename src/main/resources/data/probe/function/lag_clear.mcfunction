# probe:lag_clear - remove every lag_* scene
kill @e[type=!minecraft:player,x=0,y=-61,z=100,dx=40,dy=8,dz=70]
fill 0 -60 100 40 -54 170 minecraft:air
kill @e[type=!minecraft:player,x=100,y=-61,z=100,dx=50,dy=8,dz=40]
fill 100 -60 100 150 -54 140 minecraft:air
kill @e[type=!minecraft:player,x=200,y=-61,z=100,dx=32,dy=8,dz=32]
fill 200 -60 100 232 -54 132 minecraft:air
kill @e[type=!minecraft:player,x=300,y=-61,z=100,dx=32,dy=8,dz=32]
fill 300 -60 100 332 -54 132 minecraft:air
kill @e[type=!minecraft:player,x=400,y=-61,z=100,dx=4,dy=8,dz=4]
fill 400 -60 100 404 -54 104 minecraft:air
kill @e[type=!minecraft:player,x=500,y=-61,z=100,dx=32,dy=8,dz=32]
fill 500 -60 100 532 -54 132 minecraft:air
kill @e[type=!minecraft:player,x=700,y=-61,z=100,dx=60,dy=8,dz=35]
fill 700 -60 100 760 -54 135 minecraft:air
kill @e[type=!minecraft:player,x=608,y=-61,z=96,dx=31,dy=8,dz=31]
fill 608 -60 96 639 -54 127 minecraft:air
gamerule maxEntityCramming 24
kill @e[type=minecraft:item]
kill @e[type=!minecraft:player,x=800,y=-61,z=100,dx=32,dy=10,dz=32]
fill 800 -60 100 832 -52 132 minecraft:air
kill @e[type=!minecraft:player,x=900,y=-61,z=100,dx=32,dy=8,dz=32]
fill 900 -60 100 932 -54 132 minecraft:air
kill @e[type=!minecraft:player,x=1000,y=-61,z=100,dx=52,dy=8,dz=32]
fill 1000 -60 100 1052 -54 132 minecraft:air
kill @e[type=!minecraft:player,x=1000,y=-61,z=140,dx=30,dy=8,dz=32]
fill 1000 -60 140 1030 -54 172 minecraft:air
schedule clear probe:fluid_cycle
kill @e[type=!minecraft:player,x=1100,y=-61,z=100,dx=64,dy=8,dz=64]
fill 1100 -60 100 1164 -55 164 minecraft:air
kill @e[type=!minecraft:player,x=1200,y=-61,z=100,dx=64,dy=12,dz=64]
fill 1200 -61 100 1264 -50 164 minecraft:air
schedule clear probe:create_clutch_on
schedule clear probe:create_clutch_off
kill @e[type=!minecraft:player,x=1400,y=-61,z=100,dx=124,dy=10,dz=130]
fill 1400 -60 100 1423 -52 230 minecraft:air
fill 1424 -60 100 1447 -52 230 minecraft:air
fill 1448 -60 100 1471 -52 230 minecraft:air
fill 1472 -60 100 1495 -52 230 minecraft:air
fill 1496 -60 100 1519 -52 230 minecraft:air
fill 1520 -60 100 1524 -52 230 minecraft:air
kill @e[type=!minecraft:player,x=1600,y=-63,z=100,dx=60,dy=12,dz=104]
fill 1600 -60 100 1623 -52 204 minecraft:air
fill 1624 -60 100 1647 -52 204 minecraft:air
fill 1648 -60 100 1660 -52 204 minecraft:air
schedule clear probe:physics_srv_tick
kill @e[type=!minecraft:player,x=1800,y=-61,z=100,dx=48,dy=16,dz=48]
fill 1800 -60 100 1848 -48 148 minecraft:air
fill 1800 -61 132 1848 -61 148 minecraft:grass_block
