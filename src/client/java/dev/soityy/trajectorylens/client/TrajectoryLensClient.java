package dev.soityy.trajectorylens.client;

import dev.soityy.trajectorylens.client.command.ClientCommands;
import dev.soityy.trajectorylens.client.render.FlowRenderer;
import dev.soityy.trajectorylens.client.render.LootRenderer;
import dev.soityy.trajectorylens.client.render.ProjectileRenderer;
import dev.soityy.trajectorylens.client.render.ThreatRenderer;
import dev.soityy.trajectorylens.client.render.TrajectoryRenderer;
import dev.soityy.trajectorylens.client.render.WalkRangeRenderer;
import dev.soityy.trajectorylens.client.track.EntityCensus;
import dev.soityy.trajectorylens.client.track.FlowTracker;
import dev.soityy.trajectorylens.client.track.LootTracker;
import dev.soityy.trajectorylens.client.track.OverlayState;
import dev.soityy.trajectorylens.client.track.ProjectileOverlay;
import dev.soityy.trajectorylens.client.track.ThreatOverlay;
import dev.soityy.trajectorylens.client.track.WalkRangeOverlay;
import dev.soityy.trajectorylens.client.ui.KeySettings;
import dev.soityy.trajectorylens.client.ui.ModConfigScreen;
import dev.soityy.trajectorylens.client.ui.Report;
import dev.soityy.trajectorylens.client.ui.SettingsIO;

import dev.soityy.trajectorylens.network.TargetPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;

public class TrajectoryLensClient implements ClientModInitializer {

    private static OverlayState state;
    private static WalkRangeOverlay ranges;
    private static ProjectileOverlay projectiles;
    private static FlowTracker flow;
    private static EntityCensus census;
    private static ThreatOverlay threats;
    private static LootTracker loot;
    private static boolean cfgLoaded;
    private static boolean panelPrev;
    private static int autosaveCounter;

    public static OverlayState drops() {
        return state;
    }

    public static WalkRangeOverlay ranges() {
        return ranges;
    }

    public static ProjectileOverlay projectiles() {
        return projectiles;
    }

    public static FlowTracker flow() {
        return flow;
    }

    public static EntityCensus census() {
        return census;
    }

    public static ThreatOverlay threats() {
        return threats;
    }

    public static LootTracker loot() {
        return loot;
    }

