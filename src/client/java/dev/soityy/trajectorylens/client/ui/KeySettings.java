package dev.soityy.trajectorylens.client.ui;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;

/**
 * Chord-style hotkeys (up to 3 keys held together). Bound by pressing the keys
 * directly; not KeyMappings so nothing can hijack them. Persisted by SettingsIO.
 */
public final class KeySettings {

    public static int[] rangeChord = {InputConstants.KEY_O, InputConstants.KEY_P};
    public static int[] panelChord = {InputConstants.KEY_H, InputConstants.KEY_J};
    public static final int MAX_KEYS = 3;

    private KeySettings() {
    }

    /** True when every key of the chord is currently held. */
    public static boolean allDown(Window win, int[] chord) {
        if (chord == null || chord.length == 0 || win == null) {
            return false;
        }
        for (int k : chord) {
            if (!InputConstants.isKeyDown(win, k)) {
                return false;
            }
        }
        return true;
    }

    /** "O + P" style label. */
    public static String display(int[] chord) {
        if (chord == null || chord.length == 0) {
            return "未绑定";
        }
        StringBuilder sb = new StringBuilder();
        for (int k : chord) {
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append(name(k));
        }
        return sb.toString();
    }

    /** Build a chord from pressed keys (first MAX_KEYS kept, Esc/Backspace filtered). */
    public static int[] chordFrom(List<Integer> pressed) {
        List<Integer> out = new ArrayList<>();
        for (int k : pressed) {
            if (k == InputConstants.KEY_ESCAPE || k == InputConstants.KEY_BACKSPACE) {
                continue;
            }
            if (out.size() >= MAX_KEYS) {
                break;
            }
            out.add(k);
        }
        int[] arr = new int[out.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = out.get(i);
        }
        return arr;
    }

    public static String name(int code) {
        if (code >= 'A' && code <= 'Z') {
            return String.valueOf((char) code);
        }
        if (code >= '0' && code <= '9') {
            return String.valueOf((char) code);
        }
        if (code == InputConstants.KEY_ESCAPE) {
            return "Esc";
        }
        if (code == InputConstants.KEY_SPACE) {
            return "Space";
        }
        if (code == InputConstants.KEY_LSHIFT || code == InputConstants.KEY_RSHIFT) {
            return "Shift";
        }
        if (code == InputConstants.KEY_LCONTROL || code == InputConstants.KEY_RCONTROL) {
            return "Ctrl";
        }
        if (code == InputConstants.KEY_LALT || code == InputConstants.KEY_RALT) {
            return "Alt";
        }
        if (code == InputConstants.KEY_TAB) {
            return "Tab";
        }
        if (code == InputConstants.KEY_RETURN) {
            return "Enter";
        }
        if (code == InputConstants.KEY_BACKSPACE) {
            return "Backspace";
        }
        if (code == InputConstants.KEY_MINUS) {
            return "-";
        }
        if (code == InputConstants.KEY_EQUALS) {
            return "=";
        }
        if (code == InputConstants.KEY_LBRACKET) {
            return "[";
        }
        if (code == InputConstants.KEY_RBRACKET) {
            return "]";
        }
        if (code == InputConstants.KEY_SEMICOLON) {
            return ";";
        }
        if (code == InputConstants.KEY_APOSTROPHE) {
            return "'";
        }
        if (code == InputConstants.KEY_GRAVE) {
            return "`";
        }
        if (code == InputConstants.KEY_COMMA) {
            return ",";
        }
        if (code == InputConstants.KEY_PERIOD) {
            return ".";
        }
        if (code == InputConstants.KEY_SLASH) {
            return "/";
        }
        if (code == InputConstants.KEY_BACKSLASH) {
            return "\\";
        }
        if (code >= InputConstants.KEY_F1 && code <= InputConstants.KEY_F25) {
            return "F" + (code - InputConstants.KEY_F1 + 1);
        }
        return "键" + code;
    }
}
