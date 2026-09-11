package dev.soityy.trajectorylens.client.track;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.phys.Vec3;

/**
 * Chokepoint counters: virtual planes that count item entities crossing them
 * (per-minute rate, 5-minute average, upstream backlog). Purely client-side, so
 * it works on a vanilla server too - it only counts what this client can see.
 */
public final class FlowTracker {

    public static final double SCAN_RADIUS = 64.0;
    public static final double COUNTER_RANGE = 24.0;

    /** Queue durations offered on the jam row (seconds). */
    public static final int[] JAM_CHOICES = {3, 5, 6, 8, 10, 15, 30};

    private boolean countersOn = true;
    private boolean jamOn = true;
    private int jamSeconds = 6;
    private final Map<String, Counter> counters = new LinkedHashMap<>();
    private final Map<Long, Jam> jams = new HashMap<>();
    private int tick;

    /** Items resting on/above one hopper for too long: the hopper is not accepting them. */
    public static final class Jam {
        public Hoppers.Hit hit;
        public int items;
        public int sinceTick;
        public int lastSeenTick;
        public String sample = "";
        /** Filled in by {@link #jams()} so renderers know how long the queue has been stuck. */
        public int heldTicks;

        public Jam(Hoppers.Hit hit, int tick) {
            this.hit = hit;
            this.sinceTick = tick;
            this.lastSeenTick = tick;
        }

    }

    public static final class Counter {
        public String name;
        public double x;
        public double y;
        public double z;
        public double nx;
        public double ny;
        public double nz;
        public double w = 3.0;
        public double h = 3.0;
        public final int[] sec60 = new int[60];
        public final int[] sec300 = new int[300];
        /** /min sampled every 15 s (oldest first) - rendered as a sparkline in the panel. */
        public final int[] trend = new int[12];
        public double backlog;
        public long total;
        public final Map<Integer, Vec3> lastPos = new HashMap<>();

        public Vec3 pos() {
            return new Vec3(this.x, this.y, this.z);
        }

        public int perMinute() {
            int s = 0;
            for (int v : this.sec60) {
                s += v;
            }
            return s;
        }

        public double perMinute5() {
            int s = 0;
            for (int v : this.sec300) {
                s += v;
            }
            return s / 5.0;
        }

        /** Unicode sparkline of the last three minutes of throughput. */
        public String spark() {
            int max = 0;
            for (int v : this.trend) {
                max = Math.max(max, v);
            }
            if (max <= 0) {
                return "\u2581\u2581\u2581\u2581\u2581\u2581";
            }
            String bars = "\u2581\u2582\u2583\u2584\u2585\u2586\u2587\u2588";
            StringBuilder sb = new StringBuilder();
            for (int v : this.trend) {
                int idx = (int) Math.round(v / (double) max * (bars.length() - 1));
                sb.append(bars.charAt(Math.max(0, Math.min(bars.length() - 1, idx))));
            }
            return sb.toString();
        }

        /** Trend direction over the sampled window: +1 rising, -1 falling, 0 flat. */
        public int trendDir() {
            int half = this.trend.length / 2;
            int oldSum = 0;
            int newSum = 0;
            for (int i = 0; i < half; i++) {
                oldSum += this.trend[i];
                newSum += this.trend[i + half];
            }
            if (newSum > oldSum * 1.15 + 2) {
                return 1;
            }
            if (oldSum > newSum * 1.15 + 2) {
                return -1;
            }
            return 0;
        }
    }

    public boolean countersEnabled() {
        return this.countersOn;
    }

    public boolean jamEnabled() {
        return this.jamOn;
    }

    public void setJam(boolean on) {
        this.jamOn = on;
        if (!on) {
            this.jams.clear();
        }
    }

    public void toggleJam() {
        this.setJam(!this.jamOn);
    }

    public int jamSeconds() {
        return this.jamSeconds;
    }

    public void setJamSeconds(int seconds) {
        this.jamSeconds = Math.max(1, Math.min(120, seconds));
    }

    public void cycleJamSeconds() {
        for (int c : JAM_CHOICES) {
            if (c > this.jamSeconds) {
                this.jamSeconds = c;
                return;
            }
        }
        this.jamSeconds = JAM_CHOICES[0];
    }

