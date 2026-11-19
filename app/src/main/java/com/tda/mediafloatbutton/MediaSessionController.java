package com.tda.mediafloatbutton;

import android.content.ComponentName;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.view.KeyEvent;

import java.util.List;

public class MediaSessionController {

    public interface Result {
        void onDone(boolean handled, String message);
    }

    private MediaSessionController() {}

    /**
     * Tier 1: seek through the active Android media session when the player
     * publishes a position and supports ACTION_SEEK_TO.
     * Tier 3 fallback: dispatch rewind / fast-forward media keys (amount is
     * chosen by the target player, usually not configurable).
     */
    public static void seek(Context context, int deltaSeconds, Result result) {
        MediaController controller = findBestController(context);
        if (controller != null) {
            PlaybackState state = controller.getPlaybackState();
            MediaMetadata metadata = controller.getMetadata();
            if (state != null
                    && (state.getActions() & PlaybackState.ACTION_SEEK_TO) != 0
                    && state.getPosition() != PlaybackState.PLAYBACK_POSITION_UNKNOWN) {
                long positionMs = state.getPosition();
                if (state.getState() == PlaybackState.STATE_PLAYING
                        || state.getState() == PlaybackState.STATE_BUFFERING) {
                    long elapsed = SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime();
                    if (elapsed > 0) positionMs += (long) (elapsed * state.getPlaybackSpeed());
                }
                long target = positionMs + deltaSeconds * 1000L;
                if (target < 0) target = 0;
                if (metadata != null) {
                    long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
                    if (duration > 0 && target > duration) target = duration;
                }
                try {
                    controller.getTransportControls().seekTo(target);
                    result.onDone(true, null);
                } catch (Exception e) {
                    dispatchMediaKey(context, controller, deltaSeconds, result);
                }
                return;
            }
            dispatchMediaKey(context, controller, deltaSeconds, result);
            return;
        }
        result.onDone(false, null);
    }

    /**
     * Play/pause through the active media session. Returns the resulting
     * playing state when known (null = unknown, assume toggled).
     */
    public interface PlayPauseResult {
        void onDone(boolean handled, Boolean nowPlaying);
    }

    public static void togglePlayPause(Context context, PlayPauseResult result) {
        MediaController controller = findBestController(context);
        if (controller == null) {
            result.onDone(false, null);
            return;
        }
        PlaybackState state = controller.getPlaybackState();
        Boolean nowPlaying = null;
        try {
            if (state != null && (state.getState() == PlaybackState.STATE_PLAYING
                    || state.getState() == PlaybackState.STATE_BUFFERING)) {
                controller.getTransportControls().pause();
                nowPlaying = Boolean.FALSE;
            } else if (state != null && state.getState() == PlaybackState.STATE_PAUSED) {
                controller.getTransportControls().play();
                nowPlaying = Boolean.TRUE;
            } else {
                long now = SystemClock.uptimeMillis();
                controller.dispatchMediaButtonEvent(new KeyEvent(
                        now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
                controller.dispatchMediaButtonEvent(new KeyEvent(
                        now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
            }
            result.onDone(true, nowPlaying);
        } catch (Exception e) {
            result.onDone(false, null);
        }
    }

    /** True/false when the active session reports a state, otherwise null. */
    public static Boolean currentPlayingState(Context context) {
        MediaController controller = findBestController(context);
        if (controller == null) return null;
        PlaybackState state = controller.getPlaybackState();
        if (state == null) return null;
        if (state.getState() == PlaybackState.STATE_PLAYING
                || state.getState() == PlaybackState.STATE_BUFFERING) return Boolean.TRUE;
        if (state.getState() == PlaybackState.STATE_PAUSED) return Boolean.FALSE;
        return null;
    }

    private static void dispatchMediaKey(Context context, MediaController controller,
                                         int deltaSeconds, Result result) {
        int keyCode = deltaSeconds < 0 ? KeyEvent.KEYCODE_MEDIA_REWIND : KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
        long now = SystemClock.uptimeMillis();
        boolean handled = false;
        try {
            handled = controller.dispatchMediaButtonEvent(
                    new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
            controller.dispatchMediaButtonEvent(
                    new KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0));
        } catch (Exception ignored) {
        }
        if (handled) {
            result.onDone(true, null);
            return;
        }
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio != null) {
            audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
            audio.dispatchMediaKeyEvent(new KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0));
            result.onDone(true, null);
        } else {
            result.onDone(false, null);
        }
    }

    public static String describeActiveSession(Context context) {
        MediaController controller = findBestController(context);
        if (controller == null) return null;
        StringBuilder sb = new StringBuilder(controller.getPackageName());
        PlaybackState state = controller.getPlaybackState();
        MediaMetadata metadata = controller.getMetadata();
        if (metadata != null) {
            String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
            if (title != null) sb.append(" • ").append(title);
            long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
            if (duration > 0) sb.append(" • ").append(duration / 1000).append("s");
        }
        if (state != null) {
            sb.append(" • ").append(stateName(state.getState()));
            if ((state.getActions() & PlaybackState.ACTION_SEEK_TO) != 0) sb.append(" • SEEK_TO");
            if ((state.getActions() & PlaybackState.ACTION_REWIND) != 0) sb.append(" • REW");
            if ((state.getActions() & PlaybackState.ACTION_FAST_FORWARD) != 0) sb.append(" • FF");
        }
        return sb.toString();
    }

    private static String stateName(int state) {
        switch (state) {
            case PlaybackState.STATE_PLAYING: return "playing";
            case PlaybackState.STATE_PAUSED: return "paused";
            case PlaybackState.STATE_BUFFERING: return "buffering";
            case PlaybackState.STATE_STOPPED: return "stopped";
            case PlaybackState.STATE_FAST_FORWARDING: return "ff";
            case PlaybackState.STATE_REWINDING: return "rew";
            default: return "state=" + state;
        }
    }

    static MediaController findBestController(Context context) {
        MediaSessionManager manager =
                (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (manager == null) return null;
        List<MediaController> controllers;
        try {
            controllers = manager.getActiveSessions(
                    new ComponentName(context, MediaNotificationListener.class));
        } catch (SecurityException | IllegalArgumentException e) {
            return null;
        }
        MediaController pausedSeekable = null;
        MediaController anyActive = null;
        for (MediaController controller : controllers) {
            PlaybackState state = controller.getPlaybackState();
            if (state == null) continue;
            if (anyActive == null) anyActive = controller;
            boolean seekable = (state.getActions() & PlaybackState.ACTION_SEEK_TO) != 0;
            if (state.getState() == PlaybackState.STATE_PLAYING
                    || state.getState() == PlaybackState.STATE_BUFFERING) {
                return controller;
            }
            if (seekable && pausedSeekable == null) pausedSeekable = controller;
        }
        return pausedSeekable != null ? pausedSeekable : anyActive;
    }
}
