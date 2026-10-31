package dev.sleep.optsupport;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.AxisCycle;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.OffsetDoubleList;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * {@code Shapes.block().move(x, y, z)} as one object (CubeShapeMixin, in BlockCollisions). Vanilla builds an
 * ArrayVoxelShape with three OffsetDoubleList over CubePointRange: 4 objects per full block per collision query,
 * and every coordinate read goes through a megamorphic DoubleList.
 *
 * <p>Same values: the coordinates are exactly vanilla's {@code 0.0 + x} and {@code 1.0 + x} (CubePointRange(1) gives
 * i / 1.0, OffsetDoubleList adds the offset). collideX is VoxelShape.collideX specialised to this 1x1x1 full shape:
 * the same comparisons with the same epsilons in the same order, findIndex being the binary search over two points.
 * Every other VoxelShape method sees coordinate lists equal to vanilla's, created on first use.
 * {@code -Dlab.opt.cubeCheck=true} compares every collide with a real moved shape, bit for bit.
 */
public final class CubeAt extends VoxelShape {
    private static final DiscreteVoxelShape FULL = ((ShapeAccess) Shapes.block()).sleep$shape();
    private static final boolean CHECK = Boolean.getBoolean("lab.opt.cubeCheck");

    private final double ox, oy, oz;
    private DoubleList xs, ys, zs;

    /** Accessor of VoxelShape.shape (VoxelShapeAccessor mixin). */
    public interface ShapeAccess {
        DiscreteVoxelShape sleep$shape();
    }

    public CubeAt(double x, double y, double z) {
        super(FULL);
        this.ox = x;
        this.oy = y;
        this.oz = z;
    }

    private double off(Direction.Axis axis) {
        return axis == Direction.Axis.X ? ox : axis == Direction.Axis.Y ? oy : oz;
    }

    @Override
    public DoubleList getCoords(Direction.Axis axis) {
        switch (axis) {
            case X -> {
                if (xs == null) xs = new OffsetDoubleList(Shapes.block().getCoords(axis), ox);
                return xs;
            }
            case Y -> {
                if (ys == null) ys = new OffsetDoubleList(Shapes.block().getCoords(axis), oy);
                return ys;
            }
            default -> {
                if (zs == null) zs = new OffsetDoubleList(Shapes.block().getCoords(axis), oz);
                return zs;
            }
        }
    }

    /** Coordinate {@code index} (0 or 1) on an axis: vanilla's (index / 1.0) + offset. */
    @Override
    protected double get(Direction.Axis axis, int index) {
        return (double) index / 1.0 + off(axis);
    }

    /** VoxelShape.findIndex over the two coordinates: -1 below, 0 inside, 1 at or above the far face. */
    @Override
    protected int findIndex(Direction.Axis axis, double position) {
        double o = off(axis);
        if (position < 0.0 / 1.0 + o) return -1;
        return position < 1.0 / 1.0 + o ? 0 : 1;
    }

    @Override
    protected double collideX(AxisCycle movementAxis, AABB collisionBox, double desiredOffset) {
        double r = collideCube(movementAxis, collisionBox, desiredOffset);
        if (CHECK) {
            double v = Shapes.block().move(ox, oy, oz).collide(movementAxis.inverse().cycle(Direction.Axis.X), collisionBox, desiredOffset);
            OptCounters.CUBE_CHECKED.incrementAndGet();
            if (Double.doubleToRawLongBits(v) != Double.doubleToRawLongBits(r) && OptCounters.CUBE_MISMATCHES.incrementAndGet() <= 5)
                System.out.println("[sleep/opt] cube collide MISMATCH at " + ox + "," + oy + "," + oz + ": " + r + " vs vanilla " + v);
        }
        OptCounters.cubeCollides++;
        return r;
    }

    /** VoxelShape.collideX with getSize() == 1 on every axis and isFullWide(0, 0, 0) == true. */
    private double collideCube(AxisCycle movementAxis, AABB collisionBox, double desiredOffset) {
        if (Math.abs(desiredOffset) < 1.0E-7) return 0.0;   // isEmpty() is false
        AxisCycle axiscycle = movementAxis.inverse();
        Direction.Axis a = axiscycle.cycle(Direction.Axis.X);
        Direction.Axis a1 = axiscycle.cycle(Direction.Axis.Y);
        Direction.Axis a2 = axiscycle.cycle(Direction.Axis.Z);
        double d0 = collisionBox.max(a);
        double d1 = collisionBox.min(a);
        int i = findIndex(a, d1 + 1.0E-7);
        int j = findIndex(a, d0 - 1.0E-7);
        int k = Math.max(0, findIndex(a1, collisionBox.min(a1) + 1.0E-7));
        int l = Math.min(1, findIndex(a1, collisionBox.max(a1) - 1.0E-7) + 1);
        int i1 = Math.max(0, findIndex(a2, collisionBox.min(a2) + 1.0E-7));
        int j1 = Math.min(1, findIndex(a2, collisionBox.max(a2) - 1.0E-7) + 1);
        // the loops of the generic code visit only cell (0, 0, 0), which is full
        boolean cross = k < l && i1 < j1;   // k == 0 && l == 1 && i1 == 0 && j1 == 1
        if (desiredOffset > 0.0) {
            if (j + 1 < 1 && cross) {   // l1 = j + 1 = 0
                double d2 = get(a, 0) - d0;
                if (d2 >= -1.0E-7) desiredOffset = Math.min(desiredOffset, d2);
                return desiredOffset;
            }
        } else if (desiredOffset < 0.0) {
            if (i - 1 >= 0 && cross) {   // k2 = i - 1 = 0
                double d3 = get(a, 1) - d1;
                if (d3 <= 1.0E-7) desiredOffset = Math.max(desiredOffset, d3);
                return desiredOffset;
            }
        }
        return desiredOffset;
    }
}
