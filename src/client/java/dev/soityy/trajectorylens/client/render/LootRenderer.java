package dev.soityy.trajectorylens.client.render;

import dev.soityy.trajectorylens.client.track.Hoppers;
import dev.soityy.trajectorylens.client.track.LootTracker;

import dev.soityy.trajectorylens.client.track.LootTracker.Event;
import dev.soityy.trajectorylens.client.track.LootTracker.HopperGlow;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Ghost markers for lost items plus fading highlights on hoppers that ate them. */
public final class LootRenderer {

    private LootRenderer() {
    }

    public static void draw(LootTracker loot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !loot.enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        float life = Math.max(1000L, loot.markerMillis());
        for (Event e : loot.recentEvents()) {
            int color = e.reason.color;
            double y = e.pos.y + 0.35;
            Vec3 p = new Vec3(e.pos.x, y, e.pos.z);
            // fade the marker out over the configured lifetime so the screen stays clean
            float left = Math.max(0.0F, Math.min(1.0F, 1.0F - (now - e.millis) / life));
            int alpha = (int) (0xE0 * Math.min(1.0F, left * 2.0F));
            int edge = (alpha << 24) | (color & 0xFFFFFF);
            Gizmos.circle(p, 0.4F, GizmoStyle.strokeAndFill(edge, 1.8F, (int) (0x33 * left) << 24 | (color & 0xFFFFFF)))
                .setAlwaysOnTop();
            Gizmos.point(p, edge, 0.22F).setAlwaysOnTop();
            Gizmos.billboardText(String.format("§7%s §f%s×%d §7%.0fs", e.reason.label, e.itemName, e.count, left * life / 1000.0F),
                p.add(0, 0.45, 0), TextGizmo.Style.forColorAndCentered(0xFFFFFFFF).withScale(0.65F)).setAlwaysOnTop();
        }

        // live items close to their five-minute despawn timer
        for (LootTracker.Warning warn : loot.warnings()) {
            boolean urgent = warn.remainingTicks() <= 600;
            int rgb = urgent ? 0xFF4040 : 0xFFC040;
            Vec3 p = new Vec3(warn.pos().x, warn.pos().y + 0.9, warn.pos().z);
            Gizmos.circle(p, 0.34F, GizmoStyle.strokeAndFill((0xEE << 24) | rgb, 2.2F, (0x33 << 24) | rgb))
                .setAlwaysOnTop();
            Gizmos.billboardText(String.format("§6≤%ds 消失 §f%s", warn.remainingTicks() / 20, warn.name()),
                p.add(0, 0.35, 0), TextGizmo.Style.forColorAndCentered(0xFFFFFFFF).withScale(0.7F)).setAlwaysOnTop();
        }

        // hoppers that just swallowed an item: highlight fades over the configured duration,
        // refreshed on every new pickup
        long glowLife = Math.max(500L, loot.glowMillis());
        for (HopperGlow g : loot.glows()) {
            float remain = Math.max(0.0F, Math.min(1.0F, (g.expireMillis - now) / (float) glowLife));
            int alpha = (int) (0xEE * remain);
            int stroke = (alpha << 24) | 0x50FFB0;
            int fillA = (int) (0x66 * remain);
            int fill = (fillA << 24) | 0x50FFB0;
            AABB box;
            Vec3 center;
            if (g.pos != null) {
                box = new AABB(g.pos);
                center = new Vec3(g.pos.getX() + 0.5, g.pos.getY() + 0.5, g.pos.getZ() + 0.5);
            } else if (g.cart != null && g.cart.isAlive()) {
                box = g.cart.getBoundingBox().inflate(0.06);
                center = g.cart.position().add(0, 0.4, 0);
            } else {
                continue;
            }
            Gizmos.cuboid(box, GizmoStyle.strokeAndFill(stroke, 2.4F, fill), false).setAlwaysOnTop();
            if (g.from != null) {
                Gizmos.line(g.from, center, (alpha << 24) | 0x50FFB0, 1.6F).setAlwaysOnTop();
            }
            Gizmos.billboardText(String.format("§a吸走 §f%s §7%.1fs", g.label, remain * glowLife / 1000.0F),
                center.add(0, 0.9, 0), TextGizmo.Style.forColorAndCentered(0xFFB0FFD0).withScale(0.7F)).setAlwaysOnTop();
        }
    }
}
