package dev.soityy.trajectorylens.client.render;

import dev.soityy.trajectorylens.client.Lang;

import dev.soityy.trajectorylens.client.track.FlowTracker;

import dev.soityy.trajectorylens.client.track.FlowTracker.Counter;
import dev.soityy.trajectorylens.util.PathTools;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.Vec3;

/** Draws the chokepoint counter planes with their live rate / backlog labels. */
public final class FlowRenderer {

    private FlowRenderer() {
    }

    public static void draw(FlowTracker flow) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (flow.jamEnabled()) {
            for (FlowTracker.Jam jam : flow.jams()) {
                drawJam(jam);
            }
        }
        if (!flow.countersEnabled()) {
            return;
        }
        for (Counter c : flow.counters().values()) {
            drawCounter(c);
        }
    }

    /**
     * A hopper that keeps refusing items: red box when it is simply jammed (destination
     * full / item can never be taken), blue box when it is redstone-locked on purpose.
     */
    private static void drawJam(FlowTracker.Jam jam) {
        boolean locked = jam.hit.locked();
        int rgb = locked ? 0x60A0FF : 0xFF5040;
        int stroke = (0xEE << 24) | rgb;
        int fill = (0x2A << 24) | rgb;
        Gizmos.cuboid(jam.hit.box().inflate(0.08), GizmoStyle.strokeAndFill(stroke, 3.0F, fill), false)
            .setAlwaysOnTop();
        Gizmos.point(jam.hit.center().add(0, 0.6, 0), stroke, 0.3F).setAlwaysOnTop();
        String label = locked
            ? String.format(Lang.tr("§b红石锁定 §f%d 件 §7(漏斗被红石关掉了)"), jam.items)
            : String.format(Lang.tr("§c堵塞 §f%d 件 §7已 %.1fs §8%s"), jam.items, jam.heldTicks / 20.0, jam.sample);
        Gizmos.billboardText(label, jam.hit.center().add(0, 1.15, 0),
            TextGizmo.Style.forColorAndCentered(0xFFFFFFFF).withScale(0.75F)).setAlwaysOnTop();
    }

    private static void drawCounter(Counter c) {
        Vec3 p = c.pos();
        Vec3 n = new Vec3(c.nx, c.ny, c.nz);
        if (n.lengthSqr() < 1.0E-6) {
            n = new Vec3(0, 1, 0);
        }
        n = n.normalize();
        Vec3 up = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 t1 = n.cross(up).normalize().scale(c.w / 2);
        Vec3 t2 = n.cross(t1).normalize().scale(c.h / 2);
        int stroke = PathTools.argb(0xFF, 0xFF, 0xD0, 0x60);
        int fill = PathTools.argb(0x30, 0xFF, 0xD0, 0x60);
        Vec3 a = p.subtract(t1).subtract(t2);
        Vec3 b = p.add(t1).subtract(t2);
        Vec3 d = p.add(t1).add(t2);
        Vec3 e = p.subtract(t1).add(t2);
        Gizmos.rect(a, b, d, e, GizmoStyle.strokeAndFill(stroke, 1.5F, fill)).setAlwaysOnTop();
        Gizmos.line(a, b, stroke, 2.0F).setAlwaysOnTop();
        Gizmos.line(b, d, stroke, 2.0F).setAlwaysOnTop();
        Gizmos.line(d, e, stroke, 2.0F).setAlwaysOnTop();
        Gizmos.line(e, a, stroke, 2.0F).setAlwaysOnTop();
        String label = String.format(Lang.tr("§e%s§f: %d/min  堆积 %.0f"), c.name, c.perMinute(), c.backlog);
        Gizmos.billboardText(label, p.add(0, c.h / 2 + 0.5, 0),
            TextGizmo.Style.forColorAndCentered(0xFFFFFFFF).withScale(0.8F)).setAlwaysOnTop();
    }
}
