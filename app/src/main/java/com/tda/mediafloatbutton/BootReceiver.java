package com.tda.mediafloatbutton;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        OverlayService.refresh(context);
        OverlayControl.syncSurfaces(context);
    }
}
