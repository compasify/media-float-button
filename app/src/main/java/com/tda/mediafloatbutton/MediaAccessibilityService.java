package com.tda.mediafloatbutton;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class MediaAccessibilityService extends AccessibilityService {

    private static final String TAG = "MediaA11y";
    private static volatile MediaAccessibilityService instance;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static MediaAccessibilityService getInstance() {
        return instance;
    }

    public static boolean isRunning() {
        return instance != null;
    }

    public interface SeekCallback {
        void onResult(boolean success);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        OverlayService.refresh(this);
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // No-op: we only read the window tree on demand when a button is pressed.
    }

    @Override
    public void onInterrupt() {
    }

    /**
     * Play/pause fallbacks for players without a media session:
     * 1) click a visible play/pause button in the page,
     * 2) system media play/pause key,
     * 3) tap the video area (near the timeline thumb, away from centre
     *    double-tap zones) because a centre tap toggles most HTML5 players.
     */
    public void togglePlayPause(SeekCallback callback) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            AccessibilityNodeInfo playPause = findPlayPauseButton(root);
            if (playPause != null
                    && playPause.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                callback.onResult(true);
                return;
            }
        }
        if (root == null) {
            // No readable window: only the system media key is left. Never
            // combine it with a tap below, or a responsive player would
            // toggle twice (pause then immediately play again).
            AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (audio == null) {
                callback.onResult(false);
                return;
            }
            long now = SystemClock.uptimeMillis();
            audio.dispatchMediaKeyEvent(new KeyEvent(
                    now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
            audio.dispatchMediaKeyEvent(new KeyEvent(
                    now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
            callback.onResult(true);
            return;
        }
        Rect tapBounds = videoTapBounds(root);
        tap(tapBounds.centerX(), tapBounds.centerY(), callback::onResult);
    }

    /** The big centred playback surface when the page exposes one, else window. */
    private static Rect videoTapBounds(AccessibilityNodeInfo root) {
        Rect window = new Rect();
        root.getBoundsInScreen(window);
        Rect best = null;
        long bestArea = 0;
        java.util.ArrayDeque<AccessibilityNodeInfo> stack = new java.util.ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            AccessibilityNodeInfo node = stack.pop();
            if (node == null) continue;
            CharSequence desc = node.getContentDescription();
            if (desc != null) {
                String text = desc.toString().toLowerCase(Locale.ROOT);
                if (text.contains("video") || text.contains("trình phát")
                        || text.contains("media player")) {
                    Rect bounds = new Rect();
                    node.getBoundsInScreen(bounds);
                    long area = (long) bounds.width() * bounds.height();
                    if (bounds.width() > window.width() / 3 && area > bestArea) {
                        bestArea = area;
                        best = bounds;
                    }
                }
            }
            for (int i = node.getChildCount() - 1; i >= 0; i--) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) stack.push(child);
            }
        }
        return best != null ? best : window;
    }

    static AccessibilityNodeInfo findPlayPauseButton(AccessibilityNodeInfo root) {
        java.util.ArrayDeque<AccessibilityNodeInfo> stack = new java.util.ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            AccessibilityNodeInfo node = stack.pop();
            if (node == null) continue;
            String label = ((node.getContentDescription() != null
                    ? node.getContentDescription().toString() : "") + " "
                    + (node.getText() != null ? node.getText().toString() : ""))
                    .toLowerCase(Locale.ROOT).trim();
            boolean looksLikeToggle = label.equals("play") || label.equals("pause")
                    || label.equals("phát") || label.equals("tạm dừng")
                    || label.startsWith("play ") || label.startsWith("pause ")
                    || label.startsWith("phát ") || label.startsWith("tạm dừng ");
            boolean isSeek = label.contains("seek") || label.contains("tua")
                    || label.contains("tiếp theo") || label.contains("trước");
            if (node.isVisibleToUser() && looksLikeToggle && !isSeek
                    && (node.isClickable()
                    || hasAction(node, AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK))) {
                return node;
            }
            for (int i = node.getChildCount() - 1; i >= 0; i--) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) stack.push(child);
            }
        }
        return null;
    }

    private static boolean hasAction(AccessibilityNodeInfo node,
                                     AccessibilityNodeInfo.AccessibilityAction action) {
        for (AccessibilityNodeInfo.AccessibilityAction available : node.getActionList()) {
            if (available.getId() == action.getId()) return true;
        }
        return false;
    }

    public void seekBySeconds(int deltaSeconds, SeekCallback callback) {
        if (deltaSeconds == 0) {
            callback.onResult(false);
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            callback.onResult(false);
            return;
        }
        SeekBarInfo info = findVideoSeekBar(root);
        if (info == null) {
            // Controls may be hidden (fullscreen auto-hide). Tap video center to reveal, then retry.
            Rect window = new Rect();
            root.getBoundsInScreen(window);
            tap(window.centerX(), window.centerY(), ok -> {
                if (!ok) {
                    callback.onResult(false);
                    return;
                }
                mainHandler.postDelayed(() -> {
                    AccessibilityNodeInfo retryRoot = getRootInActiveWindow();
                    SeekBarInfo retry = retryRoot == null ? null : findVideoSeekBar(retryRoot);
                    if (retry == null) {
                        callback.onResult(false);
                    } else {
                        performSeekSwipe(retry, deltaSeconds, callback);
                    }
                }, 500);
            });
            return;
        }
        performSeekSwipe(info, deltaSeconds, callback);
    }

    private void performSeekSwipe(SeekBarInfo info, int deltaSeconds, SeekCallback callback) {
        if (info.durationSeconds <= 0 || info.widthPx <= 0) {
            callback.onResult(false);
            return;
        }
        double targetRatio = (info.currentSeconds + deltaSeconds) / (double) info.durationSeconds;
        if (targetRatio < 0) targetRatio = 0;
        if (targetRatio > 1) targetRatio = 1;
        int startX = info.bounds.left + (int) (info.bounds.width() * info.currentRatio);
        int endX = info.bounds.left + (int) (info.bounds.width() * targetRatio);
        int y = info.bounds.centerY();
        if (Math.abs(endX - startX) < 2) {
            // Very long video: a tiny swipe may be ignored by the player.
            // Fall back to tapping the target point on the timeline.
            tap(endX, y, callback::onResult);
            return;
        }
        Path path = new Path();
        path.moveTo(startX, y);
        path.lineTo(endX, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 220))
                .build();
        AtomicBoolean done = new AtomicBoolean(false);
        boolean dispatched = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                if (done.compareAndSet(false, true)) callback.onResult(true);
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                if (done.compareAndSet(false, true)) callback.onResult(false);
            }
        }, mainHandler);
        if (!dispatched && done.compareAndSet(false, true)) {
            callback.onResult(false);
        }
    }

    private void tap(int x, int y, SeekCallback callback) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 60))
                .build();
        AtomicBoolean done = new AtomicBoolean(false);
        boolean dispatched = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                if (done.compareAndSet(false, true)) callback.onResult(true);
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                if (done.compareAndSet(false, true)) callback.onResult(false);
            }
        }, mainHandler);
        if (!dispatched && done.compareAndSet(false, true)) {
            callback.onResult(false);
        }
    }

    static final class SeekBarInfo {
        Rect bounds;
        int widthPx;
        long durationSeconds;
        double currentRatio;
        long currentSeconds;
    }

    static SeekBarInfo findVideoSeekBar(AccessibilityNodeInfo node) {
        if (node == null) return null;
        SeekBarInfo best = null;
        java.util.ArrayDeque<AccessibilityNodeInfo> stack = new java.util.ArrayDeque<>();
        stack.push(node);
        while (!stack.isEmpty()) {
            AccessibilityNodeInfo current = stack.pop();
            if (current == null) continue;
            if ("android.widget.SeekBar".equals(current.getClassName() != null ? current.getClassName().toString() : "")
                    || nodeRangeInfoLooksUsable(current)) {
                SeekBarInfo candidate = toSeekBarInfo(current);
                if (candidate != null && (best == null || candidate.widthPx > best.widthPx)) {
                    best = candidate;
                }
            }
            for (int i = current.getChildCount() - 1; i >= 0; i--) {
                AccessibilityNodeInfo child = current.getChild(i);
                if (child != null) stack.push(child);
            }
        }
        return best;
    }

    private static boolean nodeRangeInfoLooksUsable(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo.RangeInfo range = node.getRangeInfo();
        return range != null && range.getMax() > range.getMin();
    }

    private static SeekBarInfo toSeekBarInfo(AccessibilityNodeInfo node) {
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (bounds.width() < 100) return null;

        long duration = parseDurationSeconds(node.getContentDescription());
        if (duration <= 0) duration = parseDurationSeconds(node.getStateDescription());
        if (duration <= 0) duration = parseDurationSeconds(node.getText());

        AccessibilityNodeInfo.RangeInfo range = node.getRangeInfo();
        double ratio;
        long currentSeconds;
        if (range != null && range.getMax() > range.getMin()) {
            ratio = (range.getCurrent() - range.getMin()) / (double) (range.getMax() - range.getMin());
            if (duration <= 0 && range.getType() == AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT) {
                // Some players expose seconds directly as the range.
                duration = Math.round(range.getMax());
                currentSeconds = Math.round(range.getCurrent());
                SeekBarInfo info = new SeekBarInfo();
                info.bounds = bounds;
                info.widthPx = bounds.width();
                info.durationSeconds = duration;
                info.currentRatio = clamp01(ratio);
                info.currentSeconds = currentSeconds;
                return info.durationSeconds > 0 ? info : null;
            }
            if (duration <= 0) return null;
            currentSeconds = Math.round(ratio * duration);
        } else {
            // Chromium on Android may expose the timeline as a SeekBar without
            // RangeInfo. In that case we cannot know the current position, so
            // this node is not precise enough; skip it.
            return null;
        }

        SeekBarInfo info = new SeekBarInfo();
        info.bounds = bounds;
        info.widthPx = bounds.width();
        info.durationSeconds = duration;
        info.currentRatio = clamp01(ratio);
        info.currentSeconds = currentSeconds;
        return info;
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    /**
     * Extracts a duration like "0:05", "12:34" or "1:02:03" from labels such as
     * "tổng thời gian: 0:05", "total time: 12:34" or a bare timestamp.
     * Returns 0 when nothing usable is found.
     */
    static long parseDurationSeconds(CharSequence text) {
        if (text == null) return 0;
        String s = text.toString().toLowerCase(Locale.ROOT);
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d{1,2}):([0-5]\\d)(?::([0-5]\\d))?")
                .matcher(s);
        long last = 0;
        while (m.find()) {
            try {
                long a = Long.parseLong(m.group(1));
                long b = Long.parseLong(m.group(2));
                String third = m.group(3);
                last = third != null ? a * 3600 + b * 60 + Long.parseLong(third) : a * 60 + b;
            } catch (NumberFormatException ignored) {
                // keep scanning
            }
        }
        return last;
    }
}
