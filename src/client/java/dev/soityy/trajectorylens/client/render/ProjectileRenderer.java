package dev.soityy.trajectorylens.client.render;

import dev.soityy.trajectorylens.client.track.ChainForecast;
import dev.soityy.trajectorylens.client.track.ProjectileKind;
import dev.soityy.trajectorylens.client.track.ProjectileOverlay;
import dev.soityy.trajectorylens.client.track.ProjectileSim;

import java.util.ArrayList;
import java.util.List;

import dev.soityy.trajectorylens.client.track.ProjectileOverlay.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.Vec3;

/** Draws projectile flight paths, hit markers, TNT fuses and blast rings. */
public final class ProjectileRenderer {

    private static final int MAX_LINES = 4000;

    private ProjectileRenderer() {
    }

    public static void draw(ProjectileOverlay overlay) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        int budget = MAX_LINES;
        for (Entry e : new ArrayList<>(overlay.entries().values())) {
            if (level.getEntity(e.entity.getId()) != e.entity || !e.entity.isAlive()) {
                continue;
            }
            ProjectileSim.Result r = e.result;
            if (r == null || r.points.size() < 2) {
                continue;
            }
            int rgb = e.kind.color & 0xFFFFFF;
            int color = (0xFF << 24) | rgb;
            List<Vec3> pts = r.points;
            if (e.kind != ProjectileKind.CREEPER) { // creepers have no flight path
                for (int i = 0; i + 1 < pts.size() && budget > 0; i++) {
                    Gizmos.line(pts.get(i), pts.get(i + 1), color, 2.0F).setAlwaysOnTop();
                    budget--;
                }
            }
            // hit marker
            if (r.hit != null) {
                Gizmos.circle(r.hit.pos(), 0.4F,
                    GizmoStyle.strokeAndFill(color, 2.0F, (0x66000000 | rgb))).setAlwaysOnTop();
                String txt = r.hit.block() ? "命中方块" : ("命中: " + r.hit.label());
                Gizmos.billboardText("§f" + txt, r.hit.pos().add(0, 0.6, 0),
                    TextGizmo.Style.forColorAndCentered(0xFFFFFFFF).withScale(0.75F)).setAlwaysOnTop();
            }
            // TNT fuse + blast rings
            if (e.kind == ProjectileKind.TNT || e.kind == ProjectileKind.CREEPER) {
                int fuse = r.fuseRemaining >= 0 ? r.fuseRemaining : (r.fuseTicks >= 0 ? r.fuseTicks : 0);
                Vec3 top = e.entity.position().add(0, 1.2, 0);
                String head = e.kind == ProjectileKind.TNT ? "§cTNT" : "§a苦力怕";
                Gizmos.billboardText(String.format("%s §f%.1fs", head, fuse / 20.0F), top,
                    TextGizmo.Style.forColorAndCentered(0xFFFF8888).withScale(0.85F)).setAlwaysOnTop();
                if (!e.impactText.isEmpty()) {
                    Gizmos.billboardText("§7" + e.impactText, top.add(0, 0.32, 0),
                        TextGizmo.Style.forColorAndCentered(0xFFDDDDDD).withScale(0.7F)).setAlwaysOnTop();
                }
                for (net.minecraft.core.BlockPos bp : e.risky) {
                    Gizmos.cuboid(new net.minecraft.world.phys.AABB(bp), GizmoStyle.stroke(0xFFFFD040), false)
                        .setAlwaysOnTop();
                    budget -= 12;
                }
            }
            // ender pearl: teleport destination + landing analysis
            if (e.kind == ProjectileKind.ENDER_PEARL && e.pearlDest != null && !e.pearlText.isEmpty()) {
                int pc = e.pearlDanger ? 0xFFFF4040 : 0xFF40FF80;
                Gizmos.circle(e.pearlDest, 0.55F,
                    GizmoStyle.strokeAndFill(pc, 2.0F, (0x44 << 24) | (pc & 0xFFFFFF))).setAlwaysOnTop();
                Gizmos.billboardText("§b→ 传送落点", e.pearlDest.add(0, 0.9, 0),
                    TextGizmo.Style.forColorAndCentered(0xFF9FE8FF).withScale(0.75F)).setAlwaysOnTop();
                Gizmos.billboardText(e.pearlText, e.pearlDest.add(0, 0.62, 0),
                    TextGizmo.Style.forColorAndCentered(0xFFFFFFFF).withScale(0.72F)).setAlwaysOnTop();
            }

            // causal chain: chained blasts, who gets launched where, falling blocks
            if (e.chain != null) {
                int order = 1;
                for (ChainForecast.Step s : e.chain.steps) {
                    int sc = order == 1 ? 0xFFFF8040 : 0xFFFFC040;
                    Gizmos.circle(s.pos, Math.max(0.6F, s.power * 0.5F),
                        GizmoStyle.strokeAndFill(sc, 1.6F, (0x22000000 | (sc & 0xFFFFFF)))).setAlwaysOnTop();
                    Gizmos.billboardText(String.format("§6%s§f %d) +%.1fs §7威力%.0f", "§l", order,
                        s.delayTicks / 20.0F, s.power), s.pos.add(0, 1.6, 0),
                        TextGizmo.Style.forColorAndCentered(0xFFFFD080).withScale(0.7F)).setAlwaysOnTop();
                    order++;
                }
                int lineNo = 0;
                for (ChainForecast.PushArc arc : e.chain.pushes) {
                    List<Vec3> ap = arc.points;
                    for (int i = 0; i + 1 < ap.size() && budget > 0; i++) {
                        Gizmos.line(ap.get(i), ap.get(i + 1), (0xAA << 24) | (arc.color & 0xFFFFFF), 1.4F).setAlwaysOnTop();
                        budget--;
                    }
                    if (arc.landing != null) {
                        Gizmos.circle(arc.landing, 0.35F, GizmoStyle.stroke(arc.color)).setAlwaysOnTop();
                        Gizmos.billboardText(arc.note, arc.landing.add(0, 0.5 + lineNo * 0.28, 0),
                            TextGizmo.Style.forColorAndCentered(0xFFFFFFFF).withScale(0.68F)).setAlwaysOnTop();
                    }
                    lineNo++;
                }
                int noteNo = 0;
                for (String note : e.chain.notes) {
                    Gizmos.billboardText(note, e.entity.position().add(0, 2.0 + noteNo * 0.28, 0),
                        TextGizmo.Style.forColorAndCentered(0xFFFFFFFF).withScale(0.68F)).setAlwaysOnTop();
                    noteNo++;
                }
            }

            double power = e.explosionPower > 0 ? e.explosionPower : e.kind.explosionPower;
            if (r.exploded && r.explosionPos != null && power > 0) {
                double inner = power;
                double outer = power * 2.0;
                emitSphere(r.explosionPos, inner, (0xCC << 24) | rgb, budget);
                budget -= 3 * 24;
                emitSphere(r.explosionPos, outer, (0x66 << 24) | rgb, budget);
                budget -= 3 * 24;
                Gizmos.point(r.explosionPos, color, 0.5F).setAlwaysOnTop();
            }
        }

