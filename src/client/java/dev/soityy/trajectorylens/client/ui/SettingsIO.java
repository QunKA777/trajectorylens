package dev.soityy.trajectorylens.client.ui;

import dev.soityy.trajectorylens.client.track.FlowTracker;
import dev.soityy.trajectorylens.client.track.LootTracker;
import dev.soityy.trajectorylens.client.track.OverlayState;
import dev.soityy.trajectorylens.client.track.ProjectileOverlay;
import dev.soityy.trajectorylens.client.track.ThreatOverlay;
import dev.soityy.trajectorylens.client.track.WalkRangeOverlay;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

/**
 * Small JSON persistence for the control panel: toggles, watch lists, colors,
 * horizon and chord keys. Written to <gamedir>/config/trajectorylens.json.
 */
public final class SettingsIO {

    public boolean dropsOn;
    public boolean rangesOn;
    public boolean projectilesOn = true;
    public boolean tntOn = true;
    public boolean chainOn = true;
    public boolean flowCountersOn = true;
    public boolean aimOn = true;
    public boolean threatOn = true;
    public boolean lootOn = true;
    public boolean despawnWarnOn = true;
    public boolean jamOn = true;
    public int jamSeconds = 6;
    public boolean fallingOn = true;
    public int chainDepth = 3;
    public int chainHorizonSeconds = 6;
    public int lostMarkerSeconds = 6;
    public int hopperGlowSeconds = 5;
    public final List<CounterEntry> counters = new ArrayList<>();

    /** Persisted chokepoint counter plane. */
    public static final class CounterEntry {
        public String name = "c";
        public double x;
        public double y;
        public double z;
        public double nx;
        public double ny = 1;
        public double nz;
        public double w = 3;
        public double h = 3;
    }
    public int horizonSeconds = 5;
    public final List<Integer> rangeKeys = new ArrayList<>();
    public final List<Integer> panelKeys = new ArrayList<>();
    public final List<String> dropWatch = new ArrayList<>();
    public final Map<String, String> dropColors = new LinkedHashMap<>();
    public final List<String> rangeWatch = new ArrayList<>();
    public final Map<String, String> rangeColors = new LinkedHashMap<>();

    private SettingsIO() {
    }

    public static File file() {
        File dir = new File(Minecraft.getInstance().gameDirectory, "config");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File f = new File(dir, "trajectorylens.json");
        if (!f.isFile()) {
            // one-time migration: keep the settings of a pre-rename install
            File legacy = new File(dir, "itemtrajectory.json");
            if (legacy.isFile()) {
                return legacy;
            }
        }
        return f;
    }

