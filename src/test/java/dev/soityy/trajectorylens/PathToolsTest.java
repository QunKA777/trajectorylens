package dev.soityy.trajectorylens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.soityy.trajectorylens.util.PathTools;

class PathToolsTest {

    @Test
    void decimateEmpty() {
        assertTrue(PathTools.decimate(List.of(), 10).isEmpty());
    }

    @Test
    void decimateKeepsAllWhenSmall() {
        List<Integer> pts = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            pts.add(i);
        }
        List<Integer> out = PathTools.decimate(pts, 10);
        assertEquals(5, out.size());
        assertEquals(Integer.valueOf(0), out.get(0));
    }

    @Test
    void decimateLargeKeepsEndpointsAndBounds() {
        List<Integer> pts = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            pts.add(i);
        }
        List<Integer> out = PathTools.decimate(pts, PathTools.MAX_SAMPLES);
        assertTrue(out.size() <= PathTools.MAX_SAMPLES + 1);
        assertEquals(Integer.valueOf(0), out.get(0));
        assertEquals(Integer.valueOf(999), out.get(out.size() - 1));
    }

    @Test
    void argbPacking() {
        assertEquals(0x8033ccff, PathTools.argb(0x33, 0xcc, 0xff, 0x80));
    }

    @Test
    void parseColorVariants() {
        assertEquals(0xEAFF0000, PathTools.parseColor("FF0000"));
        assertEquals(0xEAFF0000, PathTools.parseColor("#ff0000"));
        assertEquals(0x80123456, PathTools.parseColor("80123456"));
        assertEquals(0x8033CCFF, PathTools.parseColor("0x8033CCFF"));
        assertEquals(null, PathTools.parseColor("red"));
        assertEquals(null, PathTools.parseColor("12345"));
        assertEquals(null, PathTools.parseColor("GGGGGG"));
    }

    @Test
    void parseColorAutoWord() {
        assertEquals(null, PathTools.parseColor("auto"));
    }
    @Test
    void lerpEndpoints() {
        int a = PathTools.argb(0, 0, 0, 0);
        int b = PathTools.argb(255, 255, 255, 255);
        assertEquals(a, PathTools.lerpArgb(a, b, 0.0F));
        assertEquals(b, PathTools.lerpArgb(a, b, 1.0F));
        int mid = PathTools.lerpArgb(a, b, 0.5F);
        assertEquals(0x80, mid >>> 24);
        assertEquals(0x80, mid >> 16 & 0xFF);
    }
}