        // falling blocks: dashed drop line + landing ring, red when somebody is under it
        for (ProjectileOverlay.Falling f : overlay.falling()) {
            boolean hit = !f.crush().isEmpty();
            int rgb = hit ? 0xFF4848 : 0xC0A060;
            int stroke = (0xEE << 24) | rgb;
            Vec3 a = f.from();
            Vec3 b = f.landing();
            int segs = 8;
            for (int i = 0; i + 1 < segs && budget > 0; i++) {
                Vec3 p0 = a.add(b.subtract(a).scale(i / (double) segs));
                Vec3 p1 = a.add(b.subtract(a).scale((i + 1) / (double) segs));
                Gizmos.line(p0, p1, stroke, 1.6F).setAlwaysOnTop();
                budget--;
            }
            Gizmos.circle(b.add(0, 0.05, 0), 0.5F, GizmoStyle.strokeAndFill(stroke, 2.0F, (0x2A << 24) | rgb))
                .setAlwaysOnTop();
            Gizmos.billboardText(String.format("§6%s §7落点 %.1fs%s", f.block(), f.ticks() / 20.0F,
                hit ? (" §c" + f.crush()) : ""), b.add(0, 0.6, 0),
                TextGizmo.Style.forColorAndCentered(hit ? 0xFFFF9090 : 0xFFFFFFFF).withScale(0.7F)).setAlwaysOnTop();
        }

