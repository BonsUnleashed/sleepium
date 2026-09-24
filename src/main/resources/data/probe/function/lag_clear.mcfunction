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
gamerule maxEntityCramming 24
kill @e[type=minecraft:item]
