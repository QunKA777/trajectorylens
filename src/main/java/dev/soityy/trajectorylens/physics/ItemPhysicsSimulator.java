package dev.soityy.trajectorylens.physics;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Runs shadow simulations against a level (common code; client uses it per frame/tick). */
public final class ItemPhysicsSimulator {
    private final SimulatedItemEntity shadow;

    public ItemPhysicsSimulator(Level level) {
        this.shadow = new SimulatedItemEntity(level, 0, 0, 0, ItemStack.EMPTY, 0, 0, 0);
    }

    public SimulatedItemEntity shadow() {
        return this.shadow;
    }

    public TrajectoryPath simulate(double x, double y, double z, double vx, double vy, double vz) {
        return simulate(x, y, z, vx, vy, vz, null);
    }

    public TrajectoryPath simulate(double x, double y, double z, double vx, double vy, double vz, ItemStack stack) {
        TrajectoryPath path = new TrajectoryPath();
        this.shadow.reset(x, y, z, vx, vy, vz, stack);
        Vec3 start = this.shadow.position();
        path.points.add(start);
        path.ticks = 0;
        while (this.shadow.advanceTick()) {
            path.points.add(this.shadow.position());
            path.ticks = this.shadow.simTicks();
        }
        path.endReason = classify();
        return path;
    }

    private TrajectoryPath.EndReason classify() {
        return switch (this.shadow.lastStop()) {
            case HORIZON -> TrajectoryPath.EndReason.HORIZON;
            case VOID -> TrajectoryPath.EndReason.VOID;
            case REST -> TrajectoryPath.EndReason.REST;
            case BURNED -> TrajectoryPath.EndReason.BURNED;
            case UNLOADED -> TrajectoryPath.EndReason.UNLOADED;
            case CONTINUE -> TrajectoryPath.EndReason.RUNNING;
        };
    }
}
