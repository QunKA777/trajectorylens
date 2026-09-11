package dev.soityy.trajectorylens.client.track;

import dev.soityy.trajectorylens.client.ui.Report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.soityy.trajectorylens.client.Lang;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Lost & Found: watches item entities, and when one disappears it works out WHY
 * (burned in lava, destroyed by cactus, fell into the void, despawned after five
 * minutes, blown up, merged into another stack, picked up by a player or a
 * machine). Recent losses are drawn as ghost markers and summarised in the
 * stats page / {@code /trajectorylens lost}. Client-side observation only.
 */
public final class LootTracker {

    public static final double WATCH_RADIUS = 96.0;
    private static final int DESPAWN_AGE = 6000;
    private static final int MAX_EVENTS = 300;
    private static final int LOST_SIGHT_DIST = 80;

    /** Ghost-marker lifetime choices (seconds); the default is deliberately short. */
    public static final int[] MARKER_CHOICES = {3, 5, 6, 8, 10, 15, 30, 60};
    /** Hopper-highlight duration choices (seconds). */
    public static final int[] GLOW_CHOICES = {2, 3, 5, 8, 10, 15, 20};
    private int markerSeconds = 6;
    private int glowSeconds = 5;

    /** Warn once an item has been watched this long: vanilla despawns items at 6000 ticks. */
    public static final int DESPAWN_WARN_AT = 5400;
    private boolean despawnWarn = true;
    private final List<Warning> warnings = new ArrayList<>();
    private final java.util.Set<Integer> warned = new java.util.HashSet<>();
    private int lastWarnChatTick = -10000;

    /** A live item that is close to its five-minute despawn timer (observed age). */
    public record Warning(Vec3 pos, String name, int remainingTicks) {
    }

    public enum Reason {
        LAVA("岩浆烧毁", 0xFFFF6A2A),
        FIRE("烧毁", 0xFFFF9A2A),
        CACTUS("仙人掌销毁", 0xFF3FBF4F),
        VOID("掉入虚空", 0xFFB060FF),
        DESPAWN("超时消失", 0xFFB0B0B0),
        EXPLODED("被爆炸摧毁", 0xFFFF4040),
        MERGED("合并堆叠", 0xFF40D0FF),
        PICKED("被玩家拾取", 0xFF80FF80),
        MACHINE("被漏斗吸走", 0xFF40FFD0),
        UNKNOWN("原因未知", 0xFFFFFFFF);

        /** Chinese source text, also the translation key in the language files. */
        public final String key;
        public final int color;

        Reason(String key, int color) {
            this.key = key;
            this.color = color;
        }

        /** Translated label; resolved on demand so a language change is picked up. */
        public String label() {
            return Lang.tr(this.key);
        }
    }

    public static final class Event {
        public Vec3 pos;
        public String itemName;
        public int count;
        public Reason reason;
        public long millis;
    }

    private static final class Watched {
        Vec3 pos;
        String name;
        int count;
        long firstSeenTick;
        long lastSeenTick;
        boolean touchedLava;
        boolean touchedFire;
        boolean touchedCactus;
        // last observed hopper that could swallow this item (used as a fallback so a
        // hopper is never mis-reported as a player pickup)
        BlockPos hopperSeen;
        MinecartHopper hopperCartSeen;
        int hopperSeenTick = -1000;
    }

    /** A hopper that just swallowed something; highlighted while fadeMillis left. */
    public static final class HopperGlow {
        public BlockPos pos;          // hopper block (null when a minecart)
        public net.minecraft.world.entity.vehicle.minecart.MinecartHopper cart;
        public Vec3 from;
        public String label;
        public long expireMillis;
    }

    private boolean on = true;
    private final Map<String, HopperGlow> glows = new HashMap<>();
    private final Map<Integer, Watched> watched = new HashMap<>();
    private final List<Event> events = new ArrayList<>();
    private final Map<Reason, Integer> counters = new HashMap<>();
    private final List<Vec3> blastCenters = new ArrayList<>();
    private final List<Long> blastTicks = new ArrayList<>();
    private int tick;

