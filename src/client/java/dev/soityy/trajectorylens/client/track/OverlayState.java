package dev.soityy.trajectorylens.client.track;

import dev.soityy.trajectorylens.client.ui.SettingsIO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;
import dev.soityy.trajectorylens.physics.ItemPhysicsSimulator;
import dev.soityy.trajectorylens.physics.TrajectoryPath;
import dev.soityy.trajectorylens.util.PathTools;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Per-item prediction cache + key/color state. Runs on the client tick.
 * Multiple different item types are tracked at the same time; every item type
 * has its own trajectory color (explicitly set by command, or auto-assigned
 * from a distinct palette).
 */
public final class OverlayState {

    /** Total simultaneous trajectory lines. Shared fairly across item types present. */
    public static final int MAX_TRACKED = 24;

    /** Auto-assign palette (ARGB, strong alpha). Deep purple first = classic look. */
    public static final int[] PALETTE = {
        0xEA6A0DAD, 0xEAE63946, 0xEA2A9D8F, 0xEA457B9D, 0xEAE9C46A,
        0xEAF4A261, 0xEA9B5DE5, 0xEA00BBF9, 0xEA00F5D4, 0xEAF15BB5,
        0xEA90BE6D, 0xEA577590, 0xEAEF476F, 0xEA80ED99
    };

    private final Map<Integer, TrackedItem> tracked = new HashMap<>();
    private final Map<String, Integer> itemColors = new LinkedHashMap<>(); // explicit colors (ARGB)
    private final Map<String, Integer> autoColors = new LinkedHashMap<>(); // auto-assigned (ARGB)
    private final KeyMapping showKey = new KeyMapping(
        "key.trajectorylens.show",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_G,
        KeyMapping.Category.MISC
    );

    private int tick;
    private boolean enabled;   // G toggles tracking display on/off
    /** Item types explicitly watched; empty list = track every type. Repeated /target adds. */
    private final List<Item> watchItems = new ArrayList<>();
    private String targetName = "all items";

    public static final class TrackedItem {
        public ItemEntity entity;
        public ItemPhysicsSimulator sim;
        public TrajectoryPath path;
        public Vec3 lastSeedPos = Vec3.ZERO;
        public Vec3 lastSeedVel = Vec3.ZERO;
        public int refreshCounter;
        public int idleTicks; // real entity at rest: drop after a few seconds
        public int color = PALETTE[0];
    }

