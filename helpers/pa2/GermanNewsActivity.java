package com.ispina.lokalnie;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Optional daily German reading with an in-app switch, level and notification time. */
public final class GermanNewsActivity extends Activity {
    private static final String PREFS = "pa_german";
    private static final int PURPLE = 0xff6846a4;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private Switch dailySwitch;
    private Spinner levelSpinner;
    private TextView timeLabel, newsTitle, newsDate, newsText, glossary, status;
    private Button readerButton, sourceButton;
    private GermanNewsReceiver.Article currentArticle;
    private int hour, minute;
    private boolean enableAfterPermission;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        hour = prefs.getInt("hour", 8);
        minute = prefs.getInt("minute", 0);
        buildUi();
        render(GermanNewsReceiver.cached(this));
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(12), dp(18), dp(26));
        page.setBackgroundColor(color("soft", 0xfff6f3fa));
        scroll.addView(page);
        setContentView(scroll);

        TextView title = text("Deutsch · Wirtschaft", 25, color("ink", 0xff292239));
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        page.addView(title);
        page.addView(text("Krótki tekst po niemiecku, nowe słownictwo i pytanie do samodzielnej odpowiedzi.",
            14, color("muted", 0xff645e77)));

        LinearLayout settings = card();
        page.addView(settings, margins(0, 13, 0, 0));
        settings.addView(text("Ustawienia nauki", 18, color("ink", 0xff292239)));
        dailySwitch = new Switch(this);
        dailySwitch.setText("Codzienna wiadomość i powiadomienie");
        dailySwitch.setTextColor(color("ink", 0xff292239));
        dailySwitch.setChecked(prefs.getBoolean("enabled", false));
        settings.addView(dailySwitch, margins(0, 4, 0, 0));
        dailySwitch.setOnCheckedChangeListener((button, checked) -> setDailyEnabled(checked));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        settings.addView(row, margins(0, 4, 0, 0));
        levelSpinner = new Spinner(this);
        levelSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            new String[]{"B1", "B2"}));
        levelSpinner.setSelection("B2".equals(prefs.getString("level", "B1")) ? 1 : 0);
        row.addView(levelSpinner, new LinearLayout.LayoutParams(0, -2, 1));
        timeLabel = text("", 14, color("muted", 0xff645e77));
        Button timeButton = outlineButton("Godzina");
        row.addView(timeButton, new LinearLayout.LayoutParams(0, -2, 1));
        updateTimeLabel();
        timeButton.setOnClickListener(v -> chooseTime());
        levelSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String level = position == 1 ? "B2" : "B1";
                prefs.edit().putString("level", level).apply();
                render(GermanNewsReceiver.cached(GermanNewsActivity.this));
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        settings.addView(timeLabel, margins(0, 2, 0, 0));
        status = text("", 13, color("muted", 0xff645e77));
        settings.addView(status, margins(0, 3, 0, 0));

        LinearLayout article = card();
        page.addView(article, margins(0, 12, 0, 0));
        article.addView(text("Wiadomość dnia", 18, color("ink", 0xff292239)));
        newsDate = text("", 12, color("muted", 0xff645e77));
        article.addView(newsDate, margins(0, 3, 0, 0));
        newsTitle = text("Pobierz aktualną wiadomość gospodarczą.", 17, color("ink", 0xff292239));
        newsTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        article.addView(newsTitle, margins(0, 7, 0, 0));
        newsText = text("", 15, color("ink", 0xff292239));
        article.addView(newsText, margins(0, 5, 0, 0));
        glossary = text("", 13, PURPLE);
        article.addView(glossary, margins(0, 8, 0, 0));
        readerButton = outlineButton("Otwórz tekst w Czytniku Alfa");
        article.addView(readerButton, margins(0, 8, 0, 0));
        readerButton.setEnabled(false);
        readerButton.setOnClickListener(v -> openInReader());
        sourceButton = outlineButton("Otwórz pełny artykuł");
        article.addView(sourceButton, margins(0, 5, 0, 0));
        sourceButton.setEnabled(false);
        sourceButton.setOnClickListener(v -> openSource());
        Button refresh = button("Pobierz najnowszy tekst");
        article.addView(refresh, margins(0, 8, 0, 0));
        refresh.setOnClickListener(v -> refreshNews());

        LinearLayout exercise = card();
        page.addView(exercise, margins(0, 12, 0, 0));
        exercise.addView(text("Ćwiczenie", 18, color("ink", 0xff292239)));
        exercise.addView(text("Odpowiedz po niemiecku: Was ist die wichtigste Nachricht? Spróbuj streścić ją w jednym zdaniu.",
            14, color("ink", 0xff292239)), margins(0, 5, 0, 0));
        exercise.addView(text("Poziom jest orientacyjny: wersja B1 pokazuje krótszy fragment i podstawowe słownictwo, B2 udostępnia dłuższy fragment. Pełny kontekst znajduje się w źródle.",
            12, color("muted", 0xff645e77)), margins(0, 8, 0, 0));
        page.addView(text("Źródło: oficjalny kanał RSS działu Wirtschaft tagesschau.de. Lekcje i ustawienia zapisują się lokalnie na tym urządzeniu.",
            12, color("muted", 0xff645e77)), margins(3, 12, 3, 0));
    }

    private void setDailyEnabled(boolean enabled) {
        if (enabled && Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            enableAfterPermission = true;
            dailySwitch.setOnCheckedChangeListener(null);
            dailySwitch.setChecked(false);
            dailySwitch.setOnCheckedChangeListener((button, checked) -> setDailyEnabled(checked));
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 711);
            return;
        }
        if (enabled && Build.VERSION.SDK_INT >= 24) {
            android.app.NotificationManager manager =
                (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null && !manager.areNotificationsEnabled()) {
                setSwitchChecked(false);
                status.setText("Powiadomienia tej aplikacji są wyłączone w telefonie.");
                new AlertDialog.Builder(this)
                    .setMessage("Włącz powiadomienia Pomocnika Alfa, aby dostawać codzienne ćwiczenie.")
                    .setNegativeButton("Później", null)
                    .setPositiveButton("Ustawienia", (dialog, which) -> {
                        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                        i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                        startActivity(i);
                    }).show();
                return;
            }
        }
        enableAfterPermission = false;
        prefs.edit().putBoolean("enabled", enabled).apply();
        if (enabled) {
            GermanNewsReceiver.schedule(this, hour, minute);
            status.setText("Włączono. Powiadomienie może przyjść w pobliżu ustawionej godziny.");
        } else {
            GermanNewsReceiver.cancel(this);
            status.setText("Codzienne powiadomienia są wyłączone. Możesz nadal otwierać teksty ręcznie.");
        }
    }

    private void setSwitchChecked(boolean checked) {
        dailySwitch.setOnCheckedChangeListener(null);
        dailySwitch.setChecked(checked);
        dailySwitch.setOnCheckedChangeListener((button, value) -> setDailyEnabled(value));
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != 711 || !enableAfterPermission) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            dailySwitch.setChecked(true);
            prefs.edit().putBoolean("enabled", true).apply();
            GermanNewsReceiver.schedule(this, hour, minute);
            status.setText("Włączono codzienne powiadomienie.");
        } else {
            status.setText("Włącz powiadomienia w ustawieniach telefonu, aby otrzymywać tekst dnia.");
        }
        enableAfterPermission = false;
    }

    private void chooseTime() {
        new android.app.TimePickerDialog(this, (view, h, m) -> {
            hour = h;
            minute = m;
            prefs.edit().putInt("hour", hour).putInt("minute", minute).apply();
            updateTimeLabel();
            if (prefs.getBoolean("enabled", false)) GermanNewsReceiver.schedule(this, hour, minute);
        }, hour, minute, true).show();
    }
    private void updateTimeLabel() {
        if (timeLabel != null) timeLabel.setText(String.format(java.util.Locale.ROOT, "Codziennie około %02d:%02d", hour, minute));
    }
    private String level() { return "B2".equals(prefs.getString("level", "B1")) ? "B2" : "B1"; }

    private void refreshNews() {
        status.setText("Pobieram wiadomość gospodarczą…");
        worker.submit(() -> {
            try {
                GermanNewsReceiver.Article article = GermanNewsReceiver.fetchLatest(this);
                runOnUiThread(() -> {
                    render(article);
                    status.setText("Pobrano aktualny materiał z RSS.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    GermanNewsReceiver.Article cached = GermanNewsReceiver.cached(this);
                    render(cached);
                    status.setText(cached == null
                        ? "Nie udało się połączyć. Spróbuj ponownie, gdy internet będzie dostępny."
                        : "Nie udało się odświeżyć. Pokazuję ostatnio zapisany materiał.");
                });
            }
        });
    }
    private void render(GermanNewsReceiver.Article article) {
        if (newsTitle == null) return;
        if (article == null) {
            currentArticle = null;
            newsTitle.setText("Pobierz aktualną wiadomość gospodarczą.");
            newsDate.setText("");
            newsText.setText("Tekst z ostatniej aktualizacji będzie dostępny także offline.");
            glossary.setText("");
            readerButton.setEnabled(false);
            sourceButton.setEnabled(false);
            return;
        }
        currentArticle = article;
        newsTitle.setText(article.title);
        newsDate.setText(article.date.isEmpty() ? "Najnowszy dostępny artykuł" : article.date);
        newsText.setText(article.studyText(level()));
        glossary.setText("Wortschatz · " + article.glossary(level()));
        readerButton.setEnabled(true);
        sourceButton.setEnabled(article.link.startsWith("https://"));
    }
    private void openInReader() {
        GermanNewsReceiver.Article article = currentArticle;
        if (article == null) return;
        try {
            startActivity(GermanNewsReader.createIntent(this, article, level()));
        } catch (Exception e) {
            status.setText("Nie udało się otworzyć tekstu w Czytniku Alfa.");
        }
    }
    private void openSource() {
        GermanNewsReceiver.Article article = GermanNewsReceiver.cached(this);
        if (article == null || !article.link.startsWith("https://")) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(article.link))); }
        catch (Exception ignored) { }
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(13), dp(14), dp(13));
        box.setBackground(round(color("cardColor", Color.WHITE), color("line", 0xffe5ddec), 18));
        return box;
    }
    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(dp(2), 1.08f);
        return view;
    }
    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setBackground(round(0xff6846a4, 0xff6846a4, 14));
        return b;
    }
    private Button outlineButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(0xff6846a4);
        b.setAllCaps(false);
        b.setBackground(round(color("cardColor", Color.WHITE), color("line", 0xffe5ddec), 14));
        return b;
    }
    private GradientDrawable round(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }
    private int color(String method, int fallback) {
        try { return (Integer) Class.forName("com.ispina.lokalnie.NativeUi").getMethod(method, Context.class).invoke(null, this); }
        catch (Exception ignored) { return fallback; }
    }
    private LinearLayout.LayoutParams margins(int left, int top, int right, int bottom) {
        return new LinearLayout.LayoutParams(-1, -2) {{ setMargins(dp(left), dp(top), dp(right), dp(bottom)); }};
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
