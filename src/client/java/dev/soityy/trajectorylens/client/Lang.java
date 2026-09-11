package dev.soityy.trajectorylens.client;

import net.minecraft.locale.Language;

/**
 * Tiny translation helper.
 *
 * <p>The mod is written with Chinese source strings; every user-visible string is passed
 * through {@link #tr(String)}, which treats that text as a translation key. English lives in
 * {@code assets/trajectorylens/lang/en_us.json}, so:
 *
 * <ul>
 *   <li>players whose game language has a matching entry see the translation;</li>
 *   <li>everyone else simply sees the original Chinese text (Minecraft returns the key
 *       unchanged when a translation is missing), so nothing can ever render as a raw key;</li>
 *   <li>placeholders ({@code %s}, {@code %d}) stay in the string and are filled by the caller
 *       afterwards, so the helper never formats by itself (formatting with zero arguments would
 *       trip {@code String.format}).</li>
 * </ul>
 */
public final class Lang {

    private Lang() {
    }

    /** Translates a (Chinese) source string for the player's current game language. */
    public static String tr(String source) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        return Language.getInstance().getOrDefault(source);
    }
}
