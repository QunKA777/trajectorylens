package dev.soityy.trajectorylens.client.ui;

import dev.soityy.trajectorylens.client.track.EntityCensus;
import dev.soityy.trajectorylens.client.track.FlowTracker;
import dev.soityy.trajectorylens.client.track.LootTracker;
import dev.soityy.trajectorylens.client.track.OverlayState;
import dev.soityy.trajectorylens.client.track.ProjectileOverlay;
import dev.soityy.trajectorylens.client.track.ThreatOverlay;
import dev.soityy.trajectorylens.client.track.WalkRangeOverlay;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;

/**
 * Tweakeroo-style tabbed settings screen. Pages stay short (no scrolling).
 * Watch lists can be edited here (add/remove item & entity types, incl.
 * quick-pick from nearby entities). Hotkeys bind by direct key capture.
 */
public class ModConfigScreen extends Screen {

    private static final String[] PAGES = {"总览", "掉落物", "投掷物", "爆炸推演", "生物", "物流", "按键", "工具"};

    private final Screen lastScreen;
    private final OverlayState drops;
    private final WalkRangeOverlay ranges;
    private final ProjectileOverlay projectiles;
    private final FlowTracker flow;
    private final EntityCensus census;
    private final ThreatOverlay threats;
    private final LootTracker loot;
    private final int page;
    private final List<LabeledButton> labels = new ArrayList<>();