    public boolean enabled() {
        return this.on;
    }

    public boolean despawnWarnEnabled() {
        return this.despawnWarn;
    }

    public void setDespawnWarn(boolean on) {
        this.despawnWarn = on;
        if (!on) {
            this.warnings.clear();
        }
    }

    public void toggleDespawnWarn() {
        this.setDespawnWarn(!this.despawnWarn);
    }

    /** Live items within two minutes of despawning (client-observed age). */
    public List<Warning> warnings() {
        return this.warnings;
    }

    public void setEnabled(boolean on) {
        this.on = on;
        if (!on) {
            this.watched.clear();
        }
    }

    public void toggle() {
        this.setEnabled(!this.on);
    }

    /** How long a ghost marker stays on screen (milliseconds). */
    public long markerMillis() {
        return this.markerSeconds * 1000L;
    }

    /** How long a hopper stays highlighted after swallowing an item (milliseconds). */
    public long glowMillis() {
        return this.glowSeconds * 1000L;
    }

    public int markerSeconds() {
        return this.markerSeconds;
    }

    public void setMarkerSeconds(int seconds) {
        this.markerSeconds = Math.max(1, Math.min(300, seconds));
    }

    public void cycleMarkerSeconds() {
        this.setMarkerSeconds(next(MARKER_CHOICES, this.markerSeconds));
    }

    public int glowSeconds() {
        return this.glowSeconds;
    }

    public void setGlowSeconds(int seconds) {
        this.glowSeconds = Math.max(1, Math.min(300, seconds));
    }

    public void cycleGlowSeconds() {
        this.setGlowSeconds(next(GLOW_CHOICES, this.glowSeconds));
    }

    private static int next(int[] choices, int current) {
        for (int c : choices) {
            if (c > current) {
                return c;
            }
        }
        return choices[0];
    }

    public List<Event> recentEvents() {
        List<Event> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        long life = markerMillis();
        for (int i = this.events.size() - 1; i >= 0 && out.size() < 15; i--) {
            Event e = this.events.get(i);
            if (now - e.millis <= life) {
                out.add(e);
            }
        }
        return out;
    }

    public int count(Reason r) {
        return this.counters.getOrDefault(r, 0);
    }

    public int total() {
        int t = 0;
        for (int v : this.counters.values()) {
            t += v;
        }
        return t;
    }

    public void clear() {
        this.events.clear();
        this.counters.clear();
    }

    /** Active hopper highlights (expired ones are pruned). */
    public List<HopperGlow> glows() {
        long now = System.currentTimeMillis();
        this.glows.values().removeIf(g -> g.expireMillis <= now);
        return new ArrayList<>(this.glows.values());
    }

    public String summary() {
        return String.format(Lang.tr("失踪溯源=%s 共%d: 岩浆%d 火焰%d 仙人掌%d 虚空%d 超时%d 爆炸%d 合并%d 拾取%d 机器%d 未知%d"),
            this.on ? "on" : "off", total(),
            count(Reason.LAVA), count(Reason.FIRE), count(Reason.CACTUS), count(Reason.VOID),
            count(Reason.DESPAWN), count(Reason.EXPLODED), count(Reason.MERGED),
            count(Reason.PICKED), count(Reason.MACHINE), count(Reason.UNKNOWN));
    }

    public List<String> report() {
        List<String> lines = new ArrayList<>();
        lines.add("[TrajectoryLens] " + summary());
        List<Event> recent = recentEvents();
        if (recent.isEmpty()) {
            lines.add(Lang.tr("[TrajectoryLens] 最近 ") + this.markerSeconds + Lang.tr(" 秒没有物品消失事件。"));
        } else {
            for (Event e : recent) {
                long ago = (System.currentTimeMillis() - e.millis) / 1000;
                lines.add(String.format(Lang.tr("[TrajectoryLens] %s %s×%d @ (%.0f, %.0f, %.0f) %d 秒前"),
                    e.reason.label(), e.itemName, e.count, e.pos.x, e.pos.y, e.pos.z, ago));
            }
        }
        return lines;
    }

