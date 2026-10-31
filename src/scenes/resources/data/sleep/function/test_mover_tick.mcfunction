# sleep:test_mover_tick - one step of the circling targets (sleep:test_mover), every tick
execute as @e[tag=mover] at @s run tp @s ^ ^ ^0.25 ~6 ~
schedule function sleep:test_mover_tick 1t
