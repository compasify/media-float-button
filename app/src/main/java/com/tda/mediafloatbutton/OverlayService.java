package com.tda.mediafloatbutton;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

public class OverlayService extends Service {

    public static final String ACTION_REFRESH = "com.tda.mediafloatbutton.REFRESH";
    public static final String ACTION_STOP = "com.tda.mediafloatbutton.STOP";
    private static final String CHANNEL_ID = "overlay";
    private static final int NOTIFICATION_ID = 1;

    private WindowManager windowManager;
    private LinearLayout overlayView;
    private WindowManager.LayoutParams layoutParams;
    private DragListener dragListener;
    private ImageButton playPauseButton;
    private boolean assumedPlaying = true;
    private ImageView closeTarget;
    private boolean closeArmed;

    public static void refresh(Context context) {
        if (!Prefs.isOverlayEnabled(context)) return;
        if (!Settings.canDrawOverlays(context)) return;
        Intent intent = new Intent(context, OverlayService.class).setAction(ACTION_REFRESH);
        context.startForegroundService(intent);
    }

    public static void stop(Context context) {
        context.startService(new Intent(context, OverlayService.class).setAction(ACTION_STOP));
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLocale.wrap(newBase));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        startForegroundWithNotification();
        showOverlay();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action) || !Prefs.isOverlayEnabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_REFRESH.equals(action)) {
            applySettings();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        hideCloseTarget();
        removeOverlay();
        super.onDestroy();
    }

    private void startForegroundWithNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW));

        Intent openIntent = new Intent(this, SettingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_chevron_double_right)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, builder.build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, builder.build());
        }
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private ImageButton createRoundButton(int iconRes, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(iconRes);
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        button.setContentDescription(description);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xFF1F2937);
        bg.setStroke(dp(1), 0xFFFFFFFF);
        button.setBackground(bg);
        button.setClipToOutline(false);
        // No elevation shadow: it was clipped by the overlay window and made
        // the circle bottoms look cut off.
        button.setElevation(0);
        button.setStateListAnimator(null);
        button.setOnTouchListener(dragListener);
        return button;
    }

    private static int iconFor(Prefs.Slot slot) {
        switch (slot) {
            case REWIND_BIG: return R.drawable.ic_chevron_double_left;
            case REWIND_SMALL: return R.drawable.ic_chevron_left;
            case FORWARD_SMALL: return R.drawable.ic_chevron_right;
            default: return R.drawable.ic_chevron_double_right;
        }
    }

    private void doSeek(Prefs.Slot slot) {
        SeekRouter.seekByDelta(this, Prefs.getSlotDelta(this, slot), (handled, via) -> {
            if (!handled) {
                Toast.makeText(this, R.string.seek_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void doPlayPause() {
        // Optimistic icon flip; corrected afterwards when the player reports state.
        assumedPlaying = !assumedPlaying;
        updatePlayPauseIcon();
        PlayPauseRouter.toggle(this, (handled, nowPlaying) -> {
            if (!handled) {
                Toast.makeText(this, R.string.play_pause_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            if (nowPlaying != null) {
                assumedPlaying = nowPlaying;
            } else {
                Boolean reported = MediaSessionController.currentPlayingState(this);
                if (reported != null) assumedPlaying = reported;
            }
            updatePlayPauseIcon();
        });
    }

    private void updatePlayPauseIcon() {
        if (playPauseButton == null) return;
        playPauseButton.setImageResource(assumedPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        playPauseButton.setContentDescription(getString(
                assumedPlaying ? R.string.pause_desc : R.string.play_desc));
    }

    private void showOverlay() {
        if (overlayView != null) {
            applySettings();
            return;
        }
        overlayView = new LinearLayout(this);
        overlayView.setOrientation(LinearLayout.HORIZONTAL);
        // Room for elevation shadows so circle bottoms are not clipped.
        int pad = dp(10);
        overlayView.setPadding(pad, pad, pad, pad);

        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        layoutParams.gravity = Gravity.BOTTOM | Gravity.END;
        layoutParams.x = dp(16);
        layoutParams.y = dp(96);
        dragListener = new DragListener(layoutParams);

        rebuildButtons();
        try {
            windowManager.addView(overlayView, layoutParams);
        } catch (Exception e) {
            overlayView = null;
            stopSelf();
        }
    }

    private void applySettings() {
        if (overlayView == null) {
            showOverlay();
            return;
        }
        rebuildButtons();
        windowManager.updateViewLayout(overlayView, layoutParams);
    }

    private void rebuildButtons() {
        // Turning every button off hides the overlay. The foreground service
        // stays alive so re-enabling one in Settings restores them.
        overlayView.removeAllViews();
        playPauseButton = null;
        int size = dp(Prefs.getButtonSizeDp(this));
        int iconPad = Math.round(size * 0.24f);
        int gap = dp(8);
        float alpha = Prefs.getOpacityPercent(this) / 100f;

        // Display order: « ‹ [▶/⏸] › » — rewind, optional play/pause centre, forward.
        for (Prefs.Slot slot : Prefs.Slot.values()) {
            if (slot == Prefs.Slot.FORWARD_SMALL && Prefs.isPlayPauseEnabled(this)) {
                playPauseButton = createRoundButton(
                        assumedPlaying ? R.drawable.ic_pause : R.drawable.ic_play,
                        getString(assumedPlaying ? R.string.pause_desc : R.string.play_desc));
                playPauseButton.setOnClickListener(v -> doPlayPause());
                addOverlayButton(playPauseButton, size, iconPad, gap, alpha);
            }
            if (!Prefs.isSlotEnabled(this, slot)) continue;
            int seconds = Prefs.getSlotSeconds(this, slot);
            ImageButton button = createRoundButton(iconFor(slot), getString(
                    slot.direction < 0 ? R.string.prev_desc : R.string.next_desc, seconds));
            button.setOnClickListener(v -> doSeek(slot));
            addOverlayButton(button, size, iconPad, gap, alpha);
        }
        overlayView.setVisibility(overlayView.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    private void addOverlayButton(ImageButton button, int size, int iconPad, int gap, float alpha) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        if (overlayView.getChildCount() > 0) lp.setMarginStart(gap);
        button.setLayoutParams(lp);
        button.setPadding(iconPad, iconPad, iconPad, iconPad);
        button.setAlpha(alpha);
        overlayView.addView(button);
    }

    private void removeOverlay() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (IllegalArgumentException ignored) {
            }
            overlayView = null;
        }
    }

    // ---- Drag-to-close target (shows while dragging, bottom centre) ----

    private void showCloseTarget() {
        if (closeTarget != null) return;
        int size = dp(64);
        closeTarget = new ImageView(this);
        closeTarget.setImageResource(R.drawable.ic_close);
        closeTarget.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        closeTarget.setContentDescription(getString(R.string.close_target_desc));
        closeTarget.setPadding(dp(16), dp(16), dp(16), dp(16));
        styleCloseTarget(false);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.y = dp(28);
        try {
            windowManager.addView(closeTarget, lp);
        } catch (Exception e) {
            closeTarget = null;
        }
    }

    private void styleCloseTarget(boolean armed) {
        if (closeTarget == null) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(armed ? 0xFFEF4444 : 0xFF6B7280);
        bg.setStroke(dp(1), 0xFFFFFFFF);
        closeTarget.setBackground(bg);
        closeTarget.setScaleX(armed ? 1.2f : 1f);
        closeTarget.setScaleY(armed ? 1.2f : 1f);
    }

    private boolean isOverCloseTarget(float rawX, float rawY) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float centreX = metrics.widthPixels / 2f;
        float centreY = metrics.heightPixels - dp(28) - dp(32);
        double distance = Math.hypot(rawX - centreX, rawY - centreY);
        return distance <= dp(70);
    }

    private void hideCloseTarget() {
        closeArmed = false;
        if (closeTarget != null && windowManager != null) {
            try {
                windowManager.removeView(closeTarget);
            } catch (IllegalArgumentException ignored) {
            }
            closeTarget = null;
        }
    }

    private class DragListener implements View.OnTouchListener {
        private final WindowManager.LayoutParams params;
        private int startX, startY;
        private float startTouchX, startTouchY;
        private boolean moved;

        DragListener(WindowManager.LayoutParams params) {
            this.params = params;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = params.x;
                    startY = params.y;
                    startTouchX = event.getRawX();
                    startTouchY = event.getRawY();
                    moved = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int) (event.getRawX() - startTouchX);
                    int dy = (int) (event.getRawY() - startTouchY);
                    if (!moved && (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6))) {
                        moved = true;
                        showCloseTarget();
                    }
                    if (moved) {
                        // Gravity is BOTTOM|END, so x/y grow towards top-left.
                        params.x = startX - dx;
                        params.y = startY - dy;
                        windowManager.updateViewLayout(overlayView, params);
                        closeArmed = isOverCloseTarget(event.getRawX(), event.getRawY());
                        styleCloseTarget(closeArmed);
                    }
                    return moved;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    boolean dropOnClose = moved
                            && event.getActionMasked() == MotionEvent.ACTION_UP
                            && isOverCloseTarget(event.getRawX(), event.getRawY());
                    hideCloseTarget();
                    if (dropOnClose) {
                        moved = false;
                        Toast.makeText(OverlayService.this,
                                R.string.overlay_turned_off, Toast.LENGTH_SHORT).show();
                        OverlayControl.setEnabled(OverlayService.this, false);
                        return true;
                    }
                    return moved;
                }
                default:
                    return false;
            }
        }
    }
}
