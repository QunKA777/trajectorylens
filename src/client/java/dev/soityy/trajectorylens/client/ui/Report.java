package dev.soityy.trajectorylens.client.ui;

import dev.soityy.trajectorylens.client.track.EntityCensus;
import dev.soityy.trajectorylens.client.track.FlowTracker;
import dev.soityy.trajectorylens.client.track.LootTracker;
import dev.soityy.trajectorylens.client.track.OverlayState;
import dev.soityy.trajectorylens.client.track.ProjectileOverlay;
import dev.soityy.trajectorylens.client.track.ThreatOverlay;
import dev.soityy.trajectorylens.client.track.WalkRangeOverlay;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;

/**
 * Writes a plain-text snapshot of everything the client currently knows (census,
 * counters with their trend, jams, lost-item provenance, toggles) next to the config
 * file, so a farm's behaviour can be compared across sessions.
 */
public final class Report {

    private Report() {
    }

    public static List<String> lines(OverlayState state, WalkRangeOverlay ranges, ProjectileOverlay proj,
                                     FlowTracker flow, EntityCensus census, ThreatOverlay threats, LootTracker loot) {
        List<String> out = new ArrayList<>();
        out.add("TrajectoryLens report " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        out.add("");
        out.add("[开关]");
        out.add("  " + state.listing());
        out.add("  " + ranges.summary());
        out.add("  " + proj.summary());
        out.add("  " + flow.summary());
        out.add("  " + threats.summary());
        out.add("  " + loot.summary());
        out.add("");
        out.add("[实体普查]");
        out.addAll(indent(census.report()));
        out.add("");
        out.add("[卡口计数器]");
        if (flow.counters().isEmpty()) {
            out.add("  (无)");
        } else {
            for (FlowTracker.Counter c : flow.counters().values()) {
                out.add(String.format("  %s @ (%.1f, %.1f, %.1f): %d/min (%d/min 5分钟均值) 趋势%s 堆积%.0f 累计%d 面%dx%d",
                    c.name, c.x, c.y, c.z, c.perMinute(), (int) c.perMinute5(), c.spark(), c.backlog, c.total, (int) c.w, (int) c.h));
            }
        }
        out.add("");
        out.add("[堵塞]");
        out.add("  " + flow.jamSummary());
        out.add("");
        out.add("[失踪溯源]");
        out.addAll(indent(loot.report()));
        return out;
    }

    /** Writes the report and returns the file (null on failure). */
    public static File write(List<String> lines) {
        try {
            File dir = new File(Minecraft.getInstance().gameDirectory, "config");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File f = new File(dir, "trajectorylens-report-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");
            Files.write(f.toPath(), String.join(System.lineSeparator(), lines).getBytes(StandardCharsets.UTF_8));
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> indent(List<String> in) {
        List<String> out = new ArrayList<>();
        for (String s : in) {
            out.add("  " + s);
        }
        return out;
    }
}