    // direct key capture: 0 none, 1 range, 2 panel
    private int binding;
    private final Set<Integer> pressed = new LinkedHashSet<>();
    private boolean prevDown;
    private Button bindRangeBtn;
    private Button bindPanelBtn;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 78, 30);

    private record LabeledButton(Button button, Supplier<String> text) {
    }

    public ModConfigScreen(Screen lastScreen, OverlayState drops, WalkRangeOverlay ranges,
                           ProjectileOverlay projectiles, FlowTracker flow, EntityCensus census, ThreatOverlay threats,
                           LootTracker loot, int page) {
        super(Component.literal("TrajectoryLens 设置"));
        this.lastScreen = lastScreen;
        this.drops = drops;
        this.ranges = ranges;
        this.projectiles = projectiles;
        this.flow = flow;
        this.census = census;
        this.threats = threats;
        this.loot = loot;
        this.page = Math.max(0, Math.min(PAGES.length - 1, page));
    }

    @Override
    protected void init() {
        LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(3));
        header.addChild(new StringWidget(this.getTitle(), this.font));
        // two rows of four tabs so labels stay readable
        int perRow = 4;
        int tabWidth = Math.max(56, (this.width - 24 - (perRow - 1) * 3) / perRow);
        for (int row = 0; row < 2; row++) {
            LinearLayout tabs = header.addChild(LinearLayout.horizontal()).spacing(3);
            for (int col = 0; col < perRow; col++) {
                int i = row * perRow + col;
                if (i >= PAGES.length) {
                    break;
                }
                final int pi = i;
                tabs.addChild(Button.builder(
                        Component.literal((i == this.page ? "§e» " : "§7") + PAGES[i]),
                        b -> this.reload(pi))
                    .width(tabWidth).build());
            }
        }

        // two columns on wide windows: pages stay scroll-free without cramping labels
        int columns = this.width >= 560 ? 2 : 1;
        GridLayout grid = new GridLayout();
        grid.defaultCellSetting().padding(1).alignHorizontallyCenter();
        GridLayout.RowHelper helper = grid.createRowHelper(columns);
        int btnWidth = Math.min(330, (this.width - 24 - (columns - 1) * 6) / columns);
        buildPage(helper, btnWidth);

        this.layout.addToContents(grid);
        this.layout.addToFooter(Button.builder(Component.literal("完成"), b -> this.onClose()).width(150).build());
        this.layout.visitWidgets(w -> this.addRenderableWidget(w));
        this.repositionElements();
    }

    private void reload(int newPage) {
        this.minecraft.gui.setScreen(new ModConfigScreen(this.lastScreen, this.drops, this.ranges, this.projectiles, this.flow,
            this.census, this.threats, this.loot, newPage));
    }

    private void buildPage(GridLayout.RowHelper helper, int w) {
        switch (this.page) {
            case 0 -> buildOverviewPage(helper, w);
            case 1 -> buildDropsPage(helper, w);
            case 2 -> buildProjectilePage(helper, w);
            case 3 -> buildBlastPage(helper, w);
            case 4 -> buildMobPage(helper, w);
            case 5 -> buildFlowPage(helper, w);
            case 6 -> buildKeysPage(helper, w);
            default -> buildToolsPage(helper, w);
        }
    }

    // ------------------------------------------------------------------ 总览

    /** Everything a player flips most often, plus a live "what is on right now" readout. */
    private void buildOverviewPage(GridLayout.RowHelper helper, int w) {
        addToggle(helper, w,
            () -> "掉落物轨迹: " + (this.drops.visible() ? "§a开" : "§7关"),
            "预测并绘制附近掉落物的运动轨迹。也可用快捷键 G(原版绑定,可在 工具页 改)。",
            () -> {
                this.drops.toggle();
                act();
            });
        addToggle(helper, w,
            () -> "投掷物轨迹: " + (this.projectiles.projectilesEnabled() ? "§a开" : "§7关"),
            "预测箭/三叉戟/雪球/鸡蛋/末影珍珠/药水/火球的飞行路线与命中目标。",
            () -> {
                this.projectiles.toggleProjectiles();
                act();
            });
        addToggle(helper, w,
            () -> "行走范围盘: " + (this.ranges.isEnabled() ? "§a开" : "§7关"),
            "显示被观察实体类型(见 生物 页)的极限可达范围。开关快捷键见 按键 页。",
            () -> {
                this.ranges.toggle();
                act();
            });
        helper.addChild(Button.builder(Component.literal("§a全部开启"), b -> {
            this.drops.setEnabledState(true);
            this.projectiles.setProjectiles(true);
            this.projectiles.setTnt(true);
            this.projectiles.setAim(true);
            this.projectiles.setChain(true);
            this.projectiles.setFalling(true);
            this.ranges.setEnabledState(true);
            this.threats.setEnabled(true);
            this.loot.setEnabled(true);
            this.loot.setDespawnWarn(true);
            this.flow.setCounters(true);
            this.flow.setJam(true);
            act();
        }).width(w).build());
        helper.addChild(Button.builder(Component.literal("§7全部关闭 (只留面板)"), b -> {
            this.drops.setEnabledState(false);
            this.projectiles.setProjectiles(false);
            this.projectiles.setTnt(false);
            this.projectiles.setAim(false);
            this.projectiles.setChain(false);
            this.projectiles.setFalling(false);
            this.ranges.setEnabledState(false);
            this.threats.setEnabled(false);
            this.loot.setEnabled(false);
            this.loot.setDespawnWarn(false);
            this.flow.setCounters(false);
            this.flow.setJam(false);
            act();
        }).width(w).build());
        helper.addChild(new StringWidget(Component.literal("§7在追: 投掷物 " + this.projectiles.entries().size()
            + " · 计数器 " + this.flow.counters().size() + " · 堵塞 " + this.flow.jams().size()), this.font));
        helper.addChild(new StringWidget(Component.literal("§7快捷键: 轨迹 G · 范围盘 "
            + KeySettings.display(KeySettings.rangeChord) + " · 面板 " + KeySettings.display(KeySettings.panelChord)), this.font));
    }

    // ------------------------------------------------------------- 掉落物

    /** Drop trajectories, the item watch list and everything about losing items. */
    private void buildDropsPage(GridLayout.RowHelper helper, int w) {
        addToggle(helper, w,
            () -> "掉落物轨迹: " + (this.drops.visible() ? "§a开" : "§7关"),
            "预测并绘制附近掉落物的运动轨迹(快捷键 G)。",
            () -> {
                this.drops.toggle();
                act();
            });
        helper.addChild(Button.builder(Component.literal("＋ 搜索添加物品…"), b ->
            this.minecraft.gui.setScreen(new SearchPickerScreen(this.lastScreen, this.drops, this.ranges, this.projectiles, this.flow,
                this.census, this.threats, this.loot, true, this.page))
        ).width(w).build());
        helper.addChild(Button.builder(Component.literal("清空观察 (恢复显示全部物品)"), b -> {
            this.drops.clearTargets();
            act();
        }).width(w).build());
        addWatchedRows(helper, w, watchItemIds(), true);
        addToggle(helper, w,
            () -> "失踪溯源: " + (this.loot.enabled() ? "§a开" : "§7关"),
            "记录附近掉落物为什么消失(玩家拾取/漏斗吸走/岩浆/爆炸/超时/虚空…),并给出幽灵标记。",
            () -> {
                this.loot.toggle();
                act();
            });
        addToggle(helper, w,
            () -> "失踪标记显示: " + this.loot.markerSeconds() + " 秒",
            "消失事件在原地保留多久(点击循环 3/5/6/8/10/15/30/60 秒)。",
            () -> {
                this.loot.cycleMarkerSeconds();
                act();
            });
        addToggle(helper, w,
            () -> "漏斗高亮时长: " + this.loot.glowSeconds() + " 秒",
            "漏斗吸走物品后高亮多久,期间再次吸入会重置计时。",
            () -> {
                this.loot.cycleGlowSeconds();
                act();
            });
        addToggle(helper, w,
            () -> "物品寿命提醒: " + (this.loot.despawnWarnEnabled() ? "§a开" : "§7关"),
            "掉落物在原地待了 4 分半后开始倒计时提醒(原版 5 分钟消失),免得东西白丢。",
            () -> {
                this.loot.toggleDespawnWarn();
                act();
            });
        helper.addChild(new StringWidget(Component.literal("§7颜色: /trajectorylens color <物品id> <RRGGBB>"), this.font));
    }

    /** Inline "current watch list" rows shared by the drops and mob pages. */
    private void addWatchedRows(GridLayout.RowHelper helper, int w, List<String> watched, boolean forItems) {
        if (watched.isEmpty()) {
            helper.addChild(new StringWidget(Component.literal("§7当前观察: 全部 (点上方搜索即可限定)"), this.font));
            return;
        }
        int shown = Math.min(3, watched.size());
        for (int i = 0; i < shown; i++) {
            final String id = watched.get(i);
            helper.addChild(Button.builder(Component.literal("✕ " + shortId(id)), b -> {
                if (forItems) {
                    Item it = resolveItem(id);
                    if (it != null) {
                        this.drops.removeTarget(it);
                    }
                } else {
                    this.ranges.removeType(id);
                }
                act();
            }).width(w).build());
        }
        if (watched.size() > 3) {
            helper.addChild(new StringWidget(Component.literal("§7…等 " + watched.size() + " 种(移除后显示其余)"), this.font));
        }
    }

    // --------------------------------------------------------------- 生物

    /** Mobs: threat readout plus the walk-range prediction and its watch list. */
    private void buildMobPage(GridLayout.RowHelper helper, int w) {
        addToggle(helper, w,
            () -> "威胁指示: " + (this.threats.enabled() ? "§a开" : "§7关"),
            "给附近的敌对生物标出 锁定/警戒/未察觉 状态与连线(客户端推断)。",
            () -> {
                this.threats.toggle();
                act();
            });
        addToggle(helper, w,
            () -> "行走范围盘: " + (this.ranges.isEnabled() ? "§a开" : "§7关"),
            "显示被观察实体类型的极限可达范围,快捷键见 按键 页。",
            () -> {
                this.ranges.toggle();
                act();
            });
        addToggle(helper, w,
            () -> "预测时长: " + this.ranges.horizonSeconds() + " 秒",
            "范围盘预测未来多少秒的可达范围。越长越远、重算越贵。",
            () -> {
                int cur = this.ranges.horizonSeconds();
                int next = switch (cur) {
                    case 5 -> 10;
                    case 10 -> 15;
                    case 15 -> 20;
                    case 20 -> 30;
                    case 30 -> 60;
                    default -> 5;
                };
                this.ranges.setHorizon(next);
                act();
            });
        helper.addChild(Button.builder(Component.literal("＋ 搜索添加生物类型…"), b ->
            this.minecraft.gui.setScreen(new SearchPickerScreen(this.lastScreen, this.drops, this.ranges, this.projectiles, this.flow,
                this.census, this.threats, this.loot, false, this.page))
        ).width(w).build());
        helper.addChild(Button.builder(Component.literal("清空观察 (恢复显示全部类型)"), b -> {
            this.ranges.clearTypes();
            act();
        }).width(w).build());
        addWatchedRows(helper, w, this.ranges.typeIdsSnapshot(), false);
        helper.addChild(new StringWidget(Component.literal("§7颜色: range color <类型> <RRGGBB>"), this.font));
    }


    /** Projectiles in flight, the held-item aim preview and the blast warning. */
    private void buildProjectilePage(GridLayout.RowHelper helper, int w) {
        addToggle(helper, w,
            () -> "投掷物轨迹: " + (this.projectiles.projectilesEnabled() ? "§a开" : "§7关"),
            "预测箭/三叉戟/雪球/鸡蛋/末影珍珠/药水/火球的飞行路线与命中目标。",
            () -> {
                this.projectiles.toggleProjectiles();
                act();
            });
        addToggle(helper, w,
            () -> "手持瞄准预测: " + (this.projectiles.aimEnabled() ? "§a开" : "§7关"),
            "手持投掷类物品时实时显示瞄准落点;" + System.lineSeparator() + "末影珍珠还会标注传送到哪、落点是否危险。",
            () -> {
                this.projectiles.toggleAim();
                act();
            });
        addToggle(helper, w,
            () -> "爆炸预警: " + (this.projectiles.tntEnabled() ? "§a开" : "§7关"),
            "TNT/苦力怕引信倒计时 + 爆炸球体半径标注,支持被点燃的TNT矿车。",
            () -> {
                this.projectiles.toggleTnt();
                act();
            });
        helper.addChild(new StringWidget(Component.literal("§7箭/雪球落点按 26.2 参数逐 tick 复算,命中点标名字"), this.font));
        helper.addChild(new StringWidget(Component.literal("§7末影珍珠额外标传送落点、自伤与危险判定"), this.font));
    }

    /** What an explosion does next: chained blasts, launched entities, falling blocks. */
    private void buildBlastPage(GridLayout.RowHelper helper, int w) {
        addToggle(helper, w,
            () -> "因果链推演: " + (this.projectiles.chainEnabled() ? "§a开" : "§7关"),
            "从预计爆炸点继续推演: 连锁引爆(含 TNT 矿车)、被推物品与生物的落地结论、方块后果。",
            () -> {
                this.projectiles.toggleChain();
                act();
            });
        addToggle(helper, w,
            () -> "推演层数: " + this.projectiles.chainDepth() + " 层",
            "爆炸后继续追几层连锁(点击循环 1/2/3/4)。层数越多开销越大。",
            () -> {
                this.projectiles.cycleChainDepth();
                act();
            });
        addToggle(helper, w,
            () -> "击退推演时长: " + this.projectiles.chainHorizonSeconds() + " 秒",
            "被炸飞的物品/生物各推演多久的飞行(点击循环 3/6/9/12 秒)。",
            () -> {
                this.projectiles.cycleChainHorizon();
                act();
            });
        addToggle(helper, w,
            () -> "下落方块落点: " + (this.projectiles.fallingEnabled() ? "§a开" : "§7关"),
            "沙子/沙砾/铁砧/钟乳石等下落方块显示落点;正下方有人时标红提醒。",
            () -> {
                this.projectiles.toggleFalling();
                act();
            });
        helper.addChild(new StringWidget(Component.literal("§7箭头=连锁爆炸点,弧线=被推的东西,末端写落点"), this.font));
    }

    /** Logistics: entity census, chokepoint counters with trend, hopper jams. */
    private void buildFlowPage(GridLayout.RowHelper helper, int w) {
        helper.addChild(new StringWidget(Component.literal("§e" + this.census.compact()), this.font));
        helper.addChild(new StringWidget(Component.literal("§6" + this.loot.summary()), this.font));
        addToggle(helper, w,
            () -> "漏斗堵塞检测: " + (this.flow.jamEnabled() ? "§a开" : "§7关"),
            "物品停在漏斗上不动超过阈值就标出来: 漏斗堵了(红框)还是被红石关掉了(蓝框)。",
            () -> {
                this.flow.toggleJam();
                act();
            });
        addToggle(helper, w,
            () -> "堵塞判定时长: " + this.flow.jamSeconds() + " 秒",
            "物品在漏斗上静止多久算堵塞(点击循环 3/5/6/8/10/15/30 秒)。",
            () -> {
                this.flow.cycleJamSeconds();
                act();
            });
        addToggle(helper, w,
            () -> "卡口计数器: " + (this.flow.countersEnabled() ? "§a开" : "§7关"),
            "显示虚拟计数面:物品穿越该面时计数,给出 每分钟速率 与 上游堆积数。适合统计漏斗/水道产能。",
            () -> {
                this.flow.toggleCounters();
                act();
            });
        helper.addChild(Button.builder(Component.literal("＋ 在准星处添加计数器"), b -> {
            String name = "c" + (this.flow.counters().size() + 1);
            String err = this.flow.addCounter(name);
            var pl = this.minecraft.player;
            if (pl != null) {
                pl.sendSystemMessage(Component.literal(err != null
                    ? "[TrajectoryLens] " + err
                    : "[TrajectoryLens] 已添加计数器 " + name + " (想改名用 /trajectorylens counter add <名字>)"));
            }
            act();
        }).width(w).build());

        int shown = 0;
        for (var c : this.flow.counters().values()) {
            if (shown++ >= 2) {
                helper.addChild(new StringWidget(Component.literal("§7…等 " + this.flow.counters().size() + " 个(counter list 看全部)"), this.font));
                break;
            }
            final String name = c.name;
            String dir = switch (c.trendDir()) {
                case 1 -> "§a↑";
                case -1 -> "§c↓";
                default -> "§7→";
            };
            Button row = Button.builder(Component.literal(String.format("✕ §f%s§7: §e%d/min §b%s %s§7堆积%.0f",
                    name, c.perMinute(), c.spark(), dir, c.backlog)), b -> {
                this.flow.removeCounter(name);
                act();
            }).width(w).build();
            row.setTooltip(Tooltip.create(Component.literal("点一下移除该计数器。\n色带=最近 3 分钟每分钟吞吐(每 15 秒采样),↑↓=趋势。")));
            helper.addChild(row);
        }
        if (this.flow.counters().isEmpty()) {
            helper.addChild(new StringWidget(Component.literal("§7没有计数器(上方按钮或 counter add <名字>)"), this.font));
        }
        helper.addChild(new StringWidget(Component.literal("§7红框=漏斗堵了 · 蓝框=被红石关掉 · 全量导出用 export"), this.font));
    }

    /** Tools: dump state to chat, export a report file, vanilla key settings, cheatsheet. */
    private void buildToolsPage(GridLayout.RowHelper helper, int w) {
        Button listBtn = Button.builder(Component.literal("查看 颜色/观察列表/开关(聊天输出)"), b -> {
            var pl = this.minecraft.player;
            if (pl != null) {
                pl.sendSystemMessage(Component.literal("[TrajectoryLens] " + this.drops.listing()));
                pl.sendSystemMessage(Component.literal("[TrajectoryLens] " + this.ranges.summary()));
                pl.sendSystemMessage(Component.literal("[TrajectoryLens] " + this.projectiles.summary()));
                pl.sendSystemMessage(Component.literal("[TrajectoryLens] " + this.flow.summary()));
            }
        }).width(w).build();
        listBtn.setTooltip(Tooltip.create(Component.literal("把当前颜色、观察列表与所有开关状态输出到聊天栏。")));
        helper.addChild(listBtn);

        Button exportBtn = Button.builder(Component.literal("导出报告到 config/"), b -> {
            var f = Report.write(Report.lines(this.drops, this.ranges, this.projectiles, this.flow,
                this.census, this.threats, this.loot));
            var pl = this.minecraft.player;
            if (pl != null) {
                pl.sendSystemMessage(Component.literal(f != null
                    ? "[TrajectoryLens] 报告已写入 " + f.getName()
                    : "[TrajectoryLens] 报告写入失败"));
            }
        }).width(w).build();
        exportBtn.setTooltip(Tooltip.create(Component.literal("把实体普查、计数器(含趋势)、堵塞、失踪溯源写成 txt,方便对比不同时间的产量。")));
        helper.addChild(exportBtn);

        Button keysBtn = Button.builder(Component.literal("打开 原版按键设置"), b ->
            this.minecraft.gui.setScreen(new net.minecraft.client.gui.screens.options.controls.ControlsScreen(this, this.minecraft.options))
        ).width(w).build();
        keysBtn.setTooltip(Tooltip.create(Component.literal("原版控制页;掉落物轨迹的 G 键在此搜索 TrajectoryLens 修改。")));
        helper.addChild(keysBtn);

        Button reportBtn = Button.builder(Component.literal("查看失踪记录 (/trajectorylens lost)"), b -> {
            var pl = this.minecraft.player;
            if (pl != null) {
                for (String line : this.loot.report()) {
                    pl.sendSystemMessage(Component.literal(line));
                }
            }
        }).width(w).build();
        reportBtn.setTooltip(Tooltip.create(Component.literal("最近消失的物品、原因与位置。")));
        helper.addChild(reportBtn);

        helper.addChild(new StringWidget(Component.literal("§7物流: census · flowcount · jam · export"), this.font));
        helper.addChild(new StringWidget(Component.literal("§7物品: toggle · target <id> · color <id> <色>"), this.font));
        helper.addChild(new StringWidget(Component.literal("§7战斗: projectiles · tnt · aim · chain · falling"), this.font));
        helper.addChild(new StringWidget(Component.literal("§7显示: range add <类型> · losttime · glowtime · gui"), this.font));
    }

    private void buildKeysPage(GridLayout.RowHelper helper, int w) {
        bindRangeBtn = bindRow(helper, w, "行走范围开关",
            "点击后直接按新键(可多键组合,最多3个),松开即绑定。Esc 取消,退格清除。", 1);
        bindPanelBtn = bindRow(helper, w, "打开控制面板",
            "绑定打开本面板的组合键,规则同上。", 2);
        helper.addChild(new StringWidget(Component.literal("§7玩法: 点击行 → 按住新组合键 → 松手完成"), this.font));
    }

    private void addToggle(GridLayout.RowHelper helper, int w, Supplier<String> text, String tooltip, Runnable action) {
        Button b = Button.builder(Component.literal(text.get()), btn -> {
            action.run();
            repaint();
        }).width(w).build();
        b.setTooltip(Tooltip.create(Component.literal(tooltip)));
        helper.addChild(b);
        this.labels.add(new LabeledButton(b, text));
    }

    private Button bindRow(GridLayout.RowHelper helper, int w, String name, String tooltip, int target) {
        Button b = Button.builder(Component.literal("占位"), btn -> {
            if (this.binding == target) {
                this.binding = 0;
                this.pressed.clear();
            } else {
                this.binding = target;
                this.pressed.clear();
            }
            repaint();
        }).width(w).build();
        b.setTooltip(Tooltip.create(Component.literal(tooltip)));
        helper.addChild(b);
        if (target == 1) {
            this.bindRangeBtn = b;
        } else {
            this.bindPanelBtn = b;
        }
        repaint();
        return b;
    }

    /** Call after any change that should rebuild the current page (list changes). */
    private void act() {
        save();
        this.reload(this.page);
    }

    private void save() {
        SettingsIO.snapshot(this.drops, this.ranges, this.projectiles, this.flow, this.threats, this.loot).save();
    }

    /** Refresh labels of toggle/cycle/bind rows without rebuilding. */
    private void repaint() {
        for (LabeledButton lb : this.labels) {
            lb.button().setMessage(Component.literal(lb.text().get()));
        }
        if (this.bindRangeBtn != null) {
            String t = this.binding == 1
                ? "§e绑定中: " + (this.pressed.isEmpty() ? "按新键…" : KeySettings.display(KeySettings.chordFrom(List.copyOf(this.pressed))))
                : "行走范围开关: [ " + KeySettings.display(KeySettings.rangeChord) + " ]  点击绑定";
            this.bindRangeBtn.setMessage(Component.literal(t));
        }
        if (this.bindPanelBtn != null) {
            String t = this.binding == 2
                ? "§e绑定中: " + (this.pressed.isEmpty() ? "按新键…" : KeySettings.display(KeySettings.chordFrom(List.copyOf(this.pressed))))
                : "打开控制面板: [ " + KeySettings.display(KeySettings.panelChord) + " ]  点击绑定";
            this.bindPanelBtn.setMessage(Component.literal(t));
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.binding != 0) {
            int code = event.key();
            if (code == InputConstants.KEY_ESCAPE) {
                this.binding = 0;
                this.pressed.clear();
                repaint();
                return true;
            }
            if (code == InputConstants.KEY_BACKSPACE) {
                setChord(new int[0]);
                this.binding = 0;
                this.pressed.clear();
                repaint();
                save();
                return true;
            }
            this.pressed.add(code);
            repaint();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void tick() {
        if (this.binding != 0 && !this.pressed.isEmpty()) {
            var win = this.minecraft.getWindow();
            boolean anyDown = false;
            for (int k : this.pressed) {
                if (InputConstants.isKeyDown(win, k)) {
                    anyDown = true;
                    break;
                }
            }
            if (!anyDown && this.prevDown) {
                setChord(KeySettings.chordFrom(List.copyOf(this.pressed)));
                this.binding = 0;
                this.pressed.clear();
                repaint();
                save();
            }
            this.prevDown = anyDown;
        } else {
            this.prevDown = false;
        }
        if (this.binding != 0) {
            repaint();
        }
        super.tick();
    }

    private void setChord(int[] chord) {
        if (this.binding == 1) {
            KeySettings.rangeChord = chord;
        } else if (this.binding == 2) {
            KeySettings.panelChord = chord;
        }
    }

    // ---------- data helpers ----------

    private List<String> watchItemIds() {
        List<String> out = new ArrayList<>();
        for (Item it : this.drops.watchItems()) {
            String id = OverlayState.itemIdOf(it.getDefaultInstance());
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }

    private Item resolveItem(String canonicalId) {
        var level = this.minecraft.level;
        if (level == null) {
            return null;
        }
        net.minecraft.core.Registry<Item> reg =
            level.registryAccess().lookup(net.minecraft.core.registries.Registries.ITEM).orElse(null);
        if (reg == null) {
            return null;
        }
        try {
            var id = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                canonicalId.contains(":") ? canonicalId.substring(0, canonicalId.indexOf(':')) : "minecraft",
                canonicalId.contains(":") ? canonicalId.substring(canonicalId.indexOf(':') + 1) : canonicalId);
            return reg.getValue(id);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> nearbyTypes(boolean forItems) {
        List<String> out = new ArrayList<>();
        var level = this.minecraft.level;
        var player = this.minecraft.player;
        if (level == null || player == null) {
            return out;
        }
        Set<String> have = new LinkedHashSet<>();
        if (forItems) {
            for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(48.0))) {
                if (!e.isAlive() || e.getItem().isEmpty()) {
                    continue;
                }
                String id = OverlayState.itemIdOf(e.getItem());
                if (id == null || have.contains(id) || watchItemIds().contains(id)) {
                    continue;
                }
                have.add(id);
                out.add(id);
                if (out.size() >= 8) {
                    break;
                }
            }
        } else {
            for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48.0))) {
                if (!m.isAlive()) {
                    continue;
                }
                String id = WalkRangeOverlay.typeIdOf(m);
                if (id == null || have.contains(id) || this.ranges.typeIdsSnapshot().contains(id)) {
                    continue;
                }
                have.add(id);
                out.add(id);
                if (out.size() >= 8) {
                    break;
                }
            }
        }
        return out;
    }

    private static String shortId(String id) {
        int i = id.indexOf(':');
        return i >= 0 ? id.substring(i + 1) : id;
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
    }

    @Override
    public void onClose() {
        save();
        this.minecraft.gui.setScreen(this.lastScreen);
    }
}