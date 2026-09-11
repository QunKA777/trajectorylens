package dev.soityy.trajectorylens.client.render;

import dev.soityy.trajectorylens.client.track.ThreatOverlay;

import dev.soityy.trajectorylens.client.track.ThreatOverlay.Level;
import dev.soityy.trajectorylens.client.track.ThreatOverlay.Threat;
import dev.soityy.trajectorylens.util.PathTools;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

/** Draws the threat rings/labels for hostile mobs. */
public final class ThreatRenderer {

    private static final int LOCKED = 0xFFFF3B30;
    private static final int ALERT = 0xFFFFA000;
    private static final int UNAWARE = 0xFF808080;

    private ThreatRenderer() {
    }

    public static void draw(ThreatOverlay overlay) {
        if (!overlay.enabled()) {
            return;
        }
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Vec3 playerPos = mc.player.position();
        for (Threat t : overlay.threats()) {
            if (!t.mob.isAlive()) {
                continue;
            }
            int color = switch (t.level) {
                case LOCKED -> LOCKED;
                case ALERT -> ALERT;
                case UNAWARE -> UNAWARE;
            };
            Vec3 p = t.mob.position();
            Gizmos.circle(p.add(0, 0.06, 0), t.mob.getBbWidth() * 0.85F,
                GizmoStyle.strokeAndFill(color, 1.8F, (0x33000000 | (color & 0xFFFFFF)))).setAlwaysOnTop();
            String label = switch (t.level) {
                case LOCKED -> "§c锁定";
                case ALERT -> "§6警戒";
                case UNAWARE -> "§7未察觉";
            };
            Gizmos.billboardText(label + String.format(" §f%.1fm", t.distance), p.add(0, t.mob.getBbHeight() + 0.45, 0),
                net.minecraft.gizmos.TextGizmo.Style.forColorAndCentered(0xFFFFFFFF).withScale(0.7F)).setAlwaysOnTop();
            if (t.level == Level.LOCKED) {
                Gizmos.line(p.add(0, t.mob.getBbHeight() * 0.6, 0),
                    playerPos.add(0, 1.0, 0), PathTools.argb(0x66, 0xFF, 0x3B, 0x30), 1.2F).setAlwaysOnTop();
            }
        }
    }
}