    /** Currently flagged jams (queued items that the hopper refuses to take). */
    public List<Jam> jams() {
        List<Jam> out = new ArrayList<>();
        for (Jam j : this.jams.values()) {
            j.heldTicks = this.tick - j.sinceTick;
            if (j.items > 0 && (j.heldTicks >= this.jamSeconds * 20 || j.hit.locked())) {
                out.add(j);
            }
        }
        return out;
    }

    public void setCounters(boolean on) {
        this.countersOn = on;
    }

    public void toggleCounters() {
        this.countersOn = !this.countersOn;
    }

    public Map<String, Counter> counters() {
        return this.counters;
    }

    /** Add (or replace) a counter plane at the player's crosshair target. */
    public String addCounter(String name) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.level == null) {
            return "not in a world";
        }
        var hit = player.pick(16.0, 0.0F, false);
        Vec3 pt;
        double nx = 0;
        double ny = 0;
        double nz = 0;
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
            || !(hit instanceof net.minecraft.world.phys.BlockHitResult bhr)) {
            pt = player.getEyePosition().add(player.getLookAngle().scale(4.0));
            Vec3 look = player.getLookAngle();
            double ax = Math.abs(look.x);
            double ay = Math.abs(look.y);
            double az = Math.abs(look.z);
            if (ax >= ay && ax >= az) {
                nx = Math.signum(look.x);
            } else if (ay >= az) {
                ny = Math.signum(look.y);
            } else {
                nz = Math.signum(look.z);
            }
        } else {
            var face = bhr.getDirection();
            pt = bhr.getLocation();
            nx = face.getStepX();
            ny = face.getStepY();
            nz = face.getStepZ();
        }
        Counter c = new Counter();
        c.name = name;
        c.x = pt.x;
        c.y = pt.y;
        c.z = pt.z;
        c.nx = nx;
        c.ny = ny;
        c.nz = nz;
        this.counters.put(name, c);
        return null;
    }

    public boolean removeCounter(String name) {
        return this.counters.remove(name) != null;
    }

    public void clearCounters() {
        this.counters.clear();
    }

    public String summary() {
        StringBuilder sb = new StringBuilder("counters=" + (this.countersOn ? "on" : "off")
            + ", jam=" + (this.jamOn ? "on/" + this.jamSeconds + "s" : "off")
            + ", count=" + this.counters.size());
        for (Counter c : this.counters.values()) {
            sb.append(" | ").append(c.name).append(": ").append(c.perMinute()).append("/min").append(c.spark())
                .append(", 堆积 ")
                .append(String.format("%.0f", c.backlog));
        }
        return sb.toString();
    }

    public void tick(Minecraft mc) {
        this.tick++;
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            this.counters.clear();
            return;
        }
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class,
            mc.player.getBoundingBox().inflate(SCAN_RADIUS));
        if (this.jamOn) {
            this.tickJams(level, items);
        } else {
            this.jams.clear();
        }
        if (!this.countersOn || this.counters.isEmpty()) {
            return;
        }
        int slot60 = (this.tick / 20) % 60;
        int slot300 = this.tick % 300;
        for (Counter c : this.counters.values()) {
            if (this.tick % 20 == 0) {
                c.sec60[slot60] = 0;
                if (slot300 < c.sec300.length) {
                    c.sec300[slot300] = 0;
                }
            }
            Iterator<Map.Entry<Integer, Vec3>> it = c.lastPos.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Vec3> en = it.next();
                var ent = level.getEntity(en.getKey());
                if (!(ent instanceof ItemEntity item) || !item.isAlive()) {
                    it.remove();
                }
            }
            for (ItemEntity e : items) {
                if (e.position().distanceTo(c.pos()) > COUNTER_RANGE) {
                    continue;
                }
                Vec3 cur = e.position();
                Vec3 prev = c.lastPos.put(e.getId(), cur);
                if (prev == null) {
                    continue;
                }
                double s0 = side(prev, c);
                double s1 = side(cur, c);
                if (s0 == 0 || s1 == 0 || (s0 > 0) == (s1 > 0) || !within(c, cur)) {
                    continue;
                }
                c.sec60[slot60]++;
                if (slot300 < c.sec300.length) {
                    c.sec300[slot300]++;
                }
                c.total++;
            }
            if (this.tick % 300 == 0) {
                System.arraycopy(c.trend, 1, c.trend, 0, c.trend.length - 1);
                c.trend[c.trend.length - 1] = c.perMinute();
            }
            if (this.tick % 20 == 0) {
                int backlog = 0;
                for (ItemEntity e : items) {
                    double s = side(e.position(), c);
                    if (s > 0 && e.position().distanceTo(c.pos()) < 8.0 && within(c, e.position())) {
                        backlog++;
                    }
                }
                c.backlog = backlog;
            }
        }
    }

    /**
     * Items that stopped moving on a hopper are the signature of a jam: either the hopper
     * is redstone-locked, or its destination is full, or the item can never be taken.
     * Client-side observation only.
     */
    private void tickJams(ClientLevel level, List<ItemEntity> items) {
        Map<Long, Jam> seen = new HashMap<>();
        List<MinecartHopper> carts = new ArrayList<>();
        boolean cartsLoaded = false;
        for (ItemEntity e : items) {
            Vec3 p = e.position();
            if (!e.isAlive() || !e.onGround() || e.getDeltaMovement().lengthSqr() > 4.0E-4) {
                continue; // still moving: not queued
            }
            Hoppers.Hit hit = Hoppers.find(level, p, 1, 1, 0, 0.4, true, false);
            if (hit == null) {
                if (!cartsLoaded) {
                    cartsLoaded = true;
                    carts = level.getEntitiesOfClass(MinecartHopper.class,
                        Minecraft.getInstance().player.getBoundingBox().inflate(SCAN_RADIUS));
                }
                for (MinecartHopper cart : carts) {
                    if (cart.getBoundingBox().inflate(0.4).contains(p)) {
                        hit = new Hoppers.Hit(null, cart, false);
                        break;
                    }
                }
            }
            if (hit == null) {
                continue;
            }
            long key = hit.key();
            Jam jam = seen.get(key);
            if (jam == null) {
                Jam old = this.jams.get(key);
                if (old != null && this.tick - old.lastSeenTick <= 2) {
                    jam = old;                       // same queue as last tick: keep its start time
                } else {
                    jam = new Jam(hit, this.tick);   // a fresh queue starts here
                }
                jam.hit = hit;
                jam.items = 0;
                jam.sample = e.getItem().getHoverName().getString();
                seen.put(key, jam);
            }
            jam.items++;
            jam.lastSeenTick = this.tick;
        }
        // drop queues that cleared (the hopper finally took them)
        this.jams.entrySet().removeIf(en -> this.tick - en.getValue().lastSeenTick > 20);
        this.jams.putAll(seen);
    }

    public String jamSummary() {
        List<Jam> list = jams();
        if (list.isEmpty()) {
            return "堵塞检测=" + (this.jamOn ? "on" : "off") + " 当前无堵塞";
        }
        StringBuilder sb = new StringBuilder("堵塞检测=" + (this.jamOn ? "on" : "off") + " " + list.size() + " 处");
        for (Jam j : list) {
            sb.append(" | ").append(j.hit.locked() ? "红石锁定" : "堵塞")
                .append(" @(").append((int) j.hit.center().x).append(",").append((int) j.hit.center().y)
                .append(",").append((int) j.hit.center().z).append(") ").append(j.items).append(" 件 ")
                .append(String.format("%.1fs", j.heldTicks / 20.0));
        }
        return sb.toString();
    }

    private static double side(Vec3 p, Counter c) {
        return (p.x - c.x) * c.nx + (p.y - c.y) * c.ny + (p.z - c.z) * c.nz;
    }

    private static boolean within(Counter c, Vec3 p) {
        double dx = p.x - c.x;
        double dy = p.y - c.y;
        double dz = p.z - c.z;
        double ax = Math.abs(c.nx);
        double ay = Math.abs(c.ny);
        double u;
        double v;
        if (ax > 0.5) {
            u = dy;
            v = dz;
        } else if (ay > 0.5) {
            u = dx;
            v = dz;
        } else {
            u = dx;
            v = dy;
        }
        return Math.abs(u) <= c.w / 2 && Math.abs(v) <= c.h / 2;
    }

    // ---------- persistence ----------

    public List<Counter> counterSnapshot() {
        return new ArrayList<>(this.counters.values());
    }

    public void loadCounter(String name, double x, double y, double z, double nx, double ny, double nz, double w, double h) {
        Counter c = new Counter();
        c.name = name;
        c.x = x;
        c.y = y;
        c.z = z;
        c.nx = nx;
        c.ny = ny;
        c.nz = nz;
        c.w = w;
        c.h = h;
        this.counters.put(name, c);
    }
}