    public void register() {
        KeyMappingHelper.registerKeyMapping(this.showKey);
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> {
            this.tracked.clear();
        });
    }

    /** Whether the trajectory overlay is currently enabled (G toggles). */
    public boolean visible() {
        return this.enabled;
    }

    public void toggle() {
        this.enabled = !this.enabled;
        if (!this.enabled) {
            this.tracked.clear();
        }
    }

    /** Add one item type to the watch list (repeated calls accumulate; never resets others). */
    public void addTarget(Item item, @org.jspecify.annotations.Nullable String name) {
        if (!this.watchItems.contains(item)) {
            this.watchItems.add(item);
        }
        this.rebuildTargetName(name == null ? "item" : name);
        this.tracked.clear();
    }

    /** Remove one item type from the watch list (list empty = track everything). */
    public void removeTarget(Item item) {
        if (item == null) {
            return;
        }
        this.watchItems.remove(item);
        if (this.watchItems.isEmpty()) {
            this.targetName = "all items";
        } else {
            this.rebuildTargetName("");
        }
        this.tracked.clear();
    }

    /** Current watch items (empty list = all). */
    public java.util.List<Item> watchItems() {
        return new java.util.ArrayList<>(this.watchItems);
    }

    /** Empty the watch list -> track every item type again. */
    public void clearTargets() {
        this.watchItems.clear();
        this.targetName = "all items";
        this.tracked.clear();
    }

    public boolean isWatching(@org.jspecify.annotations.Nullable Item item) {
        return this.watchItems.isEmpty() || (item != null && this.watchItems.contains(item));
    }

    private void rebuildTargetName(String lastAdded) {
        if (this.watchItems.size() == 1) {
            this.targetName = lastAdded;
        } else {
            StringBuilder sb = new StringBuilder();
            for (Item it : this.watchItems) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                String id = itemIdOf(it.getDefaultInstance());
                sb.append(id == null ? "?" : id);
            }
            this.targetName = sb.toString();
        }
    }

    public String targetName() {
        return this.targetName;
    }

    /** Canonical registry id ("ns:path") of the item inside the stack, or null. */
    public static @org.jspecify.annotations.Nullable String itemIdOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        Registry<Item> reg = level.registryAccess().lookup(Registries.ITEM).orElse(null);
        if (reg == null) {
            return null;
        }
        Identifier key = reg.getKey(stack.getItem());
        return key == null ? null : key.toString();
    }

    /** Trajectory color (ARGB) for an item id; auto-assigns a palette color on first use. */
    public int colorForId(@org.jspecify.annotations.Nullable String id) {
        if (id == null) {
            return PALETTE[0];
        }
        Integer c = this.itemColors.get(id);
        if (c != null) {
            return c;
        }
        Integer a = this.autoColors.get(id);
        if (a != null) {
            return a;
        }
        int col = PALETTE[this.autoColors.size() % PALETTE.length];
        this.autoColors.put(id, col);
        return col;
    }

    /**
     * Sets the color of an item id (canonical "ns:path").
     * colorSpec: "RRGGBB" | "AARRGGBB" | "auto" (auto = default palette assignment).
     * Returns an error message or null on success.
     */
    public @org.jspecify.annotations.Nullable String setItemColor(String id, String colorSpec) {
        if (id == null) {
            return "null item";
        }
        if (colorSpec == null || colorSpec.trim().equalsIgnoreCase("auto")) {
            this.itemColors.remove(id);
            this.autoColors.remove(id);
        } else {
            Integer argb = PathTools.parseColor(colorSpec);
            if (argb == null) {
                return "bad color '" + colorSpec + "' — use RRGGBB or AARRGGBB, or 'auto'";
            }
            this.itemColors.put(id, argb);
        }
        // push new color into currently displayed tracks
        for (TrackedItem t : this.tracked.values()) {
            if (t.entity != null && id.equals(itemIdOf(t.entity.getItem()))) {
                t.color = this.colorForId(id);
            }
        }
        return null;
    }

    /** Human-readable overview: on/off, target, and the color table. */
    public String listing() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.enabled ? "on" : "off").append(" | target=").append(this.targetName);
        LinkedHashSet<String> ids = new LinkedHashSet<>(this.itemColors.keySet());
        ids.addAll(this.autoColors.keySet());
        if (ids.isEmpty()) {
            sb.append(" | colors: none yet (auto-assign on first drop)");
            return sb.toString();
        }
        sb.append(" | colors:");
        for (String id : ids) {
            Integer c = this.itemColors.get(id);
            boolean auto = c == null;
            int argb = auto ? this.autoColors.get(id) : c;
            sb.append(" ").append(id).append("=#")
                .append(String.format("%06X", argb & 0xFFFFFF))
                .append(auto ? "(auto)" : "");
        }
        return sb.toString();
    }

    /**
     * Adds an item type ("diamond" or "minecraft:diamond") to the watch list.
     * Returns an error message, or null on success.
     */
    public @org.jspecify.annotations.Nullable String setTargetFromId(String raw) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return "not in a world";
        }
        String id = raw.trim();
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
            return "invalid item id: " + id;
        }
        Registry<Item> items = level.registryAccess().lookup(Registries.ITEM).orElse(null);
        if (items == null || !items.containsKey(parsed)) {
            return "unknown item: " + id;
        }
        this.addTarget(items.getValue(parsed), parsed.toString());
        return null;
    }

    public Map<Integer, TrackedItem> tracked() {
        return this.tracked;
    }

    private java.util.List<String> pendingWatch;

    /** Set enabled state directly (config restore); false also clears tracks. */
    public void setEnabledState(boolean on) {
        this.enabled = on;
        if (!on) {
            this.tracked.clear();
        }
    }

    /** Queue watch ids; applied on the next tick once a level is available. */
    public void queueWatchIds(java.util.List<String> ids) {
        this.pendingWatch = ids == null ? null : new java.util.ArrayList<>(ids);
    }

    /** Canonical ids of the current item watch list. */
    public java.util.List<String> watchItemIdsSnapshot() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (Item it : this.watchItems) {
            String id = itemIdOf(it.getDefaultInstance());
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }

    /** Explicit item colors as canonical id -> RRGGBB/AARRGGBB (autos are regenerated). */
    public java.util.Map<String, String> colorMapSnapshot() {
        java.util.Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : this.itemColors.entrySet()) {
            out.put(e.getKey(), SettingsIO.specOf(e.getValue()));
        }
        return out;
    }

    private void onTick(Minecraft mc) {
        this.tick++;
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            if (!this.tracked.isEmpty()) {
                this.tracked.clear();
            }
            return;
        }
        if (level.dimension() == null) {
            return;
        }

        if (this.pendingWatch != null) {
            java.util.List<String> queued = this.pendingWatch;
            this.pendingWatch = null;
            this.clearTargets();
            for (String s : queued) {
                this.setTargetFromId(s); // errors ignored on restore
            }
        }

        // prune dead / disappeared entities and items resting for a while
        Iterator<Map.Entry<Integer, TrackedItem>> it = this.tracked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, TrackedItem> e = it.next();
            ItemEntity entity = (ItemEntity) level.getEntity(e.getKey());
            if (entity == null || !entity.isAlive() || entity.getItem().isEmpty()) {
                it.remove();
                continue;
            }
            Vec3 v = entity.getDeltaMovement();
            boolean resting = entity.onGround() && v.lengthSqr() < 1.0E-5;
            e.getValue().idleTicks = resting ? e.getValue().idleTicks + 1 : 0;
            if (e.getValue().idleTicks > 60) { // ~3 s after coming to rest
                it.remove();
            }
        }

        if (this.showKey.consumeClick()) {
            this.toggle();
        }
        if (!this.enabled) {
            return;
        }

        // Collect moving drops, then hand out trajectory slots FAIRLY per item type:
        // when N different item types are in range, every type gets up to MAX/N lines,
        // so a flood of one type can never starve the others.
        Map<String, List<ItemEntity>> byType = new LinkedHashMap<>();
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, mc.player.getBoundingBox().inflate(32.0))) {
            if (!entity.isAlive() || entity.getItem().isEmpty()) {
                continue;
            }
            if (!this.isWatching(entity.getItem().getItem())) {
                continue;
            }
            Vec3 vel = entity.getDeltaMovement();
            boolean fastEnough = vel.lengthSqr() > 2.5E-5;
            if (!fastEnough) {
                continue; // only moving drops are interesting
            }
            String typeId = itemIdOf(entity.getItem());
            byType.computeIfAbsent(typeId == null ? "?" : typeId, k -> new ArrayList<>()).add(entity);
        }
        int typeCount = byType.size();
        int perType = typeCount <= 1 ? MAX_TRACKED : Math.max(1, MAX_TRACKED / typeCount);
        int chosenCount = 0;
        outer:
        for (List<ItemEntity> group : byType.values()) {
            int taken = 0;
            for (ItemEntity entity : group) {
                if (chosenCount >= MAX_TRACKED) {
                    break outer;
                }
                if (taken >= perType) {
                    break;
                }
                taken++;
                chosenCount++;
                this.refreshTracked(level, entity);
            }
        }
    }

    private void refreshTracked(ClientLevel level, ItemEntity entity) {
        Vec3 vel = entity.getDeltaMovement();
        int id = entity.getId();
        TrackedItem t = this.tracked.get(id);
        if (t == null) {
            if (this.tracked.size() >= MAX_TRACKED) {
                return;
            }
            t = new TrackedItem();
            t.sim = new ItemPhysicsSimulator(level);
            t.entity = entity;
            this.tracked.put(id, t);
        }
        t.refreshCounter++;
        boolean seedChanged = t.lastSeedPos.distanceToSqr(entity.position()) > 0.005
            || t.lastSeedVel.distanceToSqr(vel) > 0.001;
        if (t.path == null || seedChanged || t.refreshCounter >= 20) {
            t.lastSeedPos = entity.position();
            t.lastSeedVel = vel;
            t.refreshCounter = 0;
            String itemId = itemIdOf(entity.getItem());
            t.color = this.colorForId(itemId);
            t.path = t.sim.simulate(t.lastSeedPos.x, t.lastSeedPos.y, t.lastSeedPos.z,
                vel.x, vel.y, vel.z, entity.getItem());
        }
        // make sure the path starts where the entity is now (sim result is anchored)
        if (t.path != null && !t.path.points.isEmpty()) {
            t.path.points.set(0, entity.position());
        }
    }
}
