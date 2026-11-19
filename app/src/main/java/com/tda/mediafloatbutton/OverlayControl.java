package com.tda.mediafloatbutton;

import android.content.Context;

/** Single source of truth for turning the overlay on/off from any surface. */
public final class OverlayControl {

    private OverlayControl() {}

    public static void setEnabled(Context context, boolean enabled) {
        Context ctx = context.getApplicationContext();
        Prefs.setOverlayEnabled(ctx, enabled);
        if (enabled) {
            OverlayService.refresh(ctx);
        } else {
            OverlayService.stop(ctx);
        }
        syncSurfaces(ctx);
    }

    public static void toggle(Context context) {
        setEnabled(context, !Prefs.isOverlayEnabled(context));
    }

    /** Refresh widget + pinned notification after any settings change. */
    public static void syncSurfaces(Context context) {
        Context ctx = context.getApplicationContext();
        ToggleWidget.updateAll(ctx);
        PinnedNotification.refresh(ctx);
    }
}
