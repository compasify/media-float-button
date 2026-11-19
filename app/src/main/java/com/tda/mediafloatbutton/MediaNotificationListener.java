package com.tda.mediafloatbutton;

import android.service.notification.NotificationListenerService;

public class MediaNotificationListener extends NotificationListenerService {
    @Override
    public void onListenerConnected() {
        OverlayService.refresh(this);
    }
}
