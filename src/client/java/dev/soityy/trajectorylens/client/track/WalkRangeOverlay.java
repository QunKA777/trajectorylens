package dev.soityy.trajectorylens.client.track;

import dev.soityy.trajectorylens.client.ui.KeySettings;
import dev.soityy.trajectorylens.client.ui.SettingsIO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;
import dev.soityy.trajectorylens.util.PathTools;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Walk-range overlay state: independent O+P chord toggle, per-entity-type watch
 * list, horizon, colors and the computed reachable regions (cached + staggered).
 * Lives entirely on the client; driven by commands forwarded from the server.
 */
public final class WalkRangeOverlay {

    public static final int MAX_REGIONS = 8;
    public static final double SCAN_RADIUS = 48.0;
    private static final int RECOMPUTE_TICKS = 10;   // ~0.5 s
    private static final int MAX_COMPUTE_PER_TICK = 2;
    private static final double MOVE_EPS = 0.25;

    // Raw O+P chord: deliberately NOT a KeyMapping so stale options.txt bindings
    // and other mods' single-key conflicts can never hijack the toggle.
    private boolean prevChord;

    private boolean enabled;
    private int horizonSeconds = 5; // default 5s: smaller region, cheaper & clearer
    private final Map<String, EntityType<?>> watchTypes = new LinkedHashMap<>(); // canonical id -> type
    private final Map<String, Integer> typeColors = new LinkedHashMap<>();       // explicit ARGB
    private final Map<String, Integer> autoColors = new LinkedHashMap<>();
    private final Map<Integer, RegionEntry> regions = new LinkedHashMap<>();
    private int tick;

