package dev.lull.opt;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Forge 1.20.1's fluid API runs a Stream pipeline over the entity's fluid heights on every tick of every entity
 * (updateInWaterStateAndDoFluidPushing's fall distance factor and extinguish test, getMaxHeightFluidType) - on the
 * items scene ~9 % of the server thread, nearly always over an empty map. The same reductions as plain loops: min /
 * max keep the first of equal values as Stream.min / max do (reduce with compare <= 0 / >= 0), anyMatch stops at the
 * first match, and the fall distance is still multiplied (by 1 when nothing matched, as orElse(1F)).
 */
@Mixin(Entity.class)
public abstract class EntityFluidLoopMixin {
    @Shadow(remap = false) protected Object2DoubleMap<FluidType> forgeFluidTypeHeight;
    @Shadow protected it.unimi.dsi.fastutil.objects.Object2DoubleMap<TagKey<Fluid>> fluidHeight;
    @Shadow public float fallDistance;

    @Shadow abstract void updateInWaterStateAndDoWaterCurrentPushing();

    @Shadow public abstract Entity getVehicle();

    @Shadow public abstract void clearFire();

    /**
     * @author sleep
     * @reason Forge's stream pipelines as loops (see the class comment)
     */
    @Overwrite
    public boolean updateInWaterStateAndDoFluidPushing() {
        Entity self = (Entity) (Object) this;
        this.fluidHeight.clear();
        this.forgeFluidTypeHeight.clear();
        this.updateInWaterStateAndDoWaterCurrentPushing();
        if (!(this.getVehicle() instanceof Boat)) {
            float factor = 1F;
            boolean any = false;
            boolean extinguish = false;
            for (Object2DoubleMap.Entry<FluidType> e : this.forgeFluidTypeHeight.object2DoubleEntrySet()) {
                FluidType t = e.getKey();
                if (t.isAir() || t.isVanilla()) continue;
                float f = self.getFluidFallDistanceModifier(t);
                if (!any || Float.compare(f, factor) < 0) factor = f;   // min(Float::compare): the first of equals stays
                any = true;
            }
            this.fallDistance *= factor;
            for (Object2DoubleMap.Entry<FluidType> e : this.forgeFluidTypeHeight.object2DoubleEntrySet()) {
                FluidType t = e.getKey();
                if (!t.isAir() && !t.isVanilla() && self.canFluidExtinguish(t)) {
                    extinguish = true;
                    break;
                }
            }
            if (extinguish) this.clearFire();
        }
        return self.isInFluidType();
    }

    /**
     * @author sleep
     * @reason Forge's stream max as a loop (see the class comment)
     */
    @Overwrite(remap = false)
    public FluidType getMaxHeightFluidType() {
        Object2DoubleMap.Entry<FluidType> best = null;
        for (Object2DoubleMap.Entry<FluidType> e : this.forgeFluidTypeHeight.object2DoubleEntrySet())
            if (best == null || Double.compare(e.getDoubleValue(), best.getDoubleValue()) > 0) best = e;   // max: the first of equals stays
        return best != null ? best.getKey() : net.minecraftforge.common.ForgeMod.EMPTY_TYPE.get();
    }
}
