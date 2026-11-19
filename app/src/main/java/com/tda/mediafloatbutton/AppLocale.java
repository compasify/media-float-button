package com.tda.mediafloatbutton;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

/**
 * Applies the in-app language choice outside Activities.
 * On Android 13+ the system per-app locale already covers every component,
 * so this only wraps contexts on older versions, using the mirrored pref.
 */
public final class AppLocale {

    private AppLocale() {}

    public static Context wrap(Context base) {
        if (Build.VERSION.SDK_INT >= 33) return base;
        String tag = Prefs.getAppLanguage(base);
        if (tag == null || tag.isEmpty()) return base;
        Locale locale = Locale.forLanguageTag(tag);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);
        config.setLocales(new LocaleList(locale));
        return base.createConfigurationContext(config);
    }
}
