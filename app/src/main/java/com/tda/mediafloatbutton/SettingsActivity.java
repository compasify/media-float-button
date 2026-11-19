package com.tda.mediafloatbutton;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.LocaleList;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.os.LocaleListCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

    private SwitchMaterial overlaySwitch;
    private SwitchMaterial pinnedNotificationSwitch;
    private TextView previewLabel;
    private CheckBox playPauseCheck;
    private CheckBox rewindBigCheck, rewindSmallCheck, forwardSmallCheck, forwardBigCheck;
    private EditText rewindBigSeconds, rewindSmallSeconds, forwardSmallSeconds, forwardBigSeconds;
    private SeekBar opacitySeekBar;
    private SeekBar sizeSeekBar;
    private TextView opacityValue;
    private TextView sizeValue;
    private TextView overlayStatus;
    private TextView accessibilityStatus;
    private TextView notificationStatus;
    private TextView sessionStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        overlaySwitch = findViewById(R.id.overlaySwitch);
        pinnedNotificationSwitch = findViewById(R.id.pinnedNotificationSwitch);
        bindLanguageControls();
        previewLabel = findViewById(R.id.previewLabel);
        playPauseCheck = findViewById(R.id.playPauseCheck);
        rewindBigCheck = findViewById(R.id.rewindBigCheck);
        rewindSmallCheck = findViewById(R.id.rewindSmallCheck);
        forwardSmallCheck = findViewById(R.id.forwardSmallCheck);
        forwardBigCheck = findViewById(R.id.forwardBigCheck);
        rewindBigSeconds = findViewById(R.id.rewindBigSeconds);
        rewindSmallSeconds = findViewById(R.id.rewindSmallSeconds);
        forwardSmallSeconds = findViewById(R.id.forwardSmallSeconds);
        forwardBigSeconds = findViewById(R.id.forwardBigSeconds);
        opacitySeekBar = findViewById(R.id.opacitySeekBar);
        sizeSeekBar = findViewById(R.id.sizeSeekBar);
        opacityValue = findViewById(R.id.opacityValue);
        sizeValue = findViewById(R.id.sizeValue);
        overlayStatus = findViewById(R.id.overlayStatus);
        accessibilityStatus = findViewById(R.id.accessibilityStatus);
        notificationStatus = findViewById(R.id.notificationStatus);
        sessionStatus = findViewById(R.id.sessionStatus);

        opacitySeekBar.setMax(100 - Prefs.MIN_OPACITY);
        opacitySeekBar.setProgress(Prefs.getOpacityPercent(this) - Prefs.MIN_OPACITY);
        sizeSeekBar.setMax(56);
        sizeSeekBar.setProgress(Prefs.getButtonSizeDp(this) - 40);
        overlaySwitch.setChecked(Prefs.isOverlayEnabled(this));
        updateSliderLabels();

        pinnedNotificationSwitch.setChecked(Prefs.isPinnedNotificationShown(this));
        pinnedNotificationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Prefs.setPinnedNotificationShown(this, isChecked);
            OverlayControl.syncSurfaces(this);
        });

        overlaySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Prefs.setOverlayEnabled(this, isChecked);
            if (isChecked) OverlayService.refresh(this); else OverlayService.stop(this);
            OverlayControl.syncSurfaces(this);
            updateStatuses();
        });

        playPauseCheck.setChecked(Prefs.isPlayPauseEnabled(this));
        playPauseCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked && Prefs.countVisibleButtons(this) == 1) {
                Toast.makeText(this, R.string.at_least_one_button, Toast.LENGTH_SHORT).show();
                playPauseCheck.setChecked(true);
                return;
            }
            Prefs.setPlayPauseEnabled(this, isChecked);
            updatePreview();
            OverlayService.refresh(this);
        });
        bindSlot(Prefs.Slot.REWIND_BIG, rewindBigCheck, rewindBigSeconds);
        bindSlot(Prefs.Slot.REWIND_SMALL, rewindSmallCheck, rewindSmallSeconds);
        bindSlot(Prefs.Slot.FORWARD_SMALL, forwardSmallCheck, forwardSmallSeconds);
        bindSlot(Prefs.Slot.FORWARD_BIG, forwardBigCheck, forwardBigSeconds);
        updatePreview();

        opacitySeekBar.setOnSeekBarChangeListener(simpleListener(() -> {
            Prefs.setOpacityPercent(this, opacitySeekBar.getProgress() + Prefs.MIN_OPACITY);
            updateSliderLabels();
            OverlayService.refresh(this);
        }));
        sizeSeekBar.setOnSeekBarChangeListener(simpleListener(() -> {
            Prefs.setButtonSizeDp(this, sizeSeekBar.getProgress() + 40);
            updateSliderLabels();
            OverlayService.refresh(this);
        }));

        Button overlayPermissionButton = findViewById(R.id.overlayPermissionButton);
        overlayPermissionButton.setOnClickListener(v -> startActivity(new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()))));

        Button accessibilityButton = findViewById(R.id.accessibilityButton);
        accessibilityButton.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        Button notificationButton = findViewById(R.id.notificationButton);
        notificationButton.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        findViewById(R.id.testRewindBigButton).setOnClickListener(v -> { saveAllSlots(); testSeek(Prefs.Slot.REWIND_BIG); });
        findViewById(R.id.testRewindSmallButton).setOnClickListener(v -> { saveAllSlots(); testSeek(Prefs.Slot.REWIND_SMALL); });
        findViewById(R.id.testPlayPauseButton).setOnClickListener(v -> {
            saveAllSlots();
            PlayPauseRouter.toggle(this, (handled, nowPlaying) -> sessionStatus.setText(
                    handled ? getString(R.string.session_test_ok, "play/pause")
                            : getString(R.string.session_test_fail)));
        });
        findViewById(R.id.testForwardSmallButton).setOnClickListener(v -> { saveAllSlots(); testSeek(Prefs.Slot.FORWARD_SMALL); });
        findViewById(R.id.testForwardBigButton).setOnClickListener(v -> { saveAllSlots(); testSeek(Prefs.Slot.FORWARD_BIG); });
    }

    // ---- Language picker (system default / 8 languages) ----

    private static final String[] LANGUAGE_TAGS = {
            Prefs.LANGUAGE_SYSTEM, "vi", "en", "zh", "ko", "ru", "de", "fr", "es"};
    private static final int[] LANGUAGE_NAME_RES = {
            R.string.language_system, R.string.language_vi, R.string.language_en,
            R.string.language_zh, R.string.language_ko, R.string.language_ru,
            R.string.language_de, R.string.language_fr, R.string.language_es};

    private void bindLanguageControls() {
        Button button = findViewById(R.id.languageButton);
        button.setText(getString(LANGUAGE_NAME_RES[languageIndex(currentAppLanguage())]));
        button.setOnClickListener(v -> showLanguageDialog());
    }

    private void showLanguageDialog() {
        String[] names = new String[LANGUAGE_NAME_RES.length];
        for (int i = 0; i < names.length; i++) names[i] = getString(LANGUAGE_NAME_RES[i]);
        new AlertDialog.Builder(this)
                .setTitle(R.string.language_label)
                .setSingleChoiceItems(names, languageIndex(currentAppLanguage()),
                        (dialog, which) -> {
                            dialog.dismiss();
                            applyLanguage(LANGUAGE_TAGS[which]);
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void applyLanguage(String tag) {
        if (tag.equals(currentAppLanguage())) return;
        Prefs.setAppLanguage(this, tag);
        LocaleListCompat locales = tag.isEmpty()
                ? LocaleListCompat.getEmptyLocaleList()
                : LocaleListCompat.forLanguageTags(tag);
        AppCompatDelegate.setApplicationLocales(locales);
        // Recreates this activity; also rebuild overlay and pinned
        // notification in the new language.
        OverlayControl.syncSurfaces(this);
        if (Prefs.isOverlayEnabled(this)) OverlayService.refresh(this);
    }

    private int languageIndex(String tag) {
        for (int i = 0; i < LANGUAGE_TAGS.length; i++) {
            if (LANGUAGE_TAGS[i].equals(tag)) return i;
        }
        return 0;
    }

    private String currentAppLanguage() {
        // Effective language currently applied (AppCompat, Android 13+ system
        // per-app language, or our stored choice as fallback). Unsupported
        // tags count as "system default" (English resources are the fallback).
        String tag = AppCompatDelegate.getApplicationLocales().toLanguageTags();
        if (tag == null || tag.isEmpty()) {
            LocaleList locales = getResources().getConfiguration().getLocales();
            tag = locales.size() > 0 ? locales.get(0).getLanguage() : "";
        }
        String primary = Locale.forLanguageTag(tag.split(",")[0].trim()).getLanguage();
        for (String supported : LANGUAGE_TAGS) {
            if (supported.equals(primary)) return primary;
        }
        return Prefs.LANGUAGE_SYSTEM;
    }

    @Override
    protected void onResume() {
        super.onResume();
        overlaySwitch.setChecked(Prefs.isOverlayEnabled(this));
        pinnedNotificationSwitch.setChecked(Prefs.isPinnedNotificationShown(this));
        OverlayControl.syncSurfaces(this);
        updateStatuses();
        if (Prefs.isOverlayEnabled(this) && Settings.canDrawOverlays(this)) {
            OverlayService.refresh(this);
        }
    }

    @Override
    protected void onPause() {
        saveAllSlots();
        super.onPause();
    }

    private void bindSlot(Prefs.Slot slot, CheckBox check, EditText seconds) {
        check.setChecked(Prefs.isSlotEnabled(this, slot));
        seconds.setText(String.valueOf(Prefs.getSlotSeconds(this, slot)));
        check.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked && Prefs.countVisibleButtons(this) == 1) {
                Toast.makeText(this, R.string.at_least_one_button, Toast.LENGTH_SHORT).show();
                check.setChecked(true);
                return;
            }
            Prefs.setSlotEnabled(this, slot, isChecked);
            updatePreview();
            OverlayService.refresh(this);
        });
        seconds.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) saveSlotSeconds(slot, seconds);
        });
    }

    private void saveSlotSeconds(Prefs.Slot slot, EditText input) {
        try {
            Prefs.setSlotSeconds(this, slot, Integer.parseInt(input.getText().toString().trim()));
        } catch (NumberFormatException ignored) {
        }
        input.setText(String.valueOf(Prefs.getSlotSeconds(this, slot)));
        updatePreview();
        OverlayService.refresh(this);
    }

    private void saveAllSlots() {
        saveSlotSeconds(Prefs.Slot.REWIND_BIG, rewindBigSeconds);
        saveSlotSeconds(Prefs.Slot.REWIND_SMALL, rewindSmallSeconds);
        saveSlotSeconds(Prefs.Slot.FORWARD_SMALL, forwardSmallSeconds);
        saveSlotSeconds(Prefs.Slot.FORWARD_BIG, forwardBigSeconds);
    }

    private void updatePreview() {
        StringBuilder sb = new StringBuilder(getString(R.string.preview_prefix));
        for (Prefs.Slot slot : Prefs.Slot.values()) {
            if (slot == Prefs.Slot.FORWARD_SMALL && Prefs.isPlayPauseEnabled(this)) {
                sb.append(" ⏯");
            }
            if (Prefs.isSlotEnabled(this, slot)) sb.append(' ').append(slot.glyph());
        }
        previewLabel.setText(sb.toString());
    }

    private void testSeek(Prefs.Slot slot) {
        SeekRouter.seekByDelta(this, Prefs.getSlotDelta(this, slot), (handled, via) -> sessionStatus.setText(
                handled ? getString(R.string.session_test_ok, via)
                        : getString(R.string.session_test_fail)));
    }

    private void updateSliderLabels() {
        opacityValue.setText(getString(R.string.percent_format, Prefs.getOpacityPercent(this)));
        sizeValue.setText(getString(R.string.dp_format, Prefs.getButtonSizeDp(this)));
    }

    private void updateStatuses() {
        overlayStatus.setText(Settings.canDrawOverlays(this)
                ? R.string.status_granted : R.string.status_missing);
        accessibilityStatus.setText(isAccessibilityEnabled()
                ? R.string.status_granted : R.string.status_missing);
        notificationStatus.setText(isNotificationListenerEnabled()
                ? R.string.status_granted : R.string.status_missing);
        String session = MediaSessionController.describeActiveSession(this);
        sessionStatus.setText(session != null
                ? getString(R.string.session_found, session)
                : getString(R.string.session_none));
    }

    private boolean isAccessibilityEnabled() {
        AccessibilityManager manager =
                (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        ComponentName expected = new ComponentName(this, MediaAccessibilityService.class);
        List<AccessibilityServiceInfo> services =
                manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : services) {
            if (info.getResolveInfo() != null && info.getResolveInfo().serviceInfo != null) {
                ComponentName cn = new ComponentName(
                        info.getResolveInfo().serviceInfo.packageName,
                        info.getResolveInfo().serviceInfo.name);
                if (expected.equals(cn)) return true;
            }
        }
        return MediaAccessibilityService.isRunning();
    }

    private boolean isNotificationListenerEnabled() {
        Set<String> packages = NotificationManagerCompat.getEnabledListenerPackages(this);
        return packages.contains(getPackageName());
    }

    private static SeekBar.OnSeekBarChangeListener simpleListener(Runnable onChange) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) onChange.run();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
    }
}
