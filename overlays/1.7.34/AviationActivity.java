package com.ispina.lokalnie;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.ispina.lokalnie.radio.RadioService;

public class AviationActivity extends ThemedActivity {
    private static final String EPKK_NAME = "Kraków Airport EPKK • Tower / Approach";
    private static final String EPKK_URL = "https://d.liveatc.net/epkk_app";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private Button playButton;

    private final Runnable stateTick = new Runnable() {
        @Override public void run() {
            refreshState();
            handler.postDelayed(this, 1200);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        startListening();
        handler.post(stateTick);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(NativeUi.bg(this));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(NativeUi.dp(this,14), NativeUi.dp(this,14), NativeUi.dp(this,14), NativeUi.dp(this,28));
        scroll.addView(root);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        Button back = NativeUi.button(this,"‹",true);
        back.setTextSize(24);
        back.setOnClickListener(v -> finish());
        head.addView(back,new LinearLayout.LayoutParams(NativeUi.dp(this,48),NativeUi.dp(this,46)));
        TextView title = NativeUi.text(this,"Lotnictwo · Kraków",24,true);
        title.setPadding(NativeUi.dp(this,12),0,0,0);
        head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(head);

        LinearLayout live = NativeUi.card(this);
        live.addView(NativeUi.text(this,"✈️ Nasłuch Kraków Airport / EPKK",19,true));
        NativeUi.addSpacer(live,this,5);
        live.addView(NativeUi.muted(this,
                "Bez przeglądarki i bez myTuner. Pomocnik łączy się bezpośrednio z feedem EPKK Tower / Approach.",13));
        NativeUi.addSpacer(live,this,12);

        status = NativeUi.muted(this,"Łączenie z EPKK…",14);
        live.addView(status);
        NativeUi.addSpacer(live,this,10);

        playButton = NativeUi.button(this,"▶ Włącz nasłuch EPKK",false);
        playButton.setOnClickListener(v -> startListening());
        live.addView(playButton,new LinearLayout.LayoutParams(-1,NativeUi.dp(this,52)));
        NativeUi.addSpacer(live,this,8);

        Button stop = NativeUi.button(this,"■ Zatrzymaj nasłuch",true);
        stop.setOnClickListener(v -> stopListening());
        live.addView(stop,new LinearLayout.LayoutParams(-1,NativeUi.dp(this,48)));

        NativeUi.addSpacer(live,this,9);
        live.addView(NativeUi.muted(this,
                "Nasłuch uruchamia się automatycznie po wejściu z kafelka. Może grać dalej po wyjściu z tego ekranu, a sterowanie jest dostępne także z powiadomienia. W eterze bywają przerwy ciszy między transmisjami.",12));
        root.addView(live);

        LinearLayout freq = NativeUi.card(this);
        freq.addView(NativeUi.text(this,"Częstotliwości EPKK",18,true));
        TextView f = NativeUi.text(this,
                "Ground: 118.105 MHz\n" +
                "Tower: 123.255 MHz\n" +
                "Approach: 121.075 MHz\n" +
                "Approach: 126.975 MHz\n" +
                "Director / Approach: 126.530 MHz\n" +
                "Delivery: 121.980 MHz\n" +
                "ATIS: 126.130 MHz",14,false);
        f.setLineSpacing(0,1.18f);
        NativeUi.addSpacer(freq,this,7);
        freq.addView(f);
        root.addView(freq);

        LinearLayout note = NativeUi.card(this);
        note.addView(NativeUi.text(this,"Jak działa",17,true));
        NativeUi.addSpacer(note,this,4);
        note.addView(NativeUi.muted(this,
                "Pomocnik Alfa odtwarza bezpośredni publiczny feed internetowy EPKK Twr/App jako strumień MP3. Nie otwiera strony WWW i nie używa ukrytego WebView.",12));
        root.addView(note);

        setContentView(scroll);
    }

    private void startListening() {
        if (status != null) status.setText("Łączenie z Kraków-Balice EPKK…");
        if (playButton != null) playButton.setText("⏳ Łączenie…");
        Intent i = new Intent(this, RadioService.class)
                .setAction(RadioService.ACTION_PLAY)
                .putExtra("name", EPKK_NAME)
                .putExtra("url", EPKK_URL);
        try {
            ContextCompat.startForegroundService(this, i);
        } catch (Throwable e) {
            if (status != null) status.setText("Nie udało się uruchomić nasłuchu: " + safeMessage(e));
            if (playButton != null) playButton.setText("▶ Spróbuj ponownie");
        }
    }

    private void stopListening() {
        try {
            startService(new Intent(this, RadioService.class).setAction(RadioService.ACTION_STOP));
        } catch (Throwable ignored) {}
        if (status != null) status.setText("Nasłuch EPKK zatrzymany");
        if (playButton != null) playButton.setText("▶ Włącz nasłuch EPKK");
    }

    private void refreshState() {
        SharedPreferences p = getSharedPreferences("radio_state", MODE_PRIVATE);
        String name = p.getString("name", "");
        if (!EPKK_NAME.equals(name)) return;
        boolean playing = p.getBoolean("playing", false);
        boolean desired = p.getBoolean("desired", false);
        String error = p.getString("error", "");
        if (playing) {
            if (status != null) status.setText("🔴 EPKK Twr/App • nasłuch działa");
            if (playButton != null) playButton.setText("▶ Nasłuch działa");
        } else if (error != null && !error.trim().isEmpty()) {
            if (status != null) status.setText("Błąd połączenia EPKK. Dotknij „Włącz nasłuch”, aby spróbować ponownie.");
            if (playButton != null) playButton.setText("▶ Spróbuj ponownie");
        } else if (desired) {
            if (status != null) status.setText("Łączenie ze strumieniem EPKK Twr/App…");
            if (playButton != null) playButton.setText("⏳ Łączenie…");
        }
    }

    private static String safeMessage(Throwable e) {
        String m = e == null ? "" : e.getMessage();
        return (m == null || m.trim().isEmpty()) ? "błąd systemu audio" : m;
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
