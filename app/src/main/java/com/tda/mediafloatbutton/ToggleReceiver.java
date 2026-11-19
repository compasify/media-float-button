package com.tda.mediafloatbutton;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ToggleReceiver extends BroadcastReceiver {

    public static final String ACTION_WIDGET_TOGGLE =
            "com.tda.mediafloatbutton.ACTION_WIDGET_TOGGLE";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (action == null) return;
        switch (action) {
            case ACTION_WIDGET_TOGGLE:
            case PinnedNotification.ACTION_TOGGLE:
                OverlayControl.toggle(context);
                break;
            case PinnedNotification.ACTION_DISMISSED:
                // Swiped away: re-post while the Settings switch is on.
                PinnedNotification.refresh(context);
                break;
            default:
                break;
        }
    }
}