    public static SettingsIO load() {
        SettingsIO s = new SettingsIO();
        try {
            File f = file();
            if (!f.isFile()) {
                return s;
            }
            JsonObject o = JsonParser.parseString(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
            s.dropsOn = bool(o, "dropsOn", false);
            s.rangesOn = bool(o, "rangesOn", false);
            s.projectilesOn = bool(o, "projectilesOn", true);
            s.tntOn = bool(o, "tntOn", true);
            s.chainOn = bool(o, "chainOn", true);
            s.flowCountersOn = bool(o, "flowCountersOn", true);
            s.aimOn = bool(o, "aimOn", true);
            s.threatOn = bool(o, "threatOn", true);
            s.lootOn = bool(o, "lootOn", true);
            s.despawnWarnOn = bool(o, "despawnWarnOn", true);
            s.jamOn = bool(o, "jamOn", true);
            s.jamSeconds = intOf(o, "jamSeconds", 6);
            s.fallingOn = bool(o, "fallingOn", true);
            s.chainDepth = intOf(o, "chainDepth", 3);
            s.chainHorizonSeconds = intOf(o, "chainHorizonSeconds", 6);
            s.lostMarkerSeconds = intOf(o, "lostMarkerSeconds", 6);
            s.hopperGlowSeconds = intOf(o, "hopperGlowSeconds", 5);
            listCounters(o, "counters", s.counters);
            s.horizonSeconds = intOf(o, "horizonSeconds", 5);
            intList(o, "rangeKeys", s.rangeKeys);
            intList(o, "panelKeys", s.panelKeys);
            // legacy single-pair schema (older builds)
            if (s.rangeKeys.isEmpty()) {
                int a = intOf(o, "rangeKeyA", 0);
                int b = intOf(o, "rangeKeyB", 0);
                if (a != 0) {
                    s.rangeKeys.add(a);
                }
                if (b != 0) {
                    s.rangeKeys.add(b);
                }
            }
            if (s.panelKeys.isEmpty()) {
                int a = intOf(o, "panelKeyA", 0);
                int b = intOf(o, "panelKeyB", 0);
                if (a != 0) {
                    s.panelKeys.add(a);
                }
                if (b != 0) {
                    s.panelKeys.add(b);
                }
            }
            list(o, "dropWatch", s.dropWatch);
            list(o, "rangeWatch", s.rangeWatch);
            map(o, "dropColors", s.dropColors);
            map(o, "rangeColors", s.rangeColors);
        } catch (Exception ignored) {
            // corrupt/old file: start over
        }
        return s;
    }

    public void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("dropsOn", this.dropsOn);
            o.addProperty("rangesOn", this.rangesOn);
            o.addProperty("projectilesOn", this.projectilesOn);
            o.addProperty("tntOn", this.tntOn);
            o.addProperty("chainOn", this.chainOn);
            o.addProperty("flowCountersOn", this.flowCountersOn);
            o.addProperty("aimOn", this.aimOn);
            o.addProperty("threatOn", this.threatOn);
            o.addProperty("lootOn", this.lootOn);
            o.addProperty("despawnWarnOn", this.despawnWarnOn);
            o.addProperty("jamOn", this.jamOn);
            o.addProperty("jamSeconds", this.jamSeconds);
            o.addProperty("fallingOn", this.fallingOn);
            o.addProperty("chainDepth", this.chainDepth);
            o.addProperty("chainHorizonSeconds", this.chainHorizonSeconds);
            o.addProperty("lostMarkerSeconds", this.lostMarkerSeconds);
            o.addProperty("hopperGlowSeconds", this.hopperGlowSeconds);
            o.add("counters", countersArr(this.counters));
            o.addProperty("horizonSeconds", this.horizonSeconds);
            o.add("rangeKeys", intArr(this.rangeKeys));
            o.add("panelKeys", intArr(this.panelKeys));
            o.add("dropWatch", arr(this.dropWatch));
            o.add("rangeWatch", arr(this.rangeWatch));
            o.add("dropColors", obj(this.dropColors));
            o.add("rangeColors", obj(this.rangeColors));
            Files.write(file().toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(o).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // best effort persistence
        }
    }