    /** Opens the in-game control panel (if not already on a screen). */
    public static void openPanel() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() == null && state != null && ranges != null && projectiles != null && flow != null) {
            mc.gui.setScreen(new ModConfigScreen(null, state, ranges, projectiles, flow, census, threats, loot, 0));
        }
    }

    private static void applySwitch(String op, boolean isProjectiles) {
        boolean on = switch (op) {
            case "on" -> true;
            case "off" -> false;
            default -> isProjectiles ? !projectiles.projectilesEnabled() : !projectiles.tntEnabled();
        };
        if (isProjectiles) {
            projectiles.setProjectiles(on);
        } else {
            projectiles.setTnt(on);
        }
        SettingsIO.snapshot(state, ranges, projectiles, flow, threats, loot).save();
        var pl = Minecraft.getInstance().player;
        if (pl != null) {
            pl.sendSystemMessage(Component.literal("[TrajectoryLens] "
                + (isProjectiles ? "投掷物轨迹" : "TNT 爆炸预测") + " -> " + (on ? "开" : "关")));
        }
    }

    private static void applyFlowSwitch(String op) {
        boolean on = switch (op) {
            case "on" -> true;
            case "off" -> false;
            default -> !flow.countersEnabled();
        };
        flow.setCounters(on);
        SettingsIO.snapshot(state, ranges, projectiles, flow, threats, loot).save();
        var pl = Minecraft.getInstance().player;
        if (pl != null) {
            pl.sendSystemMessage(Component.literal("[TrajectoryLens] 卡口计数器 -> " + (on ? "开" : "关")));
        }
    }

    private static void applyCounterOp(String rest, net.minecraft.client.player.LocalPlayer player) {
        String[] parts = rest.split(":", 2);
        String op = parts[0];
        String name = parts.length > 1 ? parts[1] : "c";
        switch (op) {
            case "add" -> {
                String err = flow.addCounter(name);
                if (player != null) {
                    player.sendSystemMessage(Component.literal(err != null
                        ? "[TrajectoryLens] " + err
                        : "[TrajectoryLens] 已添加计数器: " + name + " (准星处)"));
                }
            }
            case "remove" -> {
                boolean ok = flow.removeCounter(name);
                if (player != null) {
                    player.sendSystemMessage(Component.literal("[TrajectoryLens] "
                        + (ok ? "已移除计数器: " + name : "没有这个计数器: " + name)));
                }
            }
            case "clear" -> {
                flow.clearCounters();
                if (player != null) {
                    player.sendSystemMessage(Component.literal("[TrajectoryLens] 已清空所有计数器"));
                }
            }
            default -> {
                if (player != null) {
                    player.sendSystemMessage(Component.literal("[TrajectoryLens] " + flow.summary()));
                }
            }
        }
        SettingsIO.snapshot(state, ranges, projectiles, flow, threats, loot).save();
    }

    @Override
    public void onInitializeClient() {
        LoggerFactory.getLogger("trajectorylens")
            .info("[TrajectoryLens] client init: registering tracker/renderer");
        state = new OverlayState();
        state.register();
        ranges = new WalkRangeOverlay();
        ranges.register();
        projectiles = new ProjectileOverlay();
        projectiles.register();
        flow = new FlowTracker();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(flow::tick);
        census = new EntityCensus();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(census::tick);
        threats = new ThreatOverlay();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(threats::tick);
        loot = new LootTracker();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(loot::tick);
        ClientCommands.register(state, ranges, projectiles, flow);
        LevelRenderEvents.BEFORE_GIZMOS.register(ctx -> {
            TrajectoryRenderer.draw(state);
            WalkRangeRenderer.draw(ranges);
            ProjectileRenderer.draw(projectiles);
            FlowRenderer.draw(flow);
            ThreatRenderer.draw(threats);
            LootRenderer.draw(loot);
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            // lazy config restore (needs game directory, applied once)
            if (!cfgLoaded) {
                cfgLoaded = true;
                SettingsIO cfg = SettingsIO.load();
                if (!cfg.rangeKeys.isEmpty()) {
                    KeySettings.rangeChord = KeySettings.chordFrom(cfg.rangeKeys);
                }
                if (!cfg.panelKeys.isEmpty()) {
                    KeySettings.panelChord = KeySettings.chordFrom(cfg.panelKeys);
                }
                state.setEnabledState(cfg.dropsOn);
                ranges.setEnabledState(cfg.rangesOn);
                projectiles.setProjectiles(cfg.projectilesOn);
                projectiles.setTnt(cfg.tntOn);
                projectiles.setAim(cfg.aimOn);
                projectiles.setChain(cfg.chainOn);
                threats.setEnabled(cfg.threatOn);
                loot.setEnabled(cfg.lootOn);
                loot.setMarkerSeconds(cfg.lostMarkerSeconds);
                loot.setGlowSeconds(cfg.hopperGlowSeconds);
                loot.setDespawnWarn(cfg.despawnWarnOn);
                flow.setJam(cfg.jamOn);
                flow.setJamSeconds(cfg.jamSeconds);
                projectiles.setFalling(cfg.fallingOn);
                projectiles.setChainDepth(cfg.chainDepth);
                projectiles.setChainHorizonSeconds(cfg.chainHorizonSeconds);
                flow.setCounters(cfg.flowCountersOn);
                for (var c : cfg.counters) {
                    flow.loadCounter(c.name, c.x, c.y, c.z, c.nx, c.ny, c.nz, c.w, c.h);
                }
                ranges.setHorizon(cfg.horizonSeconds);
                state.queueWatchIds(cfg.dropWatch);
                ranges.queueTypeIds(cfg.rangeWatch);
                for (var en : cfg.dropColors.entrySet()) {
                    state.setItemColor(en.getKey(), en.getValue());
                }
                for (var en : cfg.rangeColors.entrySet()) {
                    ranges.setColor(en.getKey(), en.getValue());
                }
            }
            // panel chord
            if (mc.level != null && mc.getWindow() != null) {
                var win = mc.getWindow();
                boolean chord = KeySettings.allDown(win, KeySettings.panelChord);
                if (chord && !panelPrev) {
                    openPanel();
                }
                panelPrev = chord;
            }
            // periodic autosave keeps G/O+P toggles & commands persisted
            if (++autosaveCounter % 300 == 0 && state != null && ranges != null && projectiles != null
                && flow != null && threats != null) {
                SettingsIO.snapshot(state, ranges, projectiles, flow, threats, loot).save();
            }
        });

        // Server-side /trajectorylens ... commands are forwarded here.
        ClientPlayNetworking.registerGlobalReceiver(TargetPayload.TYPE, (payload, context) ->
            Minecraft.getInstance().execute(() -> {
                if (state == null || Minecraft.getInstance().level == null) {
                    return;
                }
                String v = payload.value();
                var player = Minecraft.getInstance().player;
                if ("toggle".equals(v)) {
                    state.toggle();
                    if (player != null) {
                        player.sendSystemMessage(
                            Component.literal("[TrajectoryLens] drops " + (state.visible() ? "on" : "off")));
                    }
                } else if ("clear".equals(v)) {
                    state.clearTargets();
                    if (player != null) {
                        player.sendSystemMessage(Component.literal("[TrajectoryLens] watch cleared: tracking all items"));
                    }
                } else if (v.startsWith("target:")) {
                    String itemId = v.substring("target:".length());
                    String err = state.setTargetFromId(itemId);
                    if (player != null) {
                        player.sendSystemMessage(Component.literal(err != null
                            ? "[TrajectoryLens] " + err
                            : "[TrajectoryLens] added " + itemId + " — watching: " + state.targetName()));
                    }
                } else if (v.startsWith("color:")) {
                    String rest = v.substring("color:".length());
                    int sep = rest.lastIndexOf(':');
                    if (sep > 0 && player != null) {
                        String id = rest.substring(0, sep);
                        String spec = rest.substring(sep + 1);
                        String err = state.setItemColor(id, spec);
                        if (err != null) {
                            player.sendSystemMessage(Component.literal("[TrajectoryLens] " + err));
                        } else {
                            player.sendSystemMessage(Component.literal(
                                "[TrajectoryLens] color of " + id + " -> " + (spec.equalsIgnoreCase("auto") ? "auto" : "#" + spec)));
                        }
                    }
                } else if (v.equals("list")) {
                    if (player != null) {
                        player.sendSystemMessage(Component.literal("[TrajectoryLens] " + state.listing()));
                        player.sendSystemMessage(Component.literal("[TrajectoryLens] " + ranges.summary()));
                    }
                } else if (v.equals("gui")) {
                    openPanel();
                } else if (v.startsWith("proj:")) {
                    applySwitch(v.substring(5), true);
                } else if (v.startsWith("tnt:")) {
                    applySwitch(v.substring(4), false);
                } else if (v.startsWith("chain:")) {
                    boolean on = switch (v.substring(6)) {
                        case "on" -> true;
                        case "off" -> false;
                        default -> !projectiles.chainEnabled();
                    };
                    projectiles.setChain(on);
                    SettingsIO.snapshot(state, ranges, projectiles, flow, threats, loot).save();
                    if (player != null) {
                        player.sendSystemMessage(Component.literal("[TrajectoryLens] 因果链推演 -> " + (on ? "开" : "关")));
                    }
                } else if (v.startsWith("losttrack:")) {
                    boolean on = switch (v.substring(10)) {
                        case "on" -> true;
                        case "off" -> false;
                        default -> !loot.enabled();
                    };
                    loot.setEnabled(on);
                    SettingsIO.snapshot(state, ranges, projectiles, flow, threats, loot).save();
                    if (player != null) {
                        player.sendSystemMessage(Component.literal("[TrajectoryLens] 失踪溯源 -> " + (on ? "开" : "关")));
                    }
                } else if (v.startsWith("jam:") || v.startsWith("despawn:") || v.startsWith("falling:")) {
                    boolean jam = v.startsWith("jam:");
                    boolean despawn = v.startsWith("despawn:");
                    String op = v.substring(v.indexOf(':') + 1);
                    String label = jam ? "漏斗堵塞检测" : despawn ? "物品寿命提醒" : "下落方块落点";
                    boolean value;
                    if (jam) {
                        value = switch (op) {
                            case "on" -> true;
                            case "off" -> false;
                            default -> !flow.jamEnabled();
                        };
                        flow.setJam(value);
                    } else if (despawn) {
                        value = switch (op) {
                            case "on" -> true;
                            case "off" -> false;
                            default -> !loot.despawnWarnEnabled();
                        };
                        loot.setDespawnWarn(value);
                    } else {
                        value = switch (op) {
                            case "on" -> true;
                            case "off" -> false;
                            default -> !projectiles.fallingEnabled();
                        };
                        projectiles.setFalling(value);
                    }
                    SettingsIO.snapshot(state, ranges, projectiles, flow, threats, loot).save();
                    if (player != null) {
                        player.sendSystemMessage(Component.literal("[TrajectoryLens] " + label + " -> " + (value ? "开" : "关")));
                    }
                } else if (v.startsWith("chaindepth:") || v.startsWith("chainhorizon:")) {
                    boolean depth = v.startsWith("chaindepth:");
                    String arg = v.substring(v.indexOf(':') + 1);
                    if (arg.isEmpty()) {
                        if (depth) {
                            projectiles.cycleChainDepth();
                        } else {
                            projectiles.cycleChainHorizon();
                        }
                    } else {
                        try {
                            int val = Integer.parseInt(arg);
                            if (depth) {
                                projectiles.setChainDepth(val);
                            } else {
                                projectiles.setChainHorizonSeconds(val);
                            }
                        } catch (NumberFormatException ignored) {
                            // keep current value
                        }
                    }
                    SettingsIO.snapshot(state, ranges, projectiles, flow, threats, loot).save();
                    if (player != null) {
                        player.sendSystemMessage(Component.literal("[TrajectoryLens] " + (depth ? "因果链层数" : "击退推演时长") + " -> "
                            + (depth ? projectiles.chainDepth() + " 层" : projectiles.chainHorizonSeconds() + " 秒")));
                    }
                } else if (v.equals("export")) {
                    var f = Report.write(Report.lines(state, ranges, projectiles, flow, census, threats, loot));
                    if (player != null) {
                        player.sendSystemMessage(Component.literal(f != null
                            ? "[TrajectoryLens] 报告已写入 " + f.getName()
                            : "[TrajectoryLens] 报告写入失败"));
                    }
                } else if (v.startsWith("losttime:") || v.startsWith("glowtime:") || v.startsWith("jamtime:")) {
                    String kind = v.substring(0, v.indexOf(':'));
                    String arg = v.substring(v.indexOf(':') + 1);
                    int secs = -1;
                    if (!arg.isEmpty()) {
                        try {
                            secs = Integer.parseInt(arg);
                        } catch (NumberFormatException ignored) {
                            // keep current value
                        }
                    }
                    switch (kind) {
                        case "losttime" -> {
                            if (secs > 0) {
                                loot.setMarkerSeconds(secs);
                            } else {
                                loot.cycleMarkerSeconds();
                            }
                        }
                        case "glowtime" -> {
                            if (secs > 0) {
                                loot.setGlowSeconds(secs);
                            } else {
                                loot.cycleGlowSeconds();
                            }
                        }
                        default -> {
                            if (secs > 0) {
                                flow.setJamSeconds(secs);
                            } else {
                                flow.cycleJamSeconds();
                            }
                        }
                    }
                    SettingsIO.snapshot(state, ranges, projectiles, flow, threats, loot).save();
                    if (player != null) {
                        String label = switch (kind) {
                            case "losttime" -> "失踪标记显示 -> " + loot.markerSeconds() + " 秒";
                            case "glowtime" -> "漏斗高亮时长 -> " + loot.glowSeconds() + " 秒";
                            default -> "堵塞判定时长 -> " + flow.jamSeconds() + " 秒";
                        };
                        player.sendSystemMessage(Component.literal("[TrajectoryLens] " + label));
                    }
                } else if (v.equals("lost") || v.startsWith("lost:")) {
                    if (player != null) {
                        if (v.endsWith(":clear")) {
                            loot.clear();
                            player.sendSystemMessage(Component.literal("[TrajectoryLens] 失踪记录已清空"));
                        } else {
                            for (String line : loot.report()) {
                                player.sendSystemMessage(Component.literal(line));
                            }
                        }
                    }
                } else if (v.startsWith("threat:")) {
                    boolean on = switch (v.substring(7)) {
                        case "on" -> true;
                        case "off" -> false;
                        default -> !threats.enabled();
                    };
                    threats.setEnabled(on);
                    SettingsIO.snapshot(state, ranges, projectiles, flow, threats, loot).save();
                    if (player != null) {
                        player.sendSystemMessage(Component.literal("[TrajectoryLens] 威胁指示 -> " + (on ? "开" : "关")));
                    }
                } else if (v.startsWith("aim:")) {
                    boolean on = switch (v.substring(4)) {
                        case "on" -> true;
                        case "off" -> false;
                        default -> !projectiles.aimEnabled();
                    };
                    projectiles.setAim(on);
                    SettingsIO.snapshot(state, ranges, projectiles, flow, threats, loot).save();
                    if (player != null) {
                        player.sendSystemMessage(Component.literal("[TrajectoryLens] 手持瞄准预测 -> " + (on ? "开" : "关")));
                    }
                } else if (v.startsWith("flowcount:")) {
                    applyFlowSwitch(v.substring(10));
                } else if (v.startsWith("counter:")) {
                    applyCounterOp(v.substring(8), player);
                } else if (v.equals("rangetoggle")) {
                    ranges.toggle();
                    if (player != null) {
                        player.sendSystemMessage(Component.literal("[TrajectoryLens] walk ranges " + (ranges.isEnabled() ? "on" : "off")));
                    }
                } else if (v.equals("rangeclear")) {
                    ranges.clearTypes();
                    if (player != null) {
                        player.sendSystemMessage(Component.literal("[TrajectoryLens] range watch cleared (no walk ranges)"));
                    }
                } else if (v.startsWith("rangeadd:")) {
                    String typeRaw = v.substring("rangeadd:".length());
                    String err = ranges.addTypeFromId(typeRaw);
                    if (player != null) {
                        player.sendSystemMessage(Component.literal(err != null
                            ? "[TrajectoryLens] " + err
                            : "[TrajectoryLens] added " + typeRaw + " to range watch — " + ranges.summary()));
                    }
                } else if (v.startsWith("rangetime:")) {
                    try {
                        int sec = Integer.parseInt(v.substring("rangetime:".length()));
                        ranges.setHorizon(sec);
                        if (player != null) {
                            player.sendSystemMessage(Component.literal("[TrajectoryLens] range horizon = " + ranges.horizonSeconds() + "s"));
                        }
                    } catch (NumberFormatException ignored) {
                    }
                } else if (v.startsWith("rangecolor:")) {
                    String rest = v.substring("rangecolor:".length());
                    int sep = rest.lastIndexOf(':');
                    if (sep > 0 && player != null) {
                        String id = rest.substring(0, sep);
                        String spec = rest.substring(sep + 1);
                        String err = ranges.setColor(id, spec);
                        if (err != null) {
                            player.sendSystemMessage(Component.literal("[TrajectoryLens] " + err));
                        } else {
                            player.sendSystemMessage(Component.literal(
                                "[TrajectoryLens] range color of " + id + " -> " + (spec.equalsIgnoreCase("auto") ? "auto" : "#" + spec)));
                        }
                    }
                } else if (v.equals("census")) {
                    if (player != null) {
                        for (String line : census.report()) {
                            player.sendSystemMessage(Component.literal(line));
                        }
                    }
                } else if (v.equals("rangelist")) {
                    if (player != null) {
                        player.sendSystemMessage(Component.literal("[TrajectoryLens] " + ranges.summary()));
                    }
                }
            }));
    }
}