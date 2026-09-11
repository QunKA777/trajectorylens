package dev.soityy.trajectorylens.client.command;

import dev.soityy.trajectorylens.client.track.FlowTracker;
import dev.soityy.trajectorylens.client.track.LootTracker;
import dev.soityy.trajectorylens.client.track.OverlayState;
import dev.soityy.trajectorylens.client.track.ProjectileOverlay;
import dev.soityy.trajectorylens.client.track.ThreatOverlay;
import dev.soityy.trajectorylens.client.track.WalkRangeOverlay;
import dev.soityy.trajectorylens.client.TrajectoryLensClient;
import dev.soityy.trajectorylens.client.ui.Report;
import dev.soityy.trajectorylens.client.ui.SettingsIO;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Client-side fallbacks of the same command tree (used when the server does not
 * run this mod). With the mod on the server the server copies take precedence.
 */
public final class ClientCommands {
    private ClientCommands() {
    }

    public static void register(OverlayState state, WalkRangeOverlay ranges, ProjectileOverlay projectiles, FlowTracker flow) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> root =
                LiteralArgumentBuilder.<FabricClientCommandSource>literal("trajectorylens");
            root.then(targetTree(state));
            root.then(colorTree(state));
            root.then(rangeTree(ranges));
            root.then(colorsTree(state, ranges));
            root.then(toggleTree(state));
            root.then(statusTree(state, ranges));
            root.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("gui")
                .executes(ctx -> {
                    TrajectoryLensClient.openPanel();
                    return 1;
                }));
            root.then(switchTree("projectiles", projectiles, true, state, ranges, projectiles, flow));
            root.then(switchTree("tnt", projectiles, false, state, ranges, projectiles, flow));
            root.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("losttrack")
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("on").executes(ctx -> lootSwitch(ctx.getSource(), true)))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("off").executes(ctx -> lootSwitch(ctx.getSource(), false)))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("toggle").executes(ctx -> lootSwitch(ctx.getSource(), null))));
            root.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("lost")
                .executes(ctx -> {
                    for (String line : TrajectoryLensClient.loot().report()) {
                        ctx.getSource().sendFeedback(Component.literal(line));
                    }
                    return 1;
                })
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(ctx -> {
                    TrajectoryLensClient.loot().clear();
                    ctx.getSource().sendFeedback(Component.literal("[TrajectoryLens] 失踪记录已清空"));
                    return 1;
                })));
            root.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("threat")
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("on").executes(ctx -> threatSwitch(ctx.getSource(), TrajectoryLensClient.threats(), state, ranges, projectiles, flow, true)))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("off").executes(ctx -> threatSwitch(ctx.getSource(), TrajectoryLensClient.threats(), state, ranges, projectiles, flow, false)))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("toggle").executes(ctx -> threatSwitch(ctx.getSource(), TrajectoryLensClient.threats(), state, ranges, projectiles, flow, null))));
            root.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("census").executes(ctx -> {
                for (String line : TrajectoryLensClient.census().report()) {
                    ctx.getSource().sendFeedback(Component.literal(line));
                }
                return 1;
            }));
            root.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("aim")
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("on").executes(ctx -> aimSwitch(ctx.getSource(), projectiles, flow, state, ranges, true)))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("off").executes(ctx -> aimSwitch(ctx.getSource(), projectiles, flow, state, ranges, false)))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("toggle").executes(ctx -> aimSwitch(ctx.getSource(), projectiles, flow, state, ranges, null))));
            root.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("chain")
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("on").executes(ctx -> chainSwitch(ctx.getSource(), projectiles, flow, state, ranges, true)))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("off").executes(ctx -> chainSwitch(ctx.getSource(), projectiles, flow, state, ranges, false)))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("toggle").executes(ctx -> chainSwitch(ctx.getSource(), projectiles, flow, state, ranges, null))));
            root.then(switchNode("jam", TrajectoryLensClient.flow(), state, ranges, projectiles, flow));
            root.then(switchNode("despawn", TrajectoryLensClient.loot(), state, ranges, projectiles, flow));
            root.then(switchNode("falling", TrajectoryLensClient.projectiles(), state, ranges, projectiles, flow));
            root.then(lostTimeTree("jamtime", "堵塞判定时长", state, ranges, projectiles, flow, false));
            root.then(lostTimeTree("losttime", "失踪标记显示", state, ranges, projectiles, flow, true));
            root.then(lostTimeTree("glowtime", "漏斗高亮时长", state, ranges, projectiles, flow, false));
            root.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("chaindepth")
                .executes(ctx -> chainOption(ctx.getSource(), state, ranges, projectiles, flow, -1, true))
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("layers", IntegerArgumentType.integer(1, 4))
                    .executes(ctx -> chainOption(ctx.getSource(), state, ranges, projectiles, flow,
                        IntegerArgumentType.getInteger(ctx, "layers"), true))));
            root.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("chainhorizon")
                .executes(ctx -> chainOption(ctx.getSource(), state, ranges, projectiles, flow, -1, false))
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("seconds", IntegerArgumentType.integer(1, 30))
                    .executes(ctx -> chainOption(ctx.getSource(), state, ranges, projectiles, flow,
                        IntegerArgumentType.getInteger(ctx, "seconds"), false))));
            root.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("export").executes(ctx -> {
                var f = Report.write(Report.lines(state, ranges, projectiles, flow, TrajectoryLensClient.census(),
                    TrajectoryLensClient.threats(), TrajectoryLensClient.loot()));
                ctx.getSource().sendFeedback(Component.literal(f != null
                    ? "[TrajectoryLens] 报告已写入 " + f.getName()
                    : "[TrajectoryLens] 报告写入失败"));
                return 1;
            }));
            root.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("flowcount")
                .then(flowSwitch("on", state, ranges, projectiles, flow))
                .then(flowSwitch("off", state, ranges, projectiles, flow))
                .then(flowSwitch("toggle", state, ranges, projectiles, flow)));
            root.then(counterTree(state, ranges, projectiles, flow));
            dispatcher.register(root);
            // short alias: /tl ... redirects to the full command tree
            var main = dispatcher.getRoot().getChild("trajectorylens");
            if (main != null) {
                dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("tl").redirect(main));
            }
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> targetTree(OverlayState state) {
        var tree = LiteralArgumentBuilder.<FabricClientCommandSource>literal("target");
        tree.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear")
            .executes(ctx -> {
                state.clearTargets();
                ctx.getSource().sendFeedback(Component.literal("[TrajectoryLens] watch cleared: tracking all items"));
                return 1;
            }));
        tree.then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("item", StringArgumentType.string())
            .suggests(ClientCommands::suggestItems)
            .executes(ctx -> {
                String raw = StringArgumentType.getString(ctx, "item");
                String err = state.setTargetFromId(raw);
                if (err != null) {
                    ctx.getSource().sendError(Component.literal("[TrajectoryLens] " + err));
                    return 0;
                }
                ctx.getSource().sendFeedback(Component.literal(
                    "[TrajectoryLens] added " + raw + " — watching: " + state.targetName()));
                return 1;
            }));
        return tree;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> colorTree(OverlayState state) {
        var tree = LiteralArgumentBuilder.<FabricClientCommandSource>literal("color");
        tree.then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("item", StringArgumentType.string())
            .suggests(ClientCommands::suggestItems)
            .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("color", StringArgumentType.string())
                .executes(ctx -> setColorLocal(ctx.getSource(), state,
                    StringArgumentType.getString(ctx, "item"),
                    StringArgumentType.getString(ctx, "color")))));
        return tree;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> rangeTree(WalkRangeOverlay ranges) {
        var tree = LiteralArgumentBuilder.<FabricClientCommandSource>literal("range");
        tree.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("add")
            .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("type", StringArgumentType.string())
                .suggests(ClientCommands::suggestEntityTypes)
                .executes(ctx -> {
                    String raw = StringArgumentType.getString(ctx, "type");
                    String err = ranges.addTypeFromId(raw);
                    if (err != null) {
                        ctx.getSource().sendError(Component.literal("[TrajectoryLens] " + err));
                        return 0;
                    }
                    ctx.getSource().sendFeedback(Component.literal("[TrajectoryLens] added " + raw + " — " + ranges.summary()));
                    return 1;
                })));
        tree.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear")
            .executes(ctx -> {
                ranges.clearTypes();
                ctx.getSource().sendFeedback(Component.literal("[TrajectoryLens] range watch cleared"));
                return 1;
            }));
        tree.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("list")
            .executes(ctx -> {
                ctx.getSource().sendFeedback(Component.literal("[TrajectoryLens] " + ranges.summary()));
                return 1;
            }));
        tree.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("time")
            .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("seconds", IntegerArgumentType.integer(1, 60))
                .executes(ctx -> {
                    ranges.setHorizon(IntegerArgumentType.getInteger(ctx, "seconds"));
                    ctx.getSource().sendFeedback(Component.literal("[TrajectoryLens] range horizon = " + ranges.horizonSeconds() + "s"));
                    return 1;
                })));
        tree.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color")
            .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("type", StringArgumentType.string())
                .suggests(ClientCommands::suggestEntityTypes)
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("color", StringArgumentType.string())
                    .executes(ctx -> {
                        String typeRaw = StringArgumentType.getString(ctx, "type");
                        String spec = StringArgumentType.getString(ctx, "color");
                        String typeId = WalkRangeOverlay.canonicalTypeId(WalkRangeOverlay.resolveType(typeRaw));
                        if (typeId == null) {
                            ctx.getSource().sendError(Component.literal("[TrajectoryLens] unknown entity type: " + typeRaw));
                            return 0;
                        }
                        String err = ranges.setColor(typeId, spec);
                        if (err != null) {
                            ctx.getSource().sendError(Component.literal("[TrajectoryLens] " + err));
                            return 0;
                        }
                        ctx.getSource().sendFeedback(Component.literal("[TrajectoryLens] range color of " + typeId + " -> " + spec));
                        return 1;
                    }))));
        tree.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("toggle")
            .executes(ctx -> {
                ranges.toggle();
                ctx.getSource().sendFeedback(Component.literal("[TrajectoryLens] walk ranges " + (ranges.isEnabled() ? "on" : "off")));
                return 1;
            }));
        return tree;
    }

    /** on / off / toggle switch for the projectile & TNT overlays. */
    private static int lootSwitch(FabricClientCommandSource src, Boolean on) {
        LootTracker loot = TrajectoryLensClient.loot();
        boolean value = on != null ? on : !loot.enabled();
        loot.setEnabled(value);
        src.sendFeedback(Component.literal("[TrajectoryLens] 失踪溯源 -> " + (value ? "开" : "关")));
        return 1;
    }

    private static int threatSwitch(FabricClientCommandSource src, ThreatOverlay threats, OverlayState state,
                                    WalkRangeOverlay ranges, ProjectileOverlay proj, FlowTracker flow, Boolean on) {
        boolean value = on != null ? on : !threats.enabled();
        threats.setEnabled(value);
        SettingsIO.snapshot(state, ranges, proj, flow, threats, TrajectoryLensClient.loot()).save();
        src.sendFeedback(Component.literal("[TrajectoryLens] 威胁指示 -> " + (value ? "开" : "关")));
        return 1;
    }

    private static int aimSwitch(FabricClientCommandSource src, ProjectileOverlay proj, FlowTracker flow,
                                 OverlayState state, WalkRangeOverlay ranges, Boolean on) {
        boolean value = on != null ? on : !proj.aimEnabled();
        proj.setAim(value);
        SettingsIO.snapshot(state, ranges, proj, flow, TrajectoryLensClient.threats(), TrajectoryLensClient.loot()).save();
        src.sendFeedback(Component.literal("[TrajectoryLens] 手持瞄准预测 -> " + (value ? "开" : "关")));
        return 1;
    }

    /** /trajectorylens jam|despawn|falling on|off|toggle -- the newer client-side overlays. */
    private static LiteralArgumentBuilder<FabricClientCommandSource> switchNode(String name, Object target,
        OverlayState state, WalkRangeOverlay ranges, ProjectileOverlay proj, FlowTracker flow) {
        return LiteralArgumentBuilder.<FabricClientCommandSource>literal(name)
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("on")
                .executes(ctx -> extraSwitch(ctx.getSource(), target, Boolean.TRUE, state, ranges, proj, flow)))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("off")
                .executes(ctx -> extraSwitch(ctx.getSource(), target, Boolean.FALSE, state, ranges, proj, flow)))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("toggle")
                .executes(ctx -> extraSwitch(ctx.getSource(), target, null, state, ranges, proj, flow)));
    }

    private static int extraSwitch(FabricClientCommandSource src, Object target, Boolean on,
                                   OverlayState state, WalkRangeOverlay ranges, ProjectileOverlay proj, FlowTracker flow) {
        String label;
        boolean value;
        if (target instanceof FlowTracker f) {
            label = "漏斗堵塞检测";
            value = on != null ? on : !f.jamEnabled();
            f.setJam(value);
        } else if (target instanceof LootTracker l) {
            label = "物品寿命提醒";
            value = on != null ? on : !l.despawnWarnEnabled();
            l.setDespawnWarn(value);
        } else {
            label = "下落方块落点";
            value = on != null ? on : !proj.fallingEnabled();
            proj.setFalling(value);
        }
        SettingsIO.snapshot(state, ranges, proj, flow, TrajectoryLensClient.threats(), TrajectoryLensClient.loot()).save();
        src.sendFeedback(Component.literal("[TrajectoryLens] " + label + " -> " + (value ? "开" : "关")));
        return 1;
    }

    /** /trajectorylens losttime [秒] | glowtime [秒] -- how long lost-item markers / hopper highlights last. */
    private static LiteralArgumentBuilder<FabricClientCommandSource> lostTimeTree(String name, String label,
        OverlayState state, WalkRangeOverlay ranges, ProjectileOverlay proj, FlowTracker flow, boolean marker) {
        return LiteralArgumentBuilder.<FabricClientCommandSource>literal(name)
            .executes(ctx -> lostTime(ctx.getSource(), label, -1, state, ranges, proj, flow, marker))
            .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("seconds", IntegerArgumentType.integer(1, 300))
                .executes(ctx -> lostTime(ctx.getSource(), label, IntegerArgumentType.getInteger(ctx, "seconds"),
                    state, ranges, proj, flow, marker)));
    }

    private static int lostTime(FabricClientCommandSource src, String label, int seconds,
                                OverlayState state, WalkRangeOverlay ranges, ProjectileOverlay proj, FlowTracker flow, boolean marker) {
        if (label.startsWith("堵塞")) {
            if (seconds > 0) {
                flow.setJamSeconds(seconds);
            } else {
                flow.cycleJamSeconds();
            }
            SettingsIO.snapshot(state, ranges, proj, flow, TrajectoryLensClient.threats(), TrajectoryLensClient.loot()).save();
            src.sendFeedback(Component.literal("[TrajectoryLens] " + label + " -> " + flow.jamSeconds() + " 秒"));
            return 1;
        }
        LootTracker loot = TrajectoryLensClient.loot();
        if (seconds > 0) {
            if (marker) {
                loot.setMarkerSeconds(seconds);
            } else {
                loot.setGlowSeconds(seconds);
            }
        } else if (marker) {
            loot.cycleMarkerSeconds();
        } else {
            loot.cycleGlowSeconds();
        }
        SettingsIO.snapshot(state, ranges, proj, flow, TrajectoryLensClient.threats(), loot).save();
        src.sendFeedback(Component.literal("[TrajectoryLens] " + label + " -> "
            + (marker ? loot.markerSeconds() : loot.glowSeconds()) + " 秒"));
        return 1;
    }

    /** /trajectorylens chaindepth [层] / chainhorizon [秒]. */
    private static int chainOption(FabricClientCommandSource src, OverlayState state, WalkRangeOverlay ranges,
                                   ProjectileOverlay proj, FlowTracker flow, int value, boolean depth) {
        if (depth) {
            if (value > 0) {
                proj.setChainDepth(value);
            } else {
                proj.cycleChainDepth();
            }
        } else {
            if (value > 0) {
                proj.setChainHorizonSeconds(value);
            } else {
                proj.cycleChainHorizon();
            }
        }
        SettingsIO.snapshot(state, ranges, proj, flow, TrajectoryLensClient.threats(), TrajectoryLensClient.loot()).save();
        src.sendFeedback(Component.literal("[TrajectoryLens] " + (depth ? "因果链层数" : "击退推演时长") + " -> "
            + (depth ? proj.chainDepth() + " 层" : proj.chainHorizonSeconds() + " 秒")));
        return 1;
    }

    private static int chainSwitch(FabricClientCommandSource src, ProjectileOverlay proj, FlowTracker flow,
                                   OverlayState state, WalkRangeOverlay ranges, Boolean on) {
        boolean value = on != null ? on : !proj.chainEnabled();
        proj.setChain(value);
        SettingsIO.snapshot(state, ranges, proj, flow, TrajectoryLensClient.threats(), TrajectoryLensClient.loot()).save();
        src.sendFeedback(Component.literal("[TrajectoryLens] 因果链推演 -> " + (value ? "开" : "关")));
        return 1;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> flowSwitch(
        String op, OverlayState state, WalkRangeOverlay ranges, ProjectileOverlay proj, FlowTracker flow) {
        return LiteralArgumentBuilder.<FabricClientCommandSource>literal(op)
            .executes(ctx -> {
                boolean on = switch (op) {
                    case "on" -> true;
                    case "off" -> false;
                    default -> !flow.countersEnabled();
                };
                flow.setCounters(on);
                SettingsIO.snapshot(state, ranges, proj, flow, TrajectoryLensClient.threats(), TrajectoryLensClient.loot()).save();
                ctx.getSource().sendFeedback(Component.literal("[TrajectoryLens] 卡口计数器 -> " + (on ? "开" : "关")));
                return 1;
            });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> counterTree(
        OverlayState state, WalkRangeOverlay ranges, ProjectileOverlay proj, FlowTracker flow) {
        var tree = LiteralArgumentBuilder.<FabricClientCommandSource>literal("counter");
        tree.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("add")
            .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                .executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "name");
                    String err = flow.addCounter(name);
                    if (err != null) {
                        ctx.getSource().sendError(Component.literal("[TrajectoryLens] " + err));
                        return 0;
                    }
                    SettingsIO.snapshot(state, ranges, proj, flow, TrajectoryLensClient.threats(), TrajectoryLensClient.loot()).save();
                    ctx.getSource().sendFeedback(Component.literal("[TrajectoryLens] 已添加计数器: " + name + " (准星处)"));
                    return 1;
                })));
        tree.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("remove")
            .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                .executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "name");
                    boolean ok = flow.removeCounter(name);
                    SettingsIO.snapshot(state, ranges, proj, flow, TrajectoryLensClient.threats(), TrajectoryLensClient.loot()).save();
                    ctx.getSource().sendFeedback(Component.literal("[TrajectoryLens] " + (ok ? "已移除 " + name : "没有 " + name)));
                    return ok ? 1 : 0;
                })));
        tree.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(ctx -> {
            flow.clearCounters();
            SettingsIO.snapshot(state, ranges, proj, flow, TrajectoryLensClient.threats(), TrajectoryLensClient.loot()).save();
            ctx.getSource().sendFeedback(Component.literal("[TrajectoryLens] 已清空计数器"));
            return 1;
        }));
        tree.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("list").executes(ctx -> {
            ctx.getSource().sendFeedback(Component.literal("[TrajectoryLens] " + flow.summary()));
            return 1;
        }));
        return tree;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> switchTree(
        String name, ProjectileOverlay proj, boolean isProjectiles, OverlayState state, WalkRangeOverlay ranges,
        ProjectileOverlay sameProj, FlowTracker flow) {
        var tree = LiteralArgumentBuilder.<FabricClientCommandSource>literal(name);
        for (String op : new String[]{"on", "off", "toggle"}) {
            tree.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal(op)
                .executes(ctx -> {
                    boolean on = switch (op) {
                        case "on" -> true;
                        case "off" -> false;
                        default -> isProjectiles ? !proj.projectilesEnabled() : !proj.tntEnabled();
                    };
                    if (isProjectiles) {
                        proj.setProjectiles(on);
                    } else {
                        proj.setTnt(on);
                    }
                    SettingsIO.snapshot(state, ranges, proj, flow, TrajectoryLensClient.threats(), TrajectoryLensClient.loot()).save();
                    ctx.getSource().sendFeedback(Component.literal("[TrajectoryLens] "
                        + (isProjectiles ? "投掷物轨迹" : "TNT 爆炸预测") + " -> " + (on ? "开" : "关")));
                    return 1;
                }));
        }
        return tree;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> colorsTree(OverlayState state, WalkRangeOverlay ranges) {
        return LiteralArgumentBuilder.<FabricClientCommandSource>literal("colors")
            .executes(ctx -> {
                ctx.getSource().sendFeedback(Component.literal("[TrajectoryLens] " + state.listing()));
                ctx.getSource().sendFeedback(Component.literal("[TrajectoryLens] " + ranges.summary()));
                return 1;
            });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> toggleTree(OverlayState state) {
        return LiteralArgumentBuilder.<FabricClientCommandSource>literal("toggle")
            .executes(ctx -> {
                state.toggle();
                ctx.getSource().sendFeedback(Component.literal("[TrajectoryLens] drops " + (state.visible() ? "on" : "off")));
                return 1;
            });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> statusTree(OverlayState state, WalkRangeOverlay ranges) {
        return LiteralArgumentBuilder.<FabricClientCommandSource>literal("status")
            .executes(ctx -> {
                ctx.getSource().sendFeedback(Component.literal("[TrajectoryLens] " + state.listing()));
                ctx.getSource().sendFeedback(Component.literal("[TrajectoryLens] " + ranges.summary()));
                return 1;
            });
    }

    private static int setColorLocal(FabricClientCommandSource src, OverlayState state, String itemRaw, String colorRaw) {
        if (src.getLevel() == null) {
            src.sendError(Component.literal("[TrajectoryLens] not in a world"));
            return 0;
        }
        String id = itemRaw.trim();
        String ns;
        String path;
        int colon = id.indexOf(':');
        if (colon < 0) {
            ns = "minecraft";
            path = id;
        } else {
            ns = id.substring(0, colon);
            path = id.substring(colon + 1);
        }
        Identifier parsed;
        try {
            parsed = Identifier.fromNamespaceAndPath(ns, path);
        } catch (Exception ex) {
            src.sendError(Component.literal("[TrajectoryLens] invalid item id: " + itemRaw));
            return 0;
        }
        Registry<Item> items = src.getLevel().registryAccess().lookup(Registries.ITEM).orElse(null);
        if (items == null || !items.containsKey(parsed)) {
            src.sendError(Component.literal("[TrajectoryLens] unknown item: " + itemRaw));
            return 0;
        }
        String err = state.setItemColor(parsed.toString(), colorRaw.trim());
        if (err != null) {
            src.sendError(Component.literal("[TrajectoryLens] " + err));
            return 0;
        }
        src.sendFeedback(Component.literal("[TrajectoryLens] color of " + parsed + " -> " + colorRaw.trim()));
        return 1;
    }

    /** Vanilla-style item id Tab completion for the client-side command tree. */
    private static CompletableFuture<Suggestions> suggestItems(
        CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
        if (ctx.getSource().getLevel() == null) {
            return builder.buildFuture();
        }
        Registry<Item> items = ctx.getSource().getLevel().registryAccess().lookup(Registries.ITEM).orElse(null);
        if (items == null) {
            return builder.buildFuture();
        }
        String typed = builder.getRemainingLowerCase();
        Set<String> seen = new HashSet<>();
        for (Identifier key : items.keySet()) {
            if ("minecraft".equals(key.getNamespace())) {
                addSuggestion(builder, seen, key.getPath(), typed);
            }
            addSuggestion(builder, seen, key.toString(), typed);
        }
        return builder.buildFuture();
    }

    /** Entity-type completion ("zombie", "minecraft:villager", ...). */
    private static CompletableFuture<Suggestions> suggestEntityTypes(
        CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
        if (ctx.getSource().getLevel() == null) {
            return builder.buildFuture();
        }
        Registry<EntityType<?>> reg =
            ctx.getSource().getLevel().registryAccess().lookup(Registries.ENTITY_TYPE).orElse(null);
        if (reg == null) {
            return builder.buildFuture();
        }
        String typed = builder.getRemainingLowerCase();
        Set<String> seen = new HashSet<>();
        for (Identifier key : reg.keySet()) {
            if ("minecraft".equals(key.getNamespace())) {
                addSuggestion(builder, seen, key.getPath(), typed);
            }
            addSuggestion(builder, seen, key.toString(), typed);
        }
        return builder.buildFuture();
    }

    private static void addSuggestion(SuggestionsBuilder builder, Set<String> seen, String candidate, String typed) {
        if (seen.add(candidate) && candidate.toLowerCase(Locale.ROOT).startsWith(typed)) {
            builder.suggest(candidate);
        }
    }
}