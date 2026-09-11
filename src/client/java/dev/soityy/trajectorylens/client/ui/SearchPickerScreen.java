package dev.soityy.trajectorylens.client.ui;

import dev.soityy.trajectorylens.client.Lang;

import dev.soityy.trajectorylens.client.track.EntityCensus;
import dev.soityy.trajectorylens.client.track.FlowTracker;
import dev.soityy.trajectorylens.client.track.LootTracker;
import dev.soityy.trajectorylens.client.track.OverlayState;
import dev.soityy.trajectorylens.client.track.ProjectileOverlay;
import dev.soityy.trajectorylens.client.track.ThreatOverlay;
import dev.soityy.trajectorylens.client.track.WalkRangeOverlay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Search-and-confirm picker with command-tab style feel:
 * live filtered results (prefix matches first), arrow up/down to move the
 * highlight, Enter to confirm, click works too. Typing goes through the
 * container-focus charTyped chain (26.2 input model).
 */
public class SearchPickerScreen extends Screen {

    private static final int VISIBLE = 6;
    private static final int KEY_UP = 265;    // GLFW
    private static final int KEY_DOWN = 264;

    private final Screen rootLast;
    private final OverlayState drops;
    private final WalkRangeOverlay ranges;
    private final ProjectileOverlay projectiles;
    private final FlowTracker flow;
    private final EntityCensus census;
    private final ThreatOverlay threats;
    private final LootTracker loot;
    private final boolean forItems;
    private final int returnPage;
    private final List<String> allIds = new ArrayList<>();
    private final List<String> matches = new ArrayList<>();
    private final List<Button> slots = new ArrayList<>();
    private int selection;
    private EditBox searchBox;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 40, 26);

    public SearchPickerScreen(Screen rootLast, OverlayState drops, WalkRangeOverlay ranges,
                              ProjectileOverlay projectiles, FlowTracker flow, EntityCensus census, ThreatOverlay threats,
                              LootTracker loot, boolean forItems, int returnPage) {
        super(Component.literal(forItems ? Lang.tr("选择要模拟的掉落物") : Lang.tr("选择要模拟的生物类型")));
        this.rootLast = rootLast;
        this.drops = drops;
        this.ranges = ranges;
        this.projectiles = projectiles;
        this.flow = flow;
        this.census = census;
        this.threats = threats;
        this.loot = loot;
        this.forItems = forItems;
        this.returnPage = returnPage;
    }

    @Override
    protected void init() {
        if (this.allIds.isEmpty()) {
            var level = this.minecraft.level;
            if (level != null) {
                Registry<?> reg = forItems
                    ? level.registryAccess().lookup(Registries.ITEM).orElse(null)
                    : level.registryAccess().lookup(Registries.ENTITY_TYPE).orElse(null);
                if (reg != null) {
                    for (Identifier id : reg.keySet()) {
                        this.allIds.add(id.toString());
                    }
                    this.allIds.sort(Comparator.naturalOrder());
                }
            }
        }

        LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(2));
        header.addChild(new StringWidget(this.getTitle(), this.font));

        GridLayout grid = new GridLayout();
        grid.defaultCellSetting().padding(1).alignHorizontallyCenter();
        GridLayout.RowHelper helper = grid.createRowHelper(1);
        int w = Math.min(330, this.width - 24);

        this.searchBox = new EditBox(this.font, w, 18, Component.literal(Lang.tr("搜索")));
        this.searchBox.setHint(Component.literal(Lang.tr("输入关键字(↑↓选择, Enter 确认, Esc 返回)")));
        this.searchBox.setMaxLength(64);
        this.searchBox.setResponder(s -> {
            this.selection = 0;
            refreshMatches();
            repaintSlots();
        });
        helper.addChild(this.searchBox);

        for (int i = 0; i < VISIBLE; i++) {
            final int slot = i;
            Button b = Button.builder(Component.literal(" "), btn -> confirm(slot)).width(w).build();
            b.active = false;
            helper.addChild(b);
            this.slots.add(b);
        }
        this.layout.addToContents(grid);
        this.layout.addToFooter(Button.builder(Component.literal(Lang.tr("返回")), b -> back()).width(100).build());
        this.layout.visitWidgets(x -> this.addRenderableWidget(x));
        this.repositionElements();

        // container focus must point at the box or charTyped never reaches it
        this.setInitialFocus(this.searchBox);
        this.searchBox.setFocused(true);
        this.searchBox.setCanLoseFocus(false);
        refreshMatches();
        repaintSlots();
    }

    private void refreshMatches() {
        this.matches.clear();
        String q = this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return;
        }
        List<String> prefix = new ArrayList<>();
        List<String> contains = new ArrayList<>();
        for (String id : this.allIds) {
            String shortId = id.indexOf(':') >= 0 ? id.substring(id.indexOf(':') + 1) : id;
            if (id.startsWith(q) || shortId.startsWith(q)) {
                prefix.add(id);
            } else if (id.contains(q) || shortId.contains(q)) {
                contains.add(id);
            }
        }
        this.matches.addAll(prefix);
        this.matches.addAll(contains);
    }

    private void repaintSlots() {
        if (this.matches.isEmpty()) {
            for (Button b : this.slots) {
                b.setMessage(Component.literal(Lang.tr("§7无匹配(继续输入或改关键字)")));
                b.active = false;
            }
            this.slots.get(0).setMessage(Component.literal(Lang.tr("§7无匹配 — 试试更短的关键字")));
            return;
        }
        int start = Math.max(0, Math.min(this.selection, this.matches.size() - VISIBLE));
        for (int i = 0; i < VISIBLE; i++) {
            Button b = this.slots.get(i);
            int idx = start + i;
            if (idx < this.matches.size()) {
                String id = this.matches.get(idx);
                String label = id.indexOf(':') >= 0 && id.startsWith("minecraft:")
                    ? id.substring("minecraft:".length()) : id;
                boolean sel = idx == this.selection;
                b.setMessage(Component.literal((sel ? "§e▶ " : "§7  ") + label + (sel ? "  §7←Enter" : "")));
                b.active = true;
            } else {
                b.setMessage(Component.literal(" "));
                b.active = false;
            }
        }
    }

    private void confirm(int slot) {
        int start = Math.max(0, Math.min(this.selection, this.matches.size() - VISIBLE));
        int idx = start + slot;
        if (idx >= 0 && idx < this.matches.size()) {
            pick(idx);
        }
    }

    private void pick(int idx) {
        String id = this.matches.get(idx);
        String err = this.forItems ? this.drops.setTargetFromId(id) : this.ranges.addTypeFromId(id);
        var pl = this.minecraft.player;
        if (pl != null) {
            pl.sendSystemMessage(Component.literal(err != null
                ? "[TrajectoryLens] " + err
                : Lang.tr("[TrajectoryLens] 已添加: ") + id));
        }
        if (err == null) {
            SettingsIO.snapshot(this.drops, this.ranges, this.projectiles, this.flow, this.threats, this.loot).save();
            back();
        }
    }

    private void back() {
        this.minecraft.gui.setScreen(new ModConfigScreen(this.rootLast, this.drops, this.ranges, this.projectiles, this.flow,
            this.census, this.threats, this.loot, this.returnPage));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int code = event.key();
        if (code == KEY_DOWN && !this.matches.isEmpty()) {
            this.selection = Math.min(this.matches.size() - 1, this.selection + 1);
            repaintSlots();
            return true;
        }
        if (code == KEY_UP && !this.matches.isEmpty()) {
            this.selection = Math.max(0, this.selection - 1);
            repaintSlots();
            return true;
        }
        if ((code == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || code == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER)
            && !this.matches.isEmpty()) {
            this.pick(this.selection);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (this.searchBox != null) {
            return this.searchBox.charTyped(event) || super.charTyped(event);
        }
        return super.charTyped(event);
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
    }
}
