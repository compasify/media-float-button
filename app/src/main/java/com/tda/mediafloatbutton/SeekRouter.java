package com.tda.mediafloatbutton;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.atomic.AtomicBoolean;

public class SeekRouter {

    public interface Callback {
        void onResult(boolean handled, String via);
    }

    private SeekRouter() {}

    /** Tries MediaSession first, then Accessibility timeline drag. */
    public static void seekByDelta(Context context, int deltaSeconds, Callback callback) {
        int delta = deltaSeconds;
        AtomicBoolean finished = new AtomicBoolean(false);
        Handler handler = new Handler(Looper.getMainLooper());

        Runnable tryAccessibility = () -> {
            MediaAccessibilityService service = MediaAccessibilityService.getInstance();
            if (service == null) {
                if (finished.compareAndSet(false, true)) callback.onResult(false, "no-accessibility");
                return;
            }
            service.seekBySeconds(delta, success -> {
                if (finished.compareAndSet(false, true)) {
                    callback.onResult(success, success ? "accessibility" : "accessibility-failed");
                }
            });
        };

        // If no media session exists, skip straight to accessibility.
        if (MediaSessionController.findBestController(context) == null) {
            tryAccessibility.run();
            return;
        }
        MediaSessionController.seek(context, delta, (handled, message) -> {
            if (handled) {
                if (finished.compareAndSet(false, true)) callback.onResult(true, "mediasession");
            } else {
                tryAccessibility.run();
            }
        });
        // Safety timeout: some players never answer a seek request.
        handler.postDelayed(() -> {
            if (!finished.get()) tryAccessibility.run();
        }, 1200);
    }
}