    public void tick(Minecraft mc) {
        this.tick++;
        ClientLevel level = mc.level;
        var player = mc.player;
        if (level == null || player == null) {
            this.watched.clear();
            return;
        }
        // remember recent explosion centres (TNT / swelling creepers) for attribution
        this.blastCenters.clear();
        this.blastTicks.clear();
        for (PrimedTnt tnt : level.getEntitiesOfClass(PrimedTnt.class, player.getBoundingBox().inflate(32.0))) {
            this.blastCenters.add(tnt.position());
            this.blastTicks.add((long) this.tick);
        }
        for (Creeper c : level.getEntitiesOfClass(Creeper.class, player.getBoundingBox().inflate(32.0))) {
            if (c.getSwellDir() > 0) {
                this.blastCenters.add(c.position());
                this.blastTicks.add((long) this.tick);
            }
        }
        if (!this.on) {
            this.warnings.clear();
            return;
        }

        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(WATCH_RADIUS));
        this.warnings.clear();
        Set<Integer> present = new HashSet<>();
        for (ItemEntity e : items) {
            if (!e.isAlive() || e.getItem().isEmpty()) {
                continue;
            }
            int id = e.getId();
            present.add(id);
            Watched w = this.watched.get(id);
            if (w == null) {
                w = new Watched();
                w.name = e.getItem().getHoverName().getString();
                w.count = e.getItem().getCount();
                w.firstSeenTick = this.tick;
                this.watched.put(id, w);
            }
            w.pos = e.position();
            w.lastSeenTick = this.tick;
            w.count = Math.max(w.count, e.getItem().getCount());
            // remember hoppers the item is sitting in/near: staggered probe keeps this cheap
            if (((this.tick + id) & 7) == 0) {
                Hoppers.Hit hit = Hoppers.find(level, w.pos, 1, 1, 0, 0.35, false);
                if (hit != null) {
                    w.hopperSeen = hit.pos();
                    w.hopperCartSeen = hit.cart();
                    w.hopperSeenTick = this.tick;
                }
            }
            if (this.despawnWarn) {
                int age = this.tick - (int) w.firstSeenTick;
                if (age >= DESPAWN_WARN_AT) {
                    int remaining = Math.max(0, DESPAWN_AGE - age);
                    this.warnings.add(new Warning(w.pos, w.name, remaining));
                    if (remaining <= 600 && this.warned.add(id) && this.tick - this.lastWarnChatTick >= 20) {
                        this.lastWarnChatTick = this.tick;
                        var pl = Minecraft.getInstance().player;
                        if (pl != null) {
                            pl.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "[TrajectoryLens] ⚠ " + w.name + Lang.tr(" 即将消失(还剩约 ") + (remaining / 20) + Lang.tr(" 秒),快去捡!")));
                        }
                    }
                }
            }
            BlockPos at = BlockPos.containing(w.pos);
            if (level.getFluidState(at).is(FluidTags.LAVA)) {
                w.touchedLava = true;
            }
            if (level.getBlockState(at).is(Blocks.FIRE) || level.getBlockState(at).is(Blocks.SOUL_FIRE)) {
                w.touchedFire = true;
            }
            if (level.getBlockState(at).is(Blocks.CACTUS)) {
                w.touchedCactus = true;
            }
        }

        Iterator<Map.Entry<Integer, Watched>> it = this.watched.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Watched> en = it.next();
            if (present.contains(en.getKey())) {
                continue;
            }
            Watched w = en.getValue();
            it.remove();
            this.warned.remove(en.getKey());
            if (w.pos == null) {
                continue;
            }
            if (w.pos.distanceTo(player.position()) > LOST_SIGHT_DIST) {
                continue; // simply left our view / chunk unloaded: not a real loss
            }
            Reason reason = classify(level, w, items);
            record(w, reason);
        }
    }

    private Reason classify(ClientLevel level, Watched w, List<ItemEntity> stillPresent) {
        if (w.pos.y < level.getMinY() - 4) {
            return Reason.VOID;
        }
        if (w.touchedLava) {
            return Reason.LAVA;
        }
        if (w.touchedFire) {
            return Reason.FIRE;
        }
        if (w.touchedCactus || level.getBlockState(BlockPos.containing(w.pos)).is(Blocks.CACTUS)) {
            return Reason.CACTUS;
        }
        for (int i = 0; i < this.blastCenters.size(); i++) {
            if (this.tick - this.blastTicks.get(i) <= 10
                && this.blastCenters.get(i).distanceTo(w.pos) <= 9.0) {
                return Reason.EXPLODED;
            }
        }
        // machines beat players: a hopper right next to the player used to be reported as a pickup
        Hoppers.Hit hopper = Hoppers.find(level, w.pos, 1, 1, 0, 0.35, false);       // inside the suck volume
        if (hopper == null) {
            hopper = Hoppers.find(level, w.pos, 1, 3, 0, 0.8, false);                 // was dropping straight into one
        }
        if (hopper == null && this.tick - w.hopperSeenTick <= 40 && w.hopperSeen != null
            && new Vec3(w.hopperSeen.getX() + 0.5, w.hopperSeen.getY() + 0.5, w.hopperSeen.getZ() + 0.5)
                .distanceTo(w.pos) <= 3.0) {
            hopper = new Hoppers.Hit(w.hopperSeen, w.hopperCartSeen, false);
        }
        if (hopper != null) {
            glowHopper(hopper.pos(), hopper.cart(), w);
            return Reason.MACHINE;
        }
        var player = Minecraft.getInstance().player;
        if (player != null && playerPickupReach(player, w.pos)) {
            return Reason.PICKED;
        }
        // five minutes on the floor: vanilla despawn, even if somebody is standing nearby
        if (this.tick - w.firstSeenTick >= DESPAWN_AGE) {
            return Reason.DESPAWN;
        }
        // looser proximity fallback (last observed position can be one tick stale)
        if (player != null && player.position().distanceTo(w.pos) <= 1.8) {
            return Reason.PICKED;
        }
        for (ItemEntity other : stillPresent) {
            if (other.isAlive() && other.position().distanceTo(w.pos) <= 1.5
                && other.getItem().getHoverName().getString().equals(w.name)) {
                return Reason.MERGED;
            }
        }
        return Reason.UNKNOWN;
    }

    /** Mirrors vanilla ItemEntity.playerTouch: the player's box inflated by (1.0, 0.5, 1.0). */
    private static boolean playerPickupReach(net.minecraft.world.entity.player.Player player, Vec3 at) {
        AABB item = new AABB(at.x - 0.125, at.y, at.z - 0.125, at.x + 0.125, at.y + 0.25, at.z + 0.125);
        return player.getBoundingBox().inflate(1.0, 0.5, 1.0).intersects(item);
    }

    private void glowHopper(BlockPos pos, MinecartHopper cart, Watched w) {
        String key = pos != null ? ("b" + pos.asLong()) : ("e" + cart.getId());
        HopperGlow g = this.glows.get(key);
        if (g == null) {
            g = new HopperGlow();
            this.glows.put(key, g);
        }
        g.pos = pos;
        g.cart = cart;
        g.from = w.pos;
        g.label = w.name + "×" + w.count;
        g.expireMillis = System.currentTimeMillis() + glowMillis(); // reset on every new pickup
    }

    private void record(Watched w, Reason reason) {
        Event e = new Event();
        e.pos = w.pos;
        e.itemName = w.name;
        e.count = w.count;
        e.reason = reason;
        e.millis = System.currentTimeMillis();
        this.events.add(e);
        while (this.events.size() > MAX_EVENTS) {
            this.events.remove(0);
        }
        this.counters.merge(reason, 1, Integer::sum);
    }
}