    private static boolean bool(JsonObject o, String k, boolean d) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() && e.getAsBoolean();
    }

    private static int intOf(JsonObject o, String k, int d) {
        try {
            JsonElement e = o.get(k);
            return e != null && e.isJsonPrimitive() ? e.getAsInt() : d;
        } catch (Exception ex) {
            return d;
        }
    }

    private static void list(JsonObject o, String k, List<String> out) {
        JsonElement e = o.get(k);
        if (e != null && e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                out.add(x.getAsString());
            }
        }
    }

    private static void map(JsonObject o, String k, Map<String, String> out) {
        JsonElement e = o.get(k);
        if (e != null && e.isJsonObject()) {
            for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
                out.put(en.getKey(), en.getValue().getAsString());
            }
        }
    }

    private static void listCounters(JsonObject o, String k, List<CounterEntry> out) {
        JsonElement e = o.get(k);
        if (e == null || !e.isJsonArray()) {
            return;
        }
        for (JsonElement x : e.getAsJsonArray()) {
            if (!x.isJsonObject()) {
                continue;
            }
            JsonObject c = x.getAsJsonObject();
            CounterEntry en = new CounterEntry();
            en.name = c.has("name") ? c.get("name").getAsString() : "c";
            en.x = c.has("x") ? c.get("x").getAsDouble() : 0;
            en.y = c.has("y") ? c.get("y").getAsDouble() : 0;
            en.z = c.has("z") ? c.get("z").getAsDouble() : 0;
            en.nx = c.has("nx") ? c.get("nx").getAsDouble() : 0;
            en.ny = c.has("ny") ? c.get("ny").getAsDouble() : 1;
            en.nz = c.has("nz") ? c.get("nz").getAsDouble() : 0;
            en.w = c.has("w") ? c.get("w").getAsDouble() : 3;
            en.h = c.has("h") ? c.get("h").getAsDouble() : 3;
            out.add(en);
        }
    }

    private static JsonArray countersArr(List<CounterEntry> xs) {
        JsonArray a = new JsonArray();
        for (CounterEntry c : xs) {
            JsonObject o = new JsonObject();
            o.addProperty("name", c.name);
            o.addProperty("x", c.x);
            o.addProperty("y", c.y);
            o.addProperty("z", c.z);
            o.addProperty("nx", c.nx);
            o.addProperty("ny", c.ny);
            o.addProperty("nz", c.nz);
            o.addProperty("w", c.w);
            o.addProperty("h", c.h);
            a.add(o);
        }
        return a;
    }

    private static void intList(JsonObject o, String k, List<Integer> out) {
        JsonElement e = o.get(k);
        if (e != null && e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                out.add(x.getAsInt());
            }
        }
    }

    private static JsonArray intArr(List<Integer> xs) {
        JsonArray a = new JsonArray();
        for (int x : xs) {
            a.add(x);
        }
        return a;
    }

    private static JsonArray arr(List<String> xs) {
        JsonArray a = new JsonArray();
        for (String x : xs) {
            a.add(x);
        }
        return a;
    }

    private static JsonObject obj(Map<String, String> m) {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, String> e : m.entrySet()) {
            o.addProperty(e.getKey(), e.getValue());
        }
        return o;
    }

    /** ARGB int -> RRGGBB (default alpha) or AARRGGBB. */
    public static String specOf(int argb) {
        int a = argb >>> 24;
        return a == 0xEA
            ? String.format("%06X", argb & 0xFFFFFF)
            : String.format("%08X", argb);
    }

    /** Snapshot current live state into a SettingsIO (explicit colors only; auto ones regenerate). */
    public static SettingsIO snapshot(OverlayState st, WalkRangeOverlay wr, ProjectileOverlay proj, FlowTracker flow,
                                      ThreatOverlay threats, LootTracker loot) {
        SettingsIO s = new SettingsIO();
        s.dropsOn = st.visible();
        s.rangesOn = wr.isEnabled();
        s.projectilesOn = proj.projectilesEnabled();
        s.tntOn = proj.tntEnabled();
        s.chainOn = proj.chainEnabled();
        s.aimOn = proj.aimEnabled();
        s.threatOn = threats.enabled();
        s.lootOn = loot.enabled();
        s.lostMarkerSeconds = loot.markerSeconds();
        s.hopperGlowSeconds = loot.glowSeconds();
        s.despawnWarnOn = loot.despawnWarnEnabled();
        s.jamOn = flow.jamEnabled();
        s.jamSeconds = flow.jamSeconds();
        s.fallingOn = proj.fallingEnabled();
        s.chainDepth = proj.chainDepth();
        s.chainHorizonSeconds = proj.chainHorizonSeconds();
        s.flowCountersOn = flow.countersEnabled();
        for (var c : flow.counterSnapshot()) {
            CounterEntry en = new CounterEntry();
            en.name = c.name;
            en.x = c.x;
            en.y = c.y;
            en.z = c.z;
            en.nx = c.nx;
            en.ny = c.ny;
            en.nz = c.nz;
            en.w = c.w;
            en.h = c.h;
            s.counters.add(en);
        }
        s.horizonSeconds = wr.horizonSeconds();
        for (int k : KeySettings.rangeChord) {
            s.rangeKeys.add(k);
        }
        for (int k : KeySettings.panelChord) {
            s.panelKeys.add(k);
        }
        s.dropWatch.addAll(st.watchItemIdsSnapshot());
        s.dropColors.putAll(st.colorMapSnapshot());
        s.rangeWatch.addAll(wr.typeIdsSnapshot());
        s.rangeColors.putAll(wr.colorMapSnapshot());
        return s;
    }
}