    public static final class RegionEntry {
        public Mob mob;
        public WalkRangeEngine.Region region;
        public Vec3 origin = Vec3.ZERO;
        public int lastComputeTick = -100;
        public int color = OverlayState.PALETTE[0];
    }

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> {
            this.regions.clear();
        });
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void toggle() {
        this.enabled = !this.enabled;
        if (!this.enabled) {
            this.regions.clear();
        }
    }

    public Map<Integer, RegionEntry> regions() {
        return this.regions;
    }

    private java.util.List<String> pendingTypes;

    public void setEnabledState(boolean on) {
        this.enabled = on;
        if (!on) {
            this.regions.clear();
        }
    }

    public void queueTypeIds(java.util.List<String> ids) {
        this.pendingTypes = ids == null ? null : new java.util.ArrayList<>(ids);
    }

    public java.util.List<String> typeIdsSnapshot() {
        return new java.util.ArrayList<>(this.watchTypes.keySet());
    }

    public java.util.Map<String, String> colorMapSnapshot() {
        java.util.Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : this.typeColors.entrySet()) {
            out.put(e.getKey(), SettingsIO.specOf(e.getValue()));
        }
        return out;
    }

    public int horizonSeconds() {
        return this.horizonSeconds;
    }

    /** Add one entity type by id ("zombie" / "minecraft:villager"). Returns error text or null. */
    public @org.jspecify.annotations.Nullable String addTypeFromId(String raw) {
        EntityType<?> type = resolveType(raw);
        if (type == null) {
            return "unknown entity type: " + raw;
        }
        String id = canonicalTypeId(type);
        if (!this.watchTypes.containsKey(id)) {
            this.watchTypes.put(id, type);
        }
        this.regions.clear();
        return null;
    }

    public void clearTypes() {
        this.watchTypes.clear();
        this.regions.clear();
    }

    /** Remove one entity type by canonical id. */
    public void removeType(String id) {
        this.watchTypes.remove(id);
        this.regions.clear();
    }

    public int typeCount() {
        return this.watchTypes.size();
    }

    public boolean isWatching(EntityType<?> type) {
        if (this.watchTypes.isEmpty()) {
            return false;
        }
        for (EntityType<?> t : this.watchTypes.values()) {
            if (t == type) {
                return true;
            }
        }
        return false;
    }

    /** horizon in seconds, clamped to [1, 60]. */
    public void setHorizon(int seconds) {
        this.horizonSeconds = Math.max(1, Math.min(60, seconds));
        this.regions.clear();
    }

    public int colorForType(@org.jspecify.annotations.Nullable String id) {
        if (id == null) {
            return OverlayState.PALETTE[0];
        }
        Integer c = this.typeColors.get(id);
        if (c != null) {
            return c;
        }
        Integer a = this.autoColors.get(id);
        if (a != null) {
            return a;
        }
        int col = OverlayState.PALETTE[this.autoColors.size() % OverlayState.PALETTE.length];
        this.autoColors.put(id, col);
        return col;
    }

    /** colorSpec: RRGGBB / AARRGGBB / auto. Returns error text or null. */
    public @org.jspecify.annotations.Nullable String setColor(String id, String colorSpec) {
        if (id == null) {
            return "null type";
        }
        if (colorSpec == null || colorSpec.trim().equalsIgnoreCase("auto")) {
            this.typeColors.remove(id);
            this.autoColors.remove(id);
        } else {
            Integer argb = PathTools.parseColor(colorSpec);
            if (argb == null) {
                return "bad color '" + colorSpec + "' — use RRGGBB or AARRGGBB, or 'auto'";
            }
            this.typeColors.put(id, argb);
        }
        for (RegionEntry e : this.regions.values()) {
            String mid = typeIdOf(e.mob);
            if (mid != null && mid.equals(id)) {
                e.color = this.colorForType(mid);
            }
        }
        return null;
    }

    public String summary() {
        StringBuilder sb = new StringBuilder("range=");
        sb.append(this.enabled ? "on" : "off")
            .append(", mobs=").append(this.watchTypes.isEmpty() ? "none" : String.join(", ", this.watchTypes.keySet()))
            .append(", time=").append(this.horizonSeconds).append("s");
        if (!this.typeColors.isEmpty() || !this.autoColors.isEmpty()) {
            sb.append(", colors:");
            java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>(this.typeColors.keySet());
            ids.addAll(this.autoColors.keySet());
            for (String id : ids) {
                Integer c = this.typeColors.get(id);
                boolean auto = c == null;
                int argb = auto ? this.autoColors.get(id) : c;
                sb.append(" ").append(id).append("=#")
                    .append(String.format("%06X", argb & 0xFFFFFF))
                    .append(auto ? "(auto)" : "");
            }
        }
        return sb.toString();
    }

    public static @org.jspecify.annotations.Nullable String typeIdOf(Mob mob) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || mob == null) {
            return null;
        }
        Registry<EntityType<?>> reg = level.registryAccess().lookup(Registries.ENTITY_TYPE).orElse(null);
        if (reg == null) {
            return null;
        }
        Identifier key = reg.getKey(mob.getType());
        return key == null ? null : key.toString();
    }

    public static @org.jspecify.annotations.Nullable EntityType<?> resolveType(String raw) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
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
            return null;
        }
        Registry<EntityType<?>> reg = level.registryAccess().lookup(Registries.ENTITY_TYPE).orElse(null);
        if (reg == null || !reg.containsKey(parsed)) {
            return null;
        }
        return reg.getValue(parsed);
    }

    public static @org.jspecify.annotations.Nullable String canonicalTypeId(EntityType<?> type) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || type == null) {
            return null;
        }
        Registry<EntityType<?>> reg = level.registryAccess().lookup(Registries.ENTITY_TYPE).orElse(null);
        if (reg == null) {
            return null;
        }
        Identifier key = reg.getKey(type);
        return key == null ? null : key.toString();
    }

    private void onTick(Minecraft mc) {
        this.tick++;
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            if (!this.regions.isEmpty()) {
                this.regions.clear();
            }
            return;
        }
        var win = mc.getWindow();
        boolean chord = KeySettings.allDown(win, KeySettings.rangeChord);
        if (chord && !this.prevChord) {
            this.toggle();
            var pl = mc.player;
            if (pl != null) {
                pl.sendOverlayMessage(Component.literal(this.enabled
                    ? (this.watchTypes.isEmpty()
                        ? "walk ranges on — add types: /trajectorylens range add <type>"
                        : "walk ranges on")
                    : "walk ranges off"));
            }
        }
        this.prevChord = chord;
        if (!this.enabled) {
            return;
        }

        if (this.pendingTypes != null) {
            java.util.List<String> queued = this.pendingTypes;
            this.pendingTypes = null;
            this.clearTypes();
            for (String s : queued) {
                this.addTypeFromId(s); // errors ignored on restore
            }
        }

        // prune dead mobs
        Iterator<Map.Entry<Integer, RegionEntry>> it = this.regions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, RegionEntry> e = it.next();
            Mob mob = (Mob) level.getEntity(e.getKey());
            if (mob == null || !mob.isAlive()) {
                it.remove();
            }
        }

        // collect watched walkable mobs nearby
        List<Mob> mobs = new ArrayList<>();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, mc.player.getBoundingBox().inflate(SCAN_RADIUS))) {
            if (!mob.isAlive()) {
                continue;
            }
            // 26.2 has no FlyingMob marker class; exclude anything airborne/gravity-less
            // (flyers) and anything riding or ridden right now.
            if (mob.isNoGravity() || (!mob.onGround() && !mob.isInWater())) {
                continue;
            }
            if (mob.isPassenger() || mob.isVehicle()) {
                continue;
            }
            if (!this.isWatching(mob.getType())) {
                continue;
            }
            if (mob.getAttributeValue(Attributes.MOVEMENT_SPEED) < 0.01) {
                continue;
            }
            mobs.add(mob);
        }

        // stagger: at most MAX_REGIONS regions, at most MAX_COMPUTE_PER_TICK recomputes
        int computed = 0;
        for (Mob mob : mobs) {
            int id = mob.getId();
            RegionEntry e = this.regions.get(id);
            if (e == null) {
                if (this.regions.size() >= MAX_REGIONS) {
                    continue;
                }
                e = new RegionEntry();
                e.mob = mob;
                this.regions.put(id, e);
            }
            e.mob = mob;
            Vec3 pos = mob.position();
            boolean due = e.region == null
                || this.tick - e.lastComputeTick >= RECOMPUTE_TICKS
                || e.origin.distanceToSqr(pos) > MOVE_EPS * MOVE_EPS;
            if (due && computed < MAX_COMPUTE_PER_TICK) {
                computed++;
                String typeId = typeIdOf(mob);
                e.color = this.colorForType(typeId);
                e.origin = pos;
                e.lastComputeTick = this.tick;
                double speed = mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
                int ceilH = Math.max(1, (int) Math.ceil(mob.getBbHeight()));
                e.region = WalkRangeEngine.compute(level, speed, ceilH,
                    pos.x, pos.y, pos.z, this.horizonSeconds * 20);
            }
        }
        if (this.regions.size() > MAX_REGIONS) {
            // drop furthest region
            Iterator<Map.Entry<Integer, RegionEntry>> rit = this.regions.entrySet().iterator();
            Map.Entry<Integer, RegionEntry> worst = null;
            double worstD = -1;
            while (rit.hasNext()) {
                Map.Entry<Integer, RegionEntry> en = rit.next();
                if (en.getValue().mob == null) {
                    continue;
                }
                double d = en.getValue().mob.distanceToSqr(mc.player);
                if (d > worstD) {
                    worstD = d;
                    worst = en;
                }
            }
            if (worst != null) {
                this.regions.remove(worst.getKey());
            }
        }
    }
}
