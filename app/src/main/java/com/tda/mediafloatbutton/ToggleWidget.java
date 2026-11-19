package com.tda.mediafloatbutton;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class ToggleWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            manager.updateAppWidget(id, buildViews(context));
        }
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, ToggleWidget.class));
        for (int id : ids) {
            manager.updateAppWidget(id, buildViews(context));
        }
    }

    private static RemoteViews buildViews(Context context) {
        boolean on = Prefs.isOverlayEnabled(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_toggle);
        views.setImageViewResource(R.id.widgetButton,
                on ? R.drawable.ic_pause : R.drawable.ic_play);
        views.setInt(R.id.widgetButton, "setBackgroundResource",
                on ? R.drawable.widget_circle_on : R.drawable.widget_circle_off);
        PendingIntent toggle = PendingIntent.getBroadcast(context, 3,
                new Intent(context, ToggleReceiver.class)
                        .setAction(ToggleReceiver.ACTION_WIDGET_TOGGLE),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        // The inner ImageButton covers the whole widget and consumes touches,
        // so the click must be registered on it as well as on the root.
        views.setOnClickPendingIntent(R.id.widgetButton, toggle);
        views.setOnClickPendingIntent(R.id.widgetRoot, toggle);
        return views;
    }
}
