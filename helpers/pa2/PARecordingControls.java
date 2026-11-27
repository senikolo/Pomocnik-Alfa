package com.ispina.lokalnie;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Recording status and record/stop controls shared by the radio and TV players. */
public final class PARecordingControls extends LinearLayout {
    private static final int TV_CONTROLS_ID = View.generateViewId();
    private final Activity activity;
    private final boolean television;
    private String tvTitle;
    private String tvUrl;
    private String tvReferer;
    private boolean webPlayer;
    private final Button button;
    private final LinearLayout statusPanel;
    private final TextView heading;
    private final TextView details;
    private PARecordingService.Status renderedStatus;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refresh();
            postDelayed(this, 450);
        }
    };

    PARecordingControls(Activity activity, boolean television) {
        super(activity);
        this.activity = activity;
        this.television = television;
        setOrientation(VERTICAL);

        statusPanel = new LinearLayout(activity);
        statusPanel.setOrientation(VERTICAL);
        statusPanel.setPadding(dp(12), dp(8), dp(12), dp(8));
        statusPanel.setVisibility(GONE);
        heading = new TextView(activity);
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(15);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        details = new TextView(activity);
        details.setTextColor(Color.WHITE);
        details.setTextSize(14);
        statusPanel.addView(heading);
        statusPanel.addView(details);
        addView(statusPanel, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        button = new Button(activity);
        button.setAllCaps(false);
        button.setOnClickListener(v -> toggle());
        addView(button, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        refresh();
    }

    public static void attachTv(Activity activity, FrameLayout frame) {
        if (frame == null || !(frame.getParent() instanceof LinearLayout)) return;
        LinearLayout card = (LinearLayout) frame.getParent();
        PARecordingControls control = new PARecordingControls(activity, true);
        control.setId(TV_CONTROLS_ID);
        card.addView(control, card.indexOfChild(frame) + 1,
            new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public static void setTvSource(Activity activity, String title, String url, String referer) {
        PARecordingControls control = activity.findViewById(TV_CONTROLS_ID);
        if (control == null) return;
        control.tvTitle = title;
        control.tvUrl = url;
        control.tvReferer = referer;
        control.webPlayer = false;
    }

    public static void clearTvSource(Activity activity) {
        setTvSource(activity, null, null, null);
    }

    static void onWebChannel(Activity activity) {
        clearTvSource(activity);
        PARecordingControls control = activity.findViewById(TV_CONTROLS_ID);
        if (control != null) control.webPlayer = true;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ticker.run();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(ticker);
        super.onDetachedFromWindow();
    }

    private void refresh() {
        PARecordingService.Status current = PARecordingService.status;
        if (current == renderedStatus) return;
        PARecordingService.Status previous = renderedStatus;
        renderedStatus = current;
        PARecordingService.State state = current.state;
        statusPanel.setVisibility(state == PARecordingService.State.IDLE ? GONE : VISIBLE);
        if (previous == null || previous.state != state) {
            GradientDrawable background = new GradientDrawable();
            background.setColor(state == PARecordingService.State.RECORDING ? 0xFF701F28 : 0xFF263344);
            background.setCornerRadius(dp(10));
            statusPanel.setBackground(background);
        }
        if (state != PARecordingService.State.IDLE) {
            String title = current.heading();
            if (!title.contentEquals(heading.getText())) heading.setText(title);
            String data = current.details();
            if (!data.contentEquals(details.getText())) details.setText(data);
            details.setVisibility(data.isEmpty() ? GONE : VISIBLE);
        }
        button.setEnabled(state != PARecordingService.State.STOPPING);
        String label;
        switch (state) {
            case CONNECTING: label = "Anuluj"; break;
            case RECORDING: label = "■ Zakończ nagranie"; break;
            case STOPPING: label = "Zapisywanie nagrania…"; break;
            default: label = television ? "● Nagraj telewizję" : "● Nagraj radio";
        }
        if (!label.contentEquals(button.getText())) button.setText(label);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toggle() {
        try {
            if (PARecordingService.status.active()) {
                PARecordingService.stop(activity);
                return;
            }
            String title = tvTitle;
            String url = tvUrl;
            if (!television) {
                SharedPreferences radio = activity.getSharedPreferences("radio_state", Activity.MODE_PRIVATE);
                title = radio.getString("name", "");
                url = radio.getString("url", "");
                if (!radio.getBoolean("playing", false)) url = "";
            }
            if (url == null || url.isEmpty() || title == null || title.isEmpty()) {
                String message;
                if (webPlayer) message = "Kanał otwiera się na stronie nadawcy. Nagrywanie wymaga bezpośredniej transmisji.";
                else message = television ? "Najpierw wybierz kanał i poczekaj na transmisję."
                    : "Najpierw włącz stację radiową.";
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                return;
            }
            PARecordingService.start(activity, title, url, tvReferer, television);
            SharedPreferences settings = activity.getSharedPreferences("pa_recording", Activity.MODE_PRIVATE);
            if (Build.VERSION.SDK_INT >= 33 && !settings.getBoolean("asked_notifications", false)
                && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                settings.edit().putBoolean("asked_notifications", true).apply();
                activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 271);
            }
        } catch (RuntimeException error) {
            Toast.makeText(activity, "Nie udało się uruchomić sterowania nagrywaniem.", Toast.LENGTH_LONG).show();
        }
    }
}
