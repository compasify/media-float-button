package com.tda.mediafloatbutton;

import android.content.Context;

import java.util.concurrent.atomic.AtomicBoolean;

public class PlayPauseRouter {

    public interface Callback {
        /** nowPlaying is null when the player does not report its state. */
        void onResult(boolean handled, Boolean nowPlaying);
    }

    private PlayPauseRouter() {}

    public static void toggle(Context context, Callback callback) {
        AtomicBoolean finished = new AtomicBoolean(false);

        MediaSessionController.togglePlayPause(context, (handled, nowPlaying) -> {
            if (handled) {
                if (finished.compareAndSet(false, true)) callback.onResult(true, nowPlaying);
                return;
            }
            MediaAccessibilityService service = MediaAccessibilityService.getInstance();
            if (service == null) {
                if (finished.compareAndSet(false, true)) callback.onResult(false, null);
                return;
            }
            service.togglePlayPause(success -> {
                if (finished.compareAndSet(false, true)) callback.onResult(success, null);
            });
        });
    }
}
