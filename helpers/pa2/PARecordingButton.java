package com.ispina.lokalnie;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

/** Shared record/stop control for both native players. */
public final class PARecordingButton extends Button {
    private static final int TV_BUTTON_ID = View.generateViewId();
    private final Activity activity;
    private final boolean television;
    private String tvTitle;
    private String tvUrl;
    private String tvReferer;
    private boolean webPlayer;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refresh();
            postDelayed(this, 450);
        }
    };

    PARecordingButton(Activity activity, boolean television) {
        super(activity);
        this.activity = activity;
        this.television = television;
        setAllCaps(false);
        setOnClickListener(v -> toggle());
        refresh();
    }

    static void attachTv(Activity activity, FrameLayout frame) {
        LinearLayout card = (LinearLayout) frame.getParent();
        PARecordingButton control = new PARecordingButton(activity, true);
        control.setId(TV_BUTTON_ID);
        card.addView(control, card.indexOfChild(frame) + 1,
            new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    static void setTvSource(Activity activity, String title, String url, String referer) {
        PARecordingButton control = activity.findViewById(TV_BUTTON_ID);
        if (control == null) return;
        control.tvTitle = title;
        control.tvUrl = url;
        control.tvReferer = referer;
        control.webPlayer = false;
    }

    static void onWebChannel(Activity activity) {
        setTvSource(activity, null, null, null);
        PARecordingButton control = activity.findViewById(TV_BUTTON_ID);
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
        PARecordingService.State state = PARecordingService.state;
        setEnabled(state != PARecordingService.State.STOPPING);
        String label;
        switch (state) {
            case CONNECTING: label = "■ Anuluj rozpoczęcie nagrania"; break;
            case RECORDING: label = "■ Zakończ nagranie"; break;
            case STOPPING: label = "Zapisywanie nagrania…"; break;
            default: label = television ? "● Nagraj telewizję" : "● Nagraj radio";
        }
        if (!label.contentEquals(getText())) setText(label);
    }

    private void toggle() {
        try {
            if (PARecordingService.state != PARecordingService.State.IDLE) {
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
