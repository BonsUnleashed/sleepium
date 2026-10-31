# sleep:lag_jobclash - job site competition: 16 composters, each remembered as JOB_SITE by two farmers loaded from NBT
# (one pair in four with equal XP), so PoiCompetitorScan must find competitors and erase the losers' memory;
# JobSiteIndex's verify scene (-Dlab.jobsite.verify=true). Area x 2000..2032, z 100..132
gamerule doDaylightCycle false
gamerule doMobSpawning false
gamerule doTraderSpawning false
gamerule doPatrolSpawning false
gamerule doInsomnia false
time set noon
weather clear
forceload add 2000 100 2032 132
kill @e[type=!minecraft:player,x=2000,y=-61,z=100,dx=32,dy=10,dz=32]
fill 2000 -60 100 2032 -52 132 minecraft:air
fill 2000 -61 100 2032 -61 132 minecraft:grass_block
fill 2000 -60 100 2032 -60 100 minecraft:oak_fence
fill 2000 -60 132 2032 -60 132 minecraft:oak_fence
fill 2000 -60 100 2000 -60 132 minecraft:oak_fence
fill 2032 -60 100 2032 -60 132 minecraft:oak_fence
setblock 2004 -60 104 minecraft:composter
summon minecraft:villager 2005.5 -60 105.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:5,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2004,-60,104],dimension:"minecraft:overworld"}}}}}
summon minecraft:villager 2006.5 -60 105.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:5,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2004,-60,104],dimension:"minecraft:overworld"}}}}}
setblock 2004 -60 111 minecraft:composter
summon minecraft:villager 2005.5 -60 112.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:5,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2004,-60,111],dimension:"minecraft:overworld"}}}}}
summon minecraft:villager 2006.5 -60 112.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:0,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2004,-60,111],dimension:"minecraft:overworld"}}}}}
setblock 2004 -60 118 minecraft:composter
summon minecraft:villager 2005.5 -60 119.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:5,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2004,-60,118],dimension:"minecraft:overworld"}}}}}
summon minecraft:villager 2006.5 -60 119.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:0,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2004,-60,118],dimension:"minecraft:overworld"}}}}}
setblock 2004 -60 125 minecraft:composter
summon minecraft:villager 2005.5 -60 126.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:5,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2004,-60,125],dimension:"minecraft:overworld"}}}}}
summon minecraft:villager 2006.5 -60 126.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:0,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2004,-60,125],dimension:"minecraft:overworld"}}}}}
setblock 2011 -60 104 minecraft:composter
summon minecraft:villager 2012.5 -60 105.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:5,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2011,-60,104],dimension:"minecraft:overworld"}}}}}
summon minecraft:villager 2013.5 -60 105.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:5,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2011,-60,104],dimension:"minecraft:overworld"}}}}}
setblock 2011 -60 111 minecraft:composter
summon minecraft:villager 2012.5 -60 112.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:5,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2011,-60,111],dimension:"minecraft:overworld"}}}}}
summon minecraft:villager 2013.5 -60 112.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:0,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2011,-60,111],dimension:"minecraft:overworld"}}}}}
setblock 2011 -60 118 minecraft:composter
summon minecraft:villager 2012.5 -60 119.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:5,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2011,-60,118],dimension:"minecraft:overworld"}}}}}
summon minecraft:villager 2013.5 -60 119.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:0,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2011,-60,118],dimension:"minecraft:overworld"}}}}}
setblock 2011 -60 125 minecraft:composter
summon minecraft:villager 2012.5 -60 126.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:5,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2011,-60,125],dimension:"minecraft:overworld"}}}}}
summon minecraft:villager 2013.5 -60 126.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:0,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2011,-60,125],dimension:"minecraft:overworld"}}}}}
setblock 2018 -60 104 minecraft:composter
summon minecraft:villager 2019.5 -60 105.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:5,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2018,-60,104],dimension:"minecraft:overworld"}}}}}
summon minecraft:villager 2020.5 -60 105.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:5,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2018,-60,104],dimension:"minecraft:overworld"}}}}}
setblock 2018 -60 111 minecraft:composter
summon minecraft:villager 2019.5 -60 112.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:5,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2018,-60,111],dimension:"minecraft:overworld"}}}}}
summon minecraft:villager 2020.5 -60 112.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:0,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2018,-60,111],dimension:"minecraft:overworld"}}}}}
setblock 2018 -60 118 minecraft:composter
summon minecraft:villager 2019.5 -60 119.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:5,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2018,-60,118],dimension:"minecraft:overworld"}}}}}
summon minecraft:villager 2020.5 -60 119.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:0,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2018,-60,118],dimension:"minecraft:overworld"}}}}}
setblock 2018 -60 125 minecraft:composter
summon minecraft:villager 2019.5 -60 126.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:5,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2018,-60,125],dimension:"minecraft:overworld"}}}}}
summon minecraft:villager 2020.5 -60 126.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:0,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2018,-60,125],dimension:"minecraft:overworld"}}}}}
setblock 2025 -60 104 minecraft:composter
summon minecraft:villager 2026.5 -60 105.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:5,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2025,-60,104],dimension:"minecraft:overworld"}}}}}
summon minecraft:villager 2027.5 -60 105.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:5,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2025,-60,104],dimension:"minecraft:overworld"}}}}}
setblock 2025 -60 111 minecraft:composter
summon minecraft:villager 2026.5 -60 112.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:5,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2025,-60,111],dimension:"minecraft:overworld"}}}}}
summon minecraft:villager 2027.5 -60 112.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:0,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2025,-60,111],dimension:"minecraft:overworld"}}}}}
setblock 2025 -60 118 minecraft:composter
summon minecraft:villager 2026.5 -60 119.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:5,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2025,-60,118],dimension:"minecraft:overworld"}}}}}
summon minecraft:villager 2027.5 -60 119.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:0,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2025,-60,118],dimension:"minecraft:overworld"}}}}}
setblock 2025 -60 125 minecraft:composter
summon minecraft:villager 2026.5 -60 126.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:5,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2025,-60,125],dimension:"minecraft:overworld"}}}}}
summon minecraft:villager 2027.5 -60 126.5 {VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Xp:0,Brain:{memories:{"minecraft:job_site":{value:{pos:[I;2025,-60,125],dimension:"minecraft:overworld"}}}}}
