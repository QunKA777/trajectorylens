package dev.soityy.trajectorylens.client.render;

import dev.soityy.trajectorylens.client.track.OverlayState;

import dev.soityy.trajectorylens.client.track.OverlayState.TrackedItem;
import dev.soityy.trajectorylens.physics.TrajectoryPath;
import dev.soityy.trajectorylens.util.PathTools;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws predicted drop paths as translucent segments ("stained glass" look)
 * using the vanilla 26.2 Gizmo API. Every tracked item type has its own color;
 * all elements are always-on-top so the paths are fully see-through terrain.
 * Purely client side, no particles, no world changes.
 */
public final class TrajectoryRenderer {

    private static final float SEGMENT_SPACING = 0.28F;  // blocks between glass segments
    private static final float SEGMENT_SIZE = 0.32F;     // cube edge length
    private static final int MAX_GIZMOS = 2000;          // per-frame safety cap

    private TrajectoryRenderer() {
    }

    public static void draw(OverlayState state) {
        if (!state.visible()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        int budget = MAX_GIZMOS;
        for (TrackedItem t : state.tracked().values()) {
            // Never draw from a stale path after the real item vanished (would flicker).
            if (level == null || level.getEntity(t.entity.getId()) != t.entity || !t.entity.isAlive()) {
                continue;
            }
            TrajectoryPath path = t.path;
            if (path == null || path.points.size() < 2) {
                continue;
            }
            budget -= drawPath(path, t.color, budget);
            if (budget <= 0) {
                break;
            }
        }
    }

    private static int drawPath(TrajectoryPath path, int itemColor, int budget) {
        var pts = PathTools.decimate(path.points, PathTools.MAX_SAMPLES);
        double total = 0.0;
        for (int i = 1; i < pts.size(); i++) {
            total += pts.get(i - 1).distanceTo(pts.get(i));
        }
        if (total < 1.0E-3) {
            return 0;
        }
        int rgb = itemColor & 0xFFFFFF;
        int fill = (0xEA << 24) | rgb;                                   // strong translucent fill
        int stroke = PathTools.lerpArgb(0xFF000000 | rgb, 0xFFFFFFFF, 0.45F); // readable outline
        stroke = (stroke & 0xFFFFFF) | 0xFF000000;
        int drawn = 0;
        Vec3 prev = pts.get(0);
        for (int i = 1; i < pts.size() && budget > 0; i++) {
            Vec3 cur = pts.get(i);
            double segLen = prev.distanceTo(cur);
            if (segLen < 1.0E-5) {
                prev = cur;
                continue;
            }
            int steps = Math.max(1, (int) Math.ceil(segLen / SEGMENT_SPACING));
            for (int k = 1; k <= steps; k++) {
                float f = (float) (k / (double) steps);
                Vec3 pos = prev.lerp(cur, f);
                AABB box = new AABB(
                    pos.x - SEGMENT_SIZE / 2, pos.y - SEGMENT_SIZE / 2, pos.z - SEGMENT_SIZE / 2,
                    pos.x + SEGMENT_SIZE / 2, pos.y + SEGMENT_SIZE / 2, pos.z + SEGMENT_SIZE / 2);
                Gizmos.cuboid(box, GizmoStyle.strokeAndFill(stroke, 0.9F, fill), false).setAlwaysOnTop();
                drawn++;
                budget--;
                if (budget <= 0) {
                    break;
                }
            }
            prev = cur;
        }
        if (budget > 0) {
            drawn += drawEndMarker(path, itemColor, budget);
        }
        return drawn;
    }

    private static int drawEndMarker(TrajectoryPath path, int itemColor, int budget) {
        Vec3 end = path.endPoint();
        int rgb = itemColor & 0xFFFFFF;
        switch (path.endReason) {
            case REST -> {
                int discFill = (0x99 << 24) | rgb;
                int discStroke = PathTools.lerpArgb(0xFF000000 | rgb, 0xFFFFFFFF, 0.6F) | 0xFF000000;
                Gizmos.circle(end, 0.5F, GizmoStyle.strokeAndFill(discStroke, 1.8F, discFill)).setAlwaysOnTop();
                float eta = path.ticks / 20.0F;
                Gizmos.billboardText(String.format("rest ~%.1fs", eta), end.add(0.0, 0.6, 0.0),
                    TextGizmo.Style.forColorAndCentered(0xFFFFFFFF).withScale(0.8F)).setAlwaysOnTop();
                return 2;
            }
            case BURNED -> {
                Gizmos.point(end, PathTools.argb(0xFF, 0xFF, 0x8C, 0x1A), 0.45F).setAlwaysOnTop();
                Gizmos.billboardText("burns", end.add(0.0, 0.5, 0.0),
                    TextGizmo.Style.forColorAndCentered(0xFFFFD9A0).withScale(0.7F)).setAlwaysOnTop();
                return 2;
            }
            case HORIZON, UNLOADED -> {
                Gizmos.point(end, PathTools.argb(0xFF, 0xFF, 0xAA, 0xFF), 0.35F).setAlwaysOnTop();
                return 1;
            }
            default -> {
                return 0;
            }
        }
    }
}
