package org.example.tverpatches.extension;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.os.Looper;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Decides whether a live player's toBackground() should be skipped so the
 * stream keeps running. Holds only when the app itself leaves the
 * foreground; in-app navigation (fragment switches inside a resumed
 * Activity) keeps TVer's stock pause-and-release behavior.
 *
 * Uses reflection sparingly: the extension is compiled independently from
 * the proprietary TVer APK, while the patch injects it into that APK.
 */
public final class LiveBackgroundHold {
    private static volatile boolean tracked;
    private static final Set<Activity> RESUMED =
            Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<Activity, Boolean>()));

    private LiveBackgroundHold() {}

    /**
     * Patch-injected entry point of LivePlaybackControllerView.toBackground().
     * Returns true only when the whole app is going to the background
     * (Home gesture, recents, or entering picture-in-picture).
     */
    public static boolean shouldHold(Object view) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            // Background-thread calls fall back to stock behavior.
            return false;
        }
        Activity activity = findActivity(view);
        if (activity == null) {
            return false;
        }
        ensureTracked(activity);
        if (activity.isInPictureInPictureMode()) {
            return true;
        }
        // onActivityPrePaused has already removed the pausing Activity by
        // the time fragment lifecycle observers run during PiP entry or a
        // Home gesture, so the set is empty exactly when no Activity of
        // this process remains resumed. In-app navigation keeps the host
        // Activity resumed, so the set is non-empty and stock runs.
        return RESUMED.isEmpty();
    }

    private static Activity findActivity(Object view) {
        try {
            Context context = (Context) view;
            while (!(context instanceof Activity)) {
                if (!(context instanceof ContextWrapper)) {
                    return null;
                }
                context = ((ContextWrapper) context).getBaseContext();
            }
            return (Activity) context;
        } catch (ClassCastException error) {
            return null;
        }
    }

    private static void ensureTracked(Activity activity) {
        if (tracked) {
            return;
        }
        synchronized (LiveBackgroundHold.class) {
            if (tracked) {
                return;
            }
            Application app = activity.getApplication();
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity a, Bundle savedInstanceState) {}

                @Override
                public void onActivityStarted(Activity a) {}

                @Override
                public void onActivityResumed(Activity a) {
                    RESUMED.add(a);
                }

                @Override
                public void onActivityPaused(Activity a) {}

                @Override
                public void onActivityStopped(Activity a) {
                    RESUMED.remove(a);
                }

                @Override
                public void onActivitySaveInstanceState(Activity a, Bundle outState) {}

                @Override
                public void onActivityDestroyed(Activity a) {
                    RESUMED.remove(a);
                }

                @Override
                public void onActivityPrePaused(Activity a) {
                    // Fires before onPause dispatch, so it precedes the
                    // live view's onPause observer that calls toBackground().
                    RESUMED.remove(a);
                }
            });
            tracked = true;
        }
    }
}
