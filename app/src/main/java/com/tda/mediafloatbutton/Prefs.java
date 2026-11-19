package com.tda.mediafloatbutton;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    private static final String NAME = "settings";
    private static final String KEY_OLD_SEEK_SECONDS = "seek_seconds";
    private static final String KEY_ENABLED = "overlay_enabled";
    private static final String KEY_OPACITY = "opacity_percent";
    private static final String KEY_BUTTON_SIZE_DP = "button_size_dp";
    private static final String KEY_PLAY_PAUSE_ENABLED = "play_pause_enabled";
    private static final String KEY_SHOW_PINNED_NOTIFICATION = "show_pinned_notification";
    private static final String KEY_APP_LANGUAGE = "app_language";

    /** Empty tag = follow the system language. */
    public static final String LANGUAGE_SYSTEM = "";

    public static final int DEFAULT_OPACITY = 85;
    public static final int MIN_OPACITY = 10;
    public static final int DEFAULT_BUTTON_SIZE_DP = 56;

    /** Overlay slots in left-to-right display order: « ‹ › » */
    public enum Slot {
        REWIND_BIG(-1, true, 10, false),
        REWIND_SMALL(-1, false, 5, true),
        FORWARD_SMALL(1, false, 5, true),
        FORWARD_BIG(1, true, 10, false);

        public final int direction;
        public final boolean big;
        public final int defaultSeconds;
        public final boolean defaultEnabled;

        Slot(int direction, boolean big, int defaultSeconds, boolean defaultEnabled) {
            this.direction = direction;
            this.big = big;
            this.defaultSeconds = defaultSeconds;
            this.defaultEnabled = defaultEnabled;
        }

        public String glyph() {
            if (direction < 0) return big ? "«" : "‹";
            return big ? "»" : "›";
        }
    }

    private Prefs() {}

    private static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    private static String secondsKey(Slot slot) {
        return "slot_" + slot.name() + "_seconds";
    }

    private static String enabledKey(Slot slot) {
        return "slot_" + slot.name() + "_enabled";
    }

    public static boolean isSlotEnabled(Context ctx, Slot slot) {
        return sp(ctx).getBoolean(enabledKey(slot), slot.defaultEnabled);
    }

    public static void setSlotEnabled(Context ctx, Slot slot, boolean enabled) {
        sp(ctx).edit().putBoolean(enabledKey(slot), enabled).apply();
    }

    public static int getSlotSeconds(Context ctx, Slot slot) {
        SharedPreferences prefs = sp(ctx);
        int fallback = slot.defaultSeconds;
        // Migrate the old single seek value into the two small-step buttons.
        if (!slot.big && prefs.contains(KEY_OLD_SEEK_SECONDS)) {
            fallback = prefs.getInt(KEY_OLD_SEEK_SECONDS, slot.defaultSeconds);
        }
        return clampSeconds(prefs.getInt(secondsKey(slot), fallback));
    }

    public static void setSlotSeconds(Context ctx, Slot slot, int seconds) {
        sp(ctx).edit().putInt(secondsKey(slot), clampSeconds(seconds)).apply();
    }

    public static int getSlotDelta(Context ctx, Slot slot) {
        return slot.direction * getSlotSeconds(ctx, slot);
    }

    public static int countEnabledSlots(Context ctx) {
        int count = 0;
        for (Slot slot : Slot.values()) {
            if (isSlotEnabled(ctx, slot)) count++;
        }
        return count;
    }

    public static boolean isPlayPauseEnabled(Context ctx) {
        return sp(ctx).getBoolean(KEY_PLAY_PAUSE_ENABLED, true);
    }

    public static void setPlayPauseEnabled(Context ctx, boolean enabled) {
        sp(ctx).edit().putBoolean(KEY_PLAY_PAUSE_ENABLED, enabled).apply();
    }

    public static boolean isPinnedNotificationShown(Context ctx) {
        return sp(ctx).getBoolean(KEY_SHOW_PINNED_NOTIFICATION, false);
    }

    public static void setPinnedNotificationShown(Context ctx, boolean shown) {
        sp(ctx).edit().putBoolean(KEY_SHOW_PINNED_NOTIFICATION, shown).apply();
    }

    /** Mirror of the in-app language choice; used to localize services pre-Android 13. */
    public static String getAppLanguage(Context ctx) {
        return sp(ctx).getString(KEY_APP_LANGUAGE, LANGUAGE_SYSTEM);
    }

    public static void setAppLanguage(Context ctx, String languageTag) {
        sp(ctx).edit().putString(KEY_APP_LANGUAGE, languageTag).apply();
    }

    public static int countVisibleButtons(Context ctx) {
        return countEnabledSlots(ctx) + (isPlayPauseEnabled(ctx) ? 1 : 0);
    }

    private static int clampSeconds(int seconds) {
        return Math.max(1, Math.min(600, seconds));
    }

    public static boolean isOverlayEnabled(Context ctx) {
        return sp(ctx).getBoolean(KEY_ENABLED, true);
    }

    public static void setOverlayEnabled(Context ctx, boolean enabled) {
        sp(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static int getOpacityPercent(Context ctx) {
        return Math.max(MIN_OPACITY, Math.min(100, sp(ctx).getInt(KEY_OPACITY, DEFAULT_OPACITY)));
    }

    public static void setOpacityPercent(Context ctx, int percent) {
        sp(ctx).edit().putInt(KEY_OPACITY, Math.max(MIN_OPACITY, Math.min(100, percent))).apply();
    }

    public static int getButtonSizeDp(Context ctx) {
        return Math.max(40, Math.min(96, sp(ctx).getInt(KEY_BUTTON_SIZE_DP, DEFAULT_BUTTON_SIZE_DP)));
    }

    public static void setButtonSizeDp(Context ctx, int dp) {
        sp(ctx).edit().putInt(KEY_BUTTON_SIZE_DP, Math.max(40, Math.min(96, dp))).apply();
    }
}