        // held-item aiming preview
        ProjectileSim.Result aim = overlay.aimResult();
        ProjectileKind aimKind = overlay.aimKind();
        if (aim != null && aimKind != null && aim.points.size() >= 2) {
            int rgb = aimKind.color & 0xFFFFFF;
            int color = (0xFF << 24) | rgb;
            List<Vec3> pts = aim.points;
            for (int i = 0; i + 1 < pts.size() && budget > 0; i++) {
                Gizmos.line(pts.get(i), pts.get(i + 1), color, 1.6F).setAlwaysOnTop();
                budget--;
            }
            Vec3 start = pts.get(0);
            Gizmos.billboardText("§b瞄准 §f" + overlay.aimLabel(), start.add(0, 0.5, 0),
                TextGizmo.Style.forColorAndCentered(0xFFB0FFFF).withScale(0.75F)).setAlwaysOnTop();
            if (aim.hit != null) {
                Gizmos.circle(aim.hit.pos(), 0.35F,
                    GizmoStyle.strokeAndFill(color, 1.8F, (0x55000000 | rgb))).setAlwaysOnTop();
                String txt = aim.hit.block() ? "§7将命中方块" : ("§c将命中: §f" + aim.hit.label());
                Gizmos.billboardText(txt, aim.hit.pos().add(0, 0.55, 0),
                    TextGizmo.Style.forColorAndCentered(0xFFFFFFFF).withScale(0.75F)).setAlwaysOnTop();
            }
            if (aimKind == ProjectileKind.ENDER_PEARL && overlay.aimPearlDest() != null && !overlay.aimPearlText().isEmpty()) {
                Vec3 dest = overlay.aimPearlDest();
                int pc = overlay.aimPearlDanger() ? 0xFFFF4040 : 0xFF40FF80;
                Gizmos.circle(dest, 0.55F,
                    GizmoStyle.strokeAndFill(pc, 2.0F, (0x44 << 24) | (pc & 0xFFFFFF))).setAlwaysOnTop();
                Gizmos.billboardText("§b→ 传送落点", dest.add(0, 0.9, 0),
                    TextGizmo.Style.forColorAndCentered(0xFF9FE8FF).withScale(0.75F)).setAlwaysOnTop();
                Gizmos.billboardText(overlay.aimPearlText(), dest.add(0, 0.62, 0),
                    TextGizmo.Style.forColorAndCentered(0xFFFFFFFF).withScale(0.72F)).setAlwaysOnTop();
            }
        }
    }

    /** Wireframe sphere approximated by three orthogonal rings. */
    private static void emitSphere(Vec3 c, double radius, int color, int budget) {
        int seg = 24;
        for (int plane = 0; plane < 3; plane++) {
            for (int i = 0; i < seg && budget > 0; i++) {
                double a0 = 2 * Math.PI * i / seg;
                double a1 = 2 * Math.PI * (i + 1) / seg;
                Vec3 p0 = ringPoint(c, radius, a0, plane);
                Vec3 p1 = ringPoint(c, radius, a1, plane);
                Gizmos.line(p0, p1, color, 1.6F).setAlwaysOnTop();
                budget--;
            }
        }
    }

    private static Vec3 ringPoint(Vec3 c, double radius, double a, int plane) {
        double x = Math.cos(a) * radius;
        double y = Math.sin(a) * radius;
        return switch (plane) {
            case 0 -> c.add(x, 0, y);
            case 1 -> c.add(x, y, 0);
            default -> c.add(0, x, y);
        };
    }
}
