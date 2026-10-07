package pl.alfalauncher.seven;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQ_READER = 71;
    private static final int REQ_LOCATION = 72;
    private static final int REQ_PICK_WIDGET = 201;
    private static final int REQ_CONFIGURE_WIDGET = 202;
    private static final int HOST_ID = 7015;

    private static final String PREFS = "alfa_launcher_7";
    private static final String PREF_NOTE = "note";
    private static final String PREF_GPS = "weather_gps";
    private static final String PREF_PLACE = "weather_place";
    private static final String PREF_LAT = "weather_lat";
    private static final String PREF_LON = "weather_lon";
    private static final String PREF_WEATHER_TEXT = "weather_cache_text";
    private static final String PREF_WEATHER_TIME = "weather_cache_time";
    private static final String KEY_WIDGET_IDS = "workspace_widget_ids";

    private final Handler clockHandler = new Handler();

    private ViewFlipper flipper;
    private TextView pageDots;
    private float touchDownX;

    private TextView timeView, dateView;
    private TextView locationChip, weatherIcon, temperatureView, weatherDescription, daySummaryView, weatherStatus;
    private TextView batteryView, noteView, todayDateView;
    private LinearLayout widgetContainer;

    private AppWidgetHost widgetHost;
    private AppWidgetManager widgetManager;

    private double weatherLat = 52.2297;
    private double weatherLon = 21.0122;
    private String weatherPlace = "Warszawa";
    private boolean gpsMode = false;
    private WeatherResult lastWeather;

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            updateClock();
            updateSystemWidgets();
            clockHandler.postDelayed(this, 30000L);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.argb(105, 0, 0, 0));
        getWindow().setNavigationBarColor(Color.argb(215, 0, 0, 0));

        widgetHost = new AppWidgetHost(this, HOST_ID);
        widgetManager = AppWidgetManager.getInstance(this);

        loadLocationPrefs();
        setContentView(buildWorkspace());
        clockHandler.post(clockTick);
        offerDefaultLauncherIfNeeded();

        if (gpsMode) resolveGps(true); else loadWeather();
    }

    @Override protected void onStart() {
        super.onStart();
        try { widgetHost.startListening(); } catch (Exception ignored) {}
    }

    @Override protected void onStop() {
        try { widgetHost.stopListening(); } catch (Exception ignored) {}
        super.onStop();
    }

    @Override protected void onResume() {
        super.onResume();
        updateClock();
        updateSystemWidgets();
        updateNote();
        loadWorkspaceWidgets();
    }

    @Override protected void onDestroy() {
        clockHandler.removeCallbacks(clockTick);
        super.onDestroy();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) touchDownX = ev.getX();
        if (ev.getAction() == MotionEvent.ACTION_UP) {
            float dx = ev.getX() - touchDownX;
            if (Math.abs(dx) > dp(85)) {
                if (dx < 0) showPage(Math.min(2, flipper.getDisplayedChild() + 1), true);
                else showPage(Math.max(0, flipper.getDisplayedChild() - 1), false);
                return true;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private View buildWorkspace() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.argb(54, 0, 0, 0));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);

        flipper = new ViewFlipper(this);
        flipper.addView(buildWidgetPage());
        flipper.addView(buildHomePage());
        flipper.addView(buildTodayPage());
        flipper.setDisplayedChild(1);

        shell.addView(flipper, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        pageDots = text("○  ●  ○", 15, Color.rgb(205, 225, 230), Gravity.CENTER);
        pageDots.setPadding(0, dp(5), 0, dp(8));
        shell.addView(pageDots);

        root.addView(shell, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    private View buildHomePage() {
        ScrollView scroll = baseScroll();
        LinearLayout body = baseBody();

        timeView = text("", 56, Color.WHITE, Gravity.CENTER_HORIZONTAL);
        timeView.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        body.addView(timeView);

        dateView = text("", 15, Color.rgb(221, 231, 234), Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dl.setMargins(0, -dp(6), 0, dp(12));
        body.addView(dateView, dl);

        body.addView(buildWeatherWidget());

        TextView title = sectionTitle("NAJWAŻNIEJSZE");
        body.addView(title);

        body.addView(tileRow(
                tile("α", "Pomocnik Alfa", v -> launchPomocnik()),
                tile("☎", "Telefon", v -> startActivity(new Intent(Intent.ACTION_DIAL)))
        ));
        body.addView(tileRow(
                tile("✉", "Wiadomości", v -> launchMessages()),
                tile("◉", "Aparat", v -> launchCamera())
        ));

        Button apps = tile("▦", "Wszystkie aplikacje", v -> startActivity(new Intent(this, AppDrawerActivity.class)));
        LinearLayout.LayoutParams appsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(82));
        appsLp.setMargins(dp(3), dp(4), dp(3), dp(3));
        body.addView(apps, appsLp);

        TextView swipe = text("← widżety       przesuń ekran       narzędzia →",
                10, Color.argb(175,255,255,255), Gravity.CENTER);
        swipe.setPadding(0, dp(10), 0, dp(2));
        body.addView(swipe);

        scroll.addView(body);
        return scroll;
    }

    private View buildWidgetPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(12), dp(12), dp(12), dp(8));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("Widżety", 22, Color.WHITE, Gravity.START);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button add = new Button(this);
        add.setText("+ Dodaj");
        add.setAllCaps(false);
        add.setOnClickListener(v -> pickWidget());
        top.addView(add, new LinearLayout.LayoutParams(dp(98), dp(46)));
        page.addView(top);

        TextView hint = text("Prawdziwe widżety Androida. Przytrzymaj widżet, aby go usunąć.",
                11, Color.rgb(175, 197, 203), Gravity.START);
        hint.setPadding(0, dp(2), 0, dp(8));
        page.addView(hint);

        ScrollView scroll = baseScroll();
        widgetContainer = new LinearLayout(this);
        widgetContainer.setOrientation(LinearLayout.VERTICAL);
        widgetContainer.setPadding(0, dp(2), 0, dp(18));
        scroll.addView(widgetContainer);

        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        loadWorkspaceWidgets();
        return page;
    }

    private View buildTodayPage() {
        ScrollView scroll = baseScroll();
        LinearLayout body = baseBody();

        TextView title = text("Dzisiaj", 28, Color.WHITE, Gravity.START);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        body.addView(title);

        todayDateView = text("", 14, Color.rgb(195,215,220), Gravity.START);
        todayDateView.setPadding(0, 0, 0, dp(10));
        body.addView(todayDateView);

        body.addView(twoPanelRow(buildBatteryCard(), buildCalendarCard()));
        body.addView(buildNoteCard());

        TextView tools = sectionTitle("NARZĘDZIA");
        body.addView(tools);

        body.addView(tileRow(
                tile("▤", "PDF / EPUB", v -> openReader()),
                tile("◉", "Radio", v -> launchPomocnik())
        ));
        body.addView(tileRow(
                tile("▣", "Widżety", v -> showPage(0, false)),
                tile("⌂", "Ekran główny", v -> openHomeSettings())
        ));

        scroll.addView(body);
        return scroll;
    }

    private ScrollView baseScroll() {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        s.setOverScrollMode(View.OVER_SCROLL_NEVER);
        return s;
    }

    private LinearLayout baseBody() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(13), dp(10), dp(13), dp(18));
        return body;
    }

    private void showPage(int index, boolean toLeft) {
        if (index == flipper.getDisplayedChild()) return;
        int distance = getResources().getDisplayMetrics().widthPixels;
        TranslateAnimation out = new TranslateAnimation(0, toLeft ? -distance : distance, 0, 0);
        TranslateAnimation in = new TranslateAnimation(toLeft ? distance : -distance, 0, 0, 0);
        out.setDuration(180);
        in.setDuration(180);
        flipper.setOutAnimation(out);
        flipper.setInAnimation(in);
        flipper.setDisplayedChild(index);
        updateDots();
    }

    private void updateDots() {
        if (pageDots == null) return;
        int i = flipper.getDisplayedChild();
        if (i == 0) pageDots.setText("●  ○  ○");
        else if (i == 1) pageDots.setText("○  ●  ○");
        else pageDots.setText("○  ○  ●");
    }

    private View buildWeatherWidget() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13), dp(11), dp(13), dp(11));
        card.setBackground(weatherBackground());
        card.setOnClickListener(v -> showWeatherDetails());

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        locationChip = text((gpsMode ? "📍 GPS" : "⌂ " + weatherPlace) + "  ▾",
                12, Color.rgb(160, 226, 235), Gravity.START);
        locationChip.setTypeface(Typeface.DEFAULT_BOLD);
        locationChip.setPadding(dp(2), dp(4), dp(8), dp(4));
        locationChip.setOnClickListener(v -> showLocationChooser());
        header.addView(locationChip, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView refresh = text("↻", 22, Color.rgb(210,235,238), Gravity.CENTER);
        refresh.setOnClickListener(v -> { if (gpsMode) resolveGps(false); else loadWeather(); });
        header.addView(refresh, new LinearLayout.LayoutParams(dp(46), dp(42)));
        card.addView(header);

        LinearLayout hero = new LinearLayout(this);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(0, dp(2), 0, dp(3));

        weatherIcon = text("☁", 50, Color.WHITE, Gravity.CENTER);
        hero.addView(weatherIcon, new LinearLayout.LayoutParams(dp(72), dp(76)));

        LinearLayout headline = new LinearLayout(this);
        headline.setOrientation(LinearLayout.VERTICAL);
        headline.setPadding(dp(8), 0, 0, 0);

        temperatureView = text("--°", 37, Color.WHITE, Gravity.START);
        temperatureView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        headline.addView(temperatureView);

        weatherDescription = text("Pobieram pogodę…", 15, Color.rgb(237,243,245), Gravity.START);
        headline.addView(weatherDescription);

        hero.addView(headline, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(hero);

        daySummaryView = text("Za chwilę podsumuję pogodę do końca dnia.",
                13, Color.rgb(219,235,238), Gravity.START);
        daySummaryView.setPadding(dp(3), dp(5), dp(3), dp(5));
        card.addView(daySummaryView);

        weatherStatus = text("Dotknij pogodę po szczegóły",
                10, Color.rgb(135,207,217), Gravity.START);
        weatherStatus.setPadding(dp(3), dp(2), 0, 0);
        card.addView(weatherStatus);

        restoreWeatherCache();
        return card;
    }

    private View buildBatteryCard() {
        LinearLayout card = miniCard();
        card.addView(miniTitle("TELEFON"));
        batteryView = text("🔋 --%", 16, Color.WHITE, Gravity.START);
        batteryView.setPadding(0, dp(7), 0, dp(2));
        card.addView(batteryView);

        TextView settings = text("Ustawienia systemu", 10, Color.rgb(135,207,217), Gravity.START);
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_SETTINGS)));
        card.addView(settings);
        return card;
    }

    private View buildCalendarCard() {
        LinearLayout card = miniCard();
        card.setOnClickListener(v -> openCalendar());
        card.addView(miniTitle("KALENDARZ"));
        TextView t = text("Otwórz dzisiejszy kalendarz", 13, Color.WHITE, Gravity.START);
        t.setPadding(0, dp(7), 0, dp(2));
        card.addView(t);
        return card;
    }

    private View buildNoteCard() {
        LinearLayout card = miniCard();
        card.setMinimumHeight(dp(112));
        card.setOnClickListener(v -> editNote());
        card.addView(miniTitle("NOTATKA ALFA"));
        noteView = text("", 14, Color.WHITE, Gravity.START);
        noteView.setMinLines(2);
        noteView.setMaxLines(4);
        noteView.setPadding(0, dp(7), 0, dp(4));
        card.addView(noteView);
        TextView caption = text("Dotknij, aby edytować", 9, Color.rgb(135,207,217), Gravity.START);
        card.addView(caption);
        updateNote();
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(dp(3), dp(5), dp(3), dp(5));
        wrapper.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrapper;
    }

    private LinearLayout twoPanelRow(View left, View right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        row.addView(left, lp);
        row.addView(right, lp);
        return row;
    }

    private LinearLayout miniCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(11), dp(9), dp(11), dp(9));
        card.setMinimumHeight(dp(94));
        card.setBackground(panelBackground());
        return card;
    }

    private TextView miniTitle(String s) {
        TextView t = text(s, 10, Color.rgb(151,219,228), Gravity.START);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView sectionTitle(String s) {
        TextView t = text(s, 11, Color.rgb(157,220,229), Gravity.START);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(4), dp(14), 0, dp(4));
        return t;
    }

    private TextView text(String s, float size, int color, int gravity) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(gravity);
        return t;
    }

    private LinearLayout tileRow(View left, View right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(82), 1f);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        row.addView(left, lp);
        row.addView(right, lp);
        return row;
    }

    private Button tile(String symbol, String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(symbol + "\n" + label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setGravity(Gravity.CENTER);
        b.setAllCaps(false);
        b.setPadding(dp(5), dp(5), dp(5), dp(5));
        b.setBackground(tileBackground());
        b.setOnClickListener(listener);
        return b;
    }

    private GradientDrawable weatherBackground() {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(225,26,57,69), Color.argb(197,11,28,35)});
        g.setCornerRadius(dp(9));
        g.setStroke(dp(1), Color.argb(155,92,203,219));
        return g;
    }

    private GradientDrawable panelBackground() {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(200,32,49,57), Color.argb(181,18,29,34)});
        g.setCornerRadius(dp(8));
        g.setStroke(dp(1), Color.argb(72,255,255,255));
        return g;
    }

    private GradientDrawable tileBackground() {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(200,37,59,69), Color.argb(184,23,36,42)});
        g.setCornerRadius(dp(7));
        g.setStroke(dp(1), Color.argb(78,255,255,255));
        return g;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void updateClock() {
        Date now = new Date();
        if (timeView != null) timeView.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
        if (dateView != null) dateView.setText(new SimpleDateFormat("EEEE, d MMMM", new Locale("pl","PL")).format(now));
        if (todayDateView != null) {
            todayDateView.setText(new SimpleDateFormat("EEEE, d MMMM yyyy", new Locale("pl","PL")).format(now));
        }
    }

    private void updateSystemWidgets() {
        if (batteryView == null) return;
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        int level = bm != null ? bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) : -1;
        batteryView.setText(level >= 0 ? "🔋 Bateria " + level + "%" : "🔋 Bateria --%");
    }

    private void updateNote() {
        if (noteView == null) return;
        String note = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_NOTE, "");
        noteView.setText(note.trim().isEmpty() ? "Krótka notatka…" : note);
    }

    private void editNote() {
        final EditText input = new EditText(this);
        input.setText(getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_NOTE, ""));
        input.setHint("Np. kupić mleko, zadzwonić…");
        input.setMinLines(3);
        input.setMaxLines(6);
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this)
                .setTitle("Notatka Alfa")
                .setView(input)
                .setNegativeButton("Anuluj", null)
                .setNeutralButton("Wyczyść", (d,w) -> {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(PREF_NOTE).apply();
                    updateNote();
                })
                .setPositiveButton("Zapisz", (d,w) -> {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_NOTE, input.getText().toString()).apply();
                    updateNote();
                }).show();
    }

    private void openCalendar() {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build());
        try { startActivity(i); }
        catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Nie znaleziono aplikacji Kalendarz.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isDefaultLauncher() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && getPackageName().equals(ri.activityInfo.packageName);
    }

    private void offerDefaultLauncherIfNeeded() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (isDefaultLauncher() || p.getBoolean("home_prompt_v15", false)) return;
        p.edit().putBoolean("home_prompt_v15", true).apply();
        new AlertDialog.Builder(this)
                .setTitle("Ustawić Alfa Launcher jako ekran główny?")
                .setMessage("Po ustawieniu jako HOME przycisk ekranu głównego będzie zawsze otwierał Alfę.")
                .setNegativeButton("Później", null)
                .setPositiveButton("Ustaw", (d,w) -> openHomeSettings())
                .show();
    }

    private void openHomeSettings() {
        try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
        catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
    }

    private void pickWidget() {
        int id = widgetHost.allocateAppWidgetId();
        Intent pick = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
        pick.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        try { startActivityForResult(pick, REQ_PICK_WIDGET); }
        catch (Exception e) {
            widgetHost.deleteAppWidgetId(id);
            Toast.makeText(this, "Wybór widżetów nie jest dostępny.", Toast.LENGTH_LONG).show();
        }
    }

    private void loadWorkspaceWidgets() {
        if (widgetContainer == null) return;
        widgetContainer.removeAllViews();

        Set<String> ids = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getStringSet(KEY_WIDGET_IDS, new LinkedHashSet<String>());
        ArrayList<Integer> stale = new ArrayList<>();

        for (String s : ids) {
            try {
                int id = Integer.parseInt(s);
                AppWidgetProviderInfo info = widgetManager.getAppWidgetInfo(id);
                if (info != null) addWidgetView(id, info);
                else stale.add(id);
            } catch (Exception ignored) {}
        }
        for (int id : stale) removeWidgetId(id, false);

        if (widgetContainer.getChildCount() == 0) {
            TextView empty = text("Tu pojawią się Twoje widżety.\nDotknij „+ Dodaj”.",
                    14, Color.rgb(170,194,201), Gravity.CENTER);
            empty.setPadding(dp(18), dp(50), dp(18), dp(50));
            widgetContainer.addView(empty);
        }
    }

    private void addWidgetView(final int id, AppWidgetProviderInfo info) {
        AppWidgetHostView view = widgetHost.createView(this, id, info);
        view.setAppWidget(id, info);
        view.setPadding(dp(4), dp(6), dp(4), dp(6));
        view.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Usunąć widżet?")
                    .setMessage("Widżet zniknie z pulpitu Alfa Launcher.")
                    .setNegativeButton("Anuluj", null)
                    .setPositiveButton("Usuń", (d,w) -> {
                        removeWidgetId(id, true);
                        loadWorkspaceWidgets();
                    }).show();
            return true;
        });

        int h = Math.max(dp(100), dp(Math.max(100, info.minHeight)));
        widgetContainer.addView(view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, h));
    }

    private void saveWidgetId(int id) {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        Set<String> current = new LinkedHashSet<>(
                p.getStringSet(KEY_WIDGET_IDS, new LinkedHashSet<String>()));
        current.add(String.valueOf(id));
        p.edit().putStringSet(KEY_WIDGET_IDS, current).apply();
    }

    private void removeWidgetId(int id, boolean deleteHostId) {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        Set<String> current = new LinkedHashSet<>(
                p.getStringSet(KEY_WIDGET_IDS, new LinkedHashSet<String>()));
        current.remove(String.valueOf(id));
        p.edit().putStringSet(KEY_WIDGET_IDS, current).apply();
        if (deleteHostId) try { widgetHost.deleteAppWidgetId(id); } catch (Exception ignored) {}
    }

    private void loadLocationPrefs() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        gpsMode = p.getBoolean(PREF_GPS, false);
        weatherPlace = p.getString(PREF_PLACE, "Warszawa");
        weatherLat = Double.longBitsToDouble(p.getLong(PREF_LAT, Double.doubleToLongBits(52.2297)));
        weatherLon = Double.longBitsToDouble(p.getLong(PREF_LON, Double.doubleToLongBits(21.0122)));
    }

    private void saveLocationPrefs() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(PREF_GPS, gpsMode)
                .putString(PREF_PLACE, weatherPlace)
                .putLong(PREF_LAT, Double.doubleToLongBits(weatherLat))
                .putLong(PREF_LON, Double.doubleToLongBits(weatherLon))
                .apply();
    }

    private void showLocationChooser() {
        final String[] options = {"📍 GPS telefonu", "Warszawa", "Ispina", "Inna miejscowość…"};
        new AlertDialog.Builder(this)
                .setTitle("Pogoda — lokalizacja")
                .setItems(options, (d,which) -> {
                    if (which == 0) selectGps();
                    else if (which == 1) setKnownPlace("Warszawa", 52.2297, 21.0122);
                    else if (which == 2) setKnownPlace("Ispina", 50.1026, 20.4493);
                    else askCustomPlace();
                })
                .setNegativeButton("Anuluj", null).show();
    }

    private void setKnownPlace(String name, double lat, double lon) {
        gpsMode = false;
        weatherPlace = name;
        weatherLat = lat;
        weatherLon = lon;
        saveLocationPrefs();
        updateLocationChip();
        loadWeather();
    }

    private void askCustomPlace() {
        final EditText input = new EditText(this);
        input.setHint("Wpisz miejscowość");
        input.setSingleLine(true);
        input.setText(gpsMode ? "" : weatherPlace);
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this)
                .setTitle("Wybierz miejscowość")
                .setView(input)
                .setNegativeButton("Anuluj", null)
                .setPositiveButton("Ustaw", (d,w) -> {
                    String q = input.getText().toString().trim();
                    if (!q.isEmpty()) geocodePlace(q);
                }).show();
    }

    private void selectGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQ_LOCATION);
            return;
        }
        gpsMode = true;
        saveLocationPrefs();
        updateLocationChip();
        resolveGps(false);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            boolean ok = false;
            for (int r : grantResults) if (r == PackageManager.PERMISSION_GRANTED) ok = true;
            if (ok) selectGps();
            else Toast.makeText(this, "GPS nie został włączony. Możesz wybrać miejscowość ręcznie.", Toast.LENGTH_LONG).show();
        }
    }

    private void updateLocationChip() {
        if (locationChip != null) locationChip.setText((gpsMode ? "📍 " : "⌂ ") + weatherPlace + "  ▾");
    }

    private void resolveGps(boolean quiet) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (!quiet) selectGps();
            else loadWeather();
            return;
        }

        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) { loadWeather(); return; }

        Location best = null;
        try {
            Location a = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            Location b = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (a != null) best = a;
            if (b != null && (best == null || b.getTime() > best.getTime())) best = b;
        } catch (Exception ignored) {}

        if (best != null) { applyGpsLocation(best); return; }

        String provider = null;
        try {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) provider = LocationManager.NETWORK_PROVIDER;
            else if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) provider = LocationManager.GPS_PROVIDER;
        } catch (Exception ignored) {}

        if (provider == null) {
            if (!quiet) Toast.makeText(this, "Lokalizacja wyłączona — używam ostatniej zapisanej pozycji.", Toast.LENGTH_LONG).show();
            loadWeather();
            return;
        }

        try {
            lm.requestSingleUpdate(provider, new LocationListener() {
                @Override public void onLocationChanged(Location l) { applyGpsLocation(l); }
                @Override public void onStatusChanged(String p, int s, Bundle e) {}
                @Override public void onProviderEnabled(String p) {}
                @Override public void onProviderDisabled(String p) {}
            }, Looper.getMainLooper());
            clockHandler.postDelayed(() -> {
                if (lastWeather == null) loadWeather();
            }, 8000L);
        } catch (Exception e) { loadWeather(); }
    }

    private void applyGpsLocation(Location l) {
        weatherLat = l.getLatitude();
        weatherLon = l.getLongitude();
        gpsMode = true;
        saveLocationPrefs();
        new ReverseGeocodeTask().execute(weatherLat, weatherLon);
        loadWeather();
    }

    private class ReverseGeocodeTask extends AsyncTask<Double, Void, String> {
        @Override protected String doInBackground(Double... p) {
            String osm = reverseWithOpenStreetMap(p[0], p[1]);
            if (osm != null && !osm.trim().isEmpty()) return osm;

            try {
                Geocoder g = new Geocoder(MainActivity.this, new Locale("pl","PL"));
                List<Address> list = g.getFromLocation(p[0], p[1], 1);
                if (list != null && !list.isEmpty()) {
                    Address a = list.get(0);
                    String fine = firstNonEmpty(
                            a.getSubLocality(),
                            safeFeature(a),
                            a.getLocality(),
                            a.getSubAdminArea()
                    );
                    String broad = firstDifferent(
                            fine,
                            a.getLocality(),
                            a.getSubAdminArea(),
                            a.getAdminArea()
                    );
                    if (fine != null && broad != null) return fine + " · " + broad;
                    if (fine != null) return fine;
                    if (broad != null) return broad;
                }
            } catch (Exception ignored) {}
            return "GPS";
        }

        @Override protected void onPostExecute(String place) {
            if (gpsMode) {
                weatherPlace = place;
                saveLocationPrefs();
                updateLocationChip();
            }
        }
    }

    private String reverseWithOpenStreetMap(double lat, double lon) {
        HttpURLConnection c = null;
        try {
            String endpoint = "https://nominatim.openstreetmap.org/reverse?format=jsonv2"
                    + "&lat=" + lat + "&lon=" + lon
                    + "&zoom=18&addressdetails=1&accept-language=pl";
            c = (HttpURLConnection) new URL(endpoint).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(6000);
            c.setRequestProperty("User-Agent", "AlfaLauncher7/1.6 (personal Android launcher)");
            c.setRequestProperty("Accept-Language", "pl");

            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            JSONObject root = new JSONObject(sb.toString());
            JSONObject a = root.optJSONObject("address");
            if (a == null) return null;

            String fine = firstNonEmpty(
                    a.optString("neighbourhood", null),
                    a.optString("quarter", null),
                    a.optString("suburb", null),
                    a.optString("hamlet", null),
                    a.optString("isolated_dwelling", null),
                    a.optString("village", null),
                    a.optString("city_district", null),
                    a.optString("borough", null)
            );

            String broad = firstDifferent(
                    fine,
                    a.optString("village", null),
                    a.optString("town", null),
                    a.optString("city", null),
                    a.optString("municipality", null),
                    a.optString("county", null)
            );

            if (fine != null && broad != null) return fine + " · " + broad;
            if (fine != null) return fine;
            if (broad != null) return broad;

            String display = root.optString("display_name", null);
            if (display != null && !display.trim().isEmpty()) {
                String[] parts = display.split(",");
                return parts.length > 0 ? parts[0].trim() : display;
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.disconnect();
        }
        return null;
    }

    private String safeFeature(Address a) {
        try {
            String f = a.getFeatureName();
            if (f == null || f.trim().isEmpty()) return null;
            // Nie pokazuj numeru budynku jako nazwy lokalizacji.
            if (f.matches("[0-9A-Za-z\\-/ ]{1,12}") && f.matches(".*[0-9].*")) return null;
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    private String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null) {
                String s = v.trim();
                if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) return s;
            }
        }
        return null;
    }

    private String firstDifferent(String fine, String... values) {
        for (String v : values) {
            if (v != null) {
                String s = v.trim();
                if (!s.isEmpty() && !"null".equalsIgnoreCase(s)
                        && (fine == null || !s.equalsIgnoreCase(fine))) return s;
            }
        }
        return null;
    }

    private void geocodePlace(String query) {
        if (weatherStatus != null) weatherStatus.setText("Szukam miejscowości…");
        new PlaceSearchTask().execute(query);
    }

    private class PlaceSearchTask extends AsyncTask<String, Void, PlaceResult> {
        @Override protected PlaceResult doInBackground(String... q) {
            HttpURLConnection c = null;
            try {
                String name = URLEncoder.encode(q[0], "UTF-8");
                URL u = new URL("https://geocoding-api.open-meteo.com/v1/search?name=" + name + "&count=1&language=pl&format=json");
                c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(7000); c.setReadTimeout(7000);
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject root = new JSONObject(sb.toString());
                JSONArray results = root.optJSONArray("results");
                if (results == null || results.length() == 0) return new PlaceResult(null,0,0,"Brak wyników");
                JSONObject x = results.getJSONObject(0);
                String display = x.getString("name");
                if (x.optString("admin1").length() > 0) display += ", " + x.optString("admin1");
                return new PlaceResult(display, x.getDouble("latitude"), x.getDouble("longitude"), null);
            } catch (Exception e) {
                return new PlaceResult(null,0,0,"Błąd wyszukiwania");
            } finally { if (c != null) c.disconnect(); }
        }
        @Override protected void onPostExecute(PlaceResult r) {
            if (r.error != null) {
                Toast.makeText(MainActivity.this, r.error, Toast.LENGTH_LONG).show();
                return;
            }
            gpsMode = false; weatherPlace = r.name; weatherLat = r.lat; weatherLon = r.lon;
            saveLocationPrefs(); updateLocationChip(); loadWeather();
        }
    }

    private static class PlaceResult {
        final String name,error; final double lat,lon;
        PlaceResult(String n,double la,double lo,String e){name=n;lat=la;lon=lo;error=e;}
    }

    private void loadWeather() {
        if (weatherStatus != null) weatherStatus.setText("Odświeżam pogodę…");
        new WeatherTask().execute();
    }

    private class WeatherTask extends AsyncTask<Void,Void,WeatherResult> {
        @Override protected WeatherResult doInBackground(Void... ignored) {
            HttpURLConnection c = null;
            try {
                String endpoint = "https://api.open-meteo.com/v1/forecast?latitude=" + weatherLat
                        + "&longitude=" + weatherLon
                        + "&current=temperature_2m,weather_code"
                        + "&hourly=temperature_2m,weather_code,precipitation_probability"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                        + "&forecast_days=5&timezone=auto";
                c = (HttpURLConnection) new URL(endpoint).openConnection();
                c.setConnectTimeout(7000); c.setReadTimeout(7000);
                c.setRequestProperty("User-Agent","AlfaLauncher7/1.5");
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject root = new JSONObject(sb.toString());
                JSONObject current = root.getJSONObject("current");
                JSONObject hourly = root.getJSONObject("hourly");
                JSONObject daily = root.getJSONObject("daily");

                WeatherResult r = new WeatherResult();
                r.temp = current.getDouble("temperature_2m");
                r.code = current.getInt("weather_code");
                r.currentTime = current.getString("time");

                JSONArray ht = hourly.getJSONArray("time");
                JSONArray htemp = hourly.getJSONArray("temperature_2m");
                JSONArray hcode = hourly.getJSONArray("weather_code");
                JSONArray hpop = hourly.getJSONArray("precipitation_probability");
                String datePrefix = r.currentTime.substring(0,10);
                double min = 999,max = -999; int firstWet = -1,maxPop = 0;

                for(int i=0;i<ht.length();i++){
                    String time=ht.getString(i);
                    if(!time.startsWith(datePrefix)||time.compareTo(r.currentTime)<0) continue;
                    double t=htemp.getDouble(i); min=Math.min(min,t); max=Math.max(max,t);
                    int pop=hpop.optInt(i,0); maxPop=Math.max(maxPop,pop);
                    if(firstWet<0 && pop>=40){firstWet=i;r.firstWetCode=hcode.optInt(i,r.code);}
                }
                if(min==999||max==-999){min=r.temp;max=r.temp;}
                r.dayMin=min;r.dayMax=max;r.maxPop=maxPop;
                if(firstWet>=0){
                    String ft=ht.getString(firstWet);
                    r.firstWetHour=ft.length()>=16?ft.substring(11,16):"";
                }

                JSONArray dtime=daily.getJSONArray("time");
                JSONArray dcode=daily.getJSONArray("weather_code");
                JSONArray dmax=daily.getJSONArray("temperature_2m_max");
                JSONArray dmin=daily.getJSONArray("temperature_2m_min");
                int count=Math.min(5,dtime.length());
                r.days=new String[count];r.codes=new int[count];r.max=new double[count];r.min=new double[count];

                SimpleDateFormat iso=new SimpleDateFormat("yyyy-MM-dd",Locale.US);
                SimpleDateFormat shortDay=new SimpleDateFormat("EEE",new Locale("pl","PL"));
                for(int i=0;i<count;i++){
                    Date d=iso.parse(dtime.getString(i));
                    r.days[i]=d!=null?capitalize(shortDay.format(d)):"—";
                    r.codes[i]=dcode.getInt(i);r.max[i]=dmax.getDouble(i);r.min[i]=dmin.getDouble(i);
                }
                return r;
            } catch(Exception e) {
                WeatherResult r=new WeatherResult();r.error=e.getClass().getSimpleName();return r;
            } finally { if(c!=null)c.disconnect(); }
        }

        @Override protected void onPostExecute(WeatherResult r) {
            if(isFinishing()) return;
            if(r.error!=null){
                restoreWeatherCache();
                if(weatherStatus!=null) weatherStatus.setText("Brak internetu • pokazuję ostatnie dane");
                return;
            }
            lastWeather=r;
            String summary=buildDaySummary(r);
            temperatureView.setText(String.format(Locale.getDefault(),"%.0f°",r.temp));
            weatherIcon.setText(weatherSymbol(r.code));
            weatherDescription.setText(weatherText(r.code));
            daySummaryView.setText(summary);
            weatherStatus.setText("Dotknij po prognozę 5-dniową");
            saveWeatherCache(r,summary);
        }
    }

    private void saveWeatherCache(WeatherResult r,String summary){
        String packed=String.format(Locale.US,"%.1f|%d|%s|%s",r.temp,r.code,weatherText(r.code),summary);
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                .putString(PREF_WEATHER_TEXT,packed)
                .putLong(PREF_WEATHER_TIME,System.currentTimeMillis()).apply();
    }

    private void restoreWeatherCache(){
        if(temperatureView==null) return;
        SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);
        String packed=p.getString(PREF_WEATHER_TEXT,"");
        if(packed.isEmpty()) return;
        try{
            String[] a=packed.split("\\|",4);
            temperatureView.setText(String.format(Locale.getDefault(),"%.0f°",Double.parseDouble(a[0])));
            int code=Integer.parseInt(a[1]);
            weatherIcon.setText(weatherSymbol(code));
            weatherDescription.setText(a[2]);
            daySummaryView.setText(a[3]);
            long age=p.getLong(PREF_WEATHER_TIME,0);
            if(age>0) weatherStatus.setText("Ostatnie dane • " + new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(age)));
        }catch(Exception ignored){}
    }

    private static class WeatherResult{
        double temp,dayMin,dayMax;int code,maxPop,firstWetCode=-1;
        String currentTime,firstWetHour,error;String[] days;int[] codes;double[] max,min;
    }

    private String buildDaySummary(WeatherResult r){
        String temps=String.format(Locale.getDefault(),"%.0f–%.0f°C",r.dayMin,r.dayMax);
        if(r.firstWetHour!=null&&!r.firstWetHour.isEmpty()){
            String event;
            if(r.firstWetCode>=95) event="możliwa burza";
            else if(r.firstWetCode>=71&&r.firstWetCode<=86) event="możliwy śnieg";
            else event="możliwy deszcz";
            return "Do końca dnia: po "+r.firstWetHour+" "+event+" • "+temps;
        }
        if(r.maxPop<30) return "Do końca dnia: bez większych opadów • "+temps;
        return "Do końca dnia: możliwe przelotne opady • "+temps;
    }

    private void showWeatherDetails(){
        if(lastWeather==null){loadWeather();return;}
        StringBuilder sb=new StringBuilder();
        sb.append((gpsMode?"📍 ":"⌂ ")).append(weatherPlace).append("\n\n");
        sb.append(String.format(Locale.getDefault(),"%.0f°C • %s\n",lastWeather.temp,weatherText(lastWeather.code)));
        sb.append(buildDaySummary(lastWeather)).append("\n\nNajbliższe dni:\n");
        if(lastWeather.days!=null){
            for(int i=0;i<lastWeather.days.length;i++){
                sb.append(lastWeather.days[i]).append("  ").append(weatherSymbol(lastWeather.codes[i])).append("  ")
                        .append(String.format(Locale.getDefault(),"%.0f / %.0f°C",lastWeather.max[i],lastWeather.min[i])).append("\n");
            }
        }
        sb.append("\nDane: Open-Meteo");
        new AlertDialog.Builder(this).setTitle("Pogoda").setMessage(sb.toString())
                .setNeutralButton("Lokalizacja",(d,w)->showLocationChooser())
                .setNegativeButton("Zamknij",null)
                .setPositiveButton("Odśwież",(d,w)->{if(gpsMode)resolveGps(false);else loadWeather();}).show();
    }

    private String weatherSymbol(int code){
        if(code==0)return"☀";if(code<=3)return"☁";if(code==45||code==48)return"≋";
        if(code>=51&&code<=67)return"☂";if(code>=71&&code<=77)return"❄";
        if(code>=80&&code<=82)return"☂";if(code>=85&&code<=86)return"❄";if(code>=95)return"ϟ";return"☁";
    }

    private String weatherText(int code){
        if(code==0)return"Bezchmurnie";if(code==1)return"Przeważnie pogodnie";if(code==2)return"Częściowe zachmurzenie";
        if(code==3)return"Pochmurno";if(code==45||code==48)return"Mgła";if(code>=51&&code<=57)return"Mżawka";
        if(code>=61&&code<=67)return"Deszcz";if(code>=71&&code<=77)return"Śnieg";if(code>=80&&code<=82)return"Przelotny deszcz";
        if(code>=85&&code<=86)return"Przelotny śnieg";if(code>=95)return"Burza";return"Warunki zmienne";
    }

    private String capitalize(String s){
        if(s==null||s.length()==0)return"";
        return s.substring(0,1).toUpperCase(new Locale("pl","PL"))+s.substring(1);
    }

    private void launchPomocnik(){
        PackageManager pm=getPackageManager();
        Intent q=new Intent(Intent.ACTION_MAIN,null);q.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps=pm.queryIntentActivities(q,0);
        for(ResolveInfo ri:apps){
            String label=ri.loadLabel(pm).toString().toLowerCase(Locale.ROOT);
            if(label.contains("pomocnik alfa")||label.equals("pomocnik")){
                Intent launch=pm.getLaunchIntentForPackage(ri.activityInfo.packageName);
                if(launch==null){launch=new Intent();launch.setComponent(new ComponentName(ri.activityInfo.packageName,ri.activityInfo.name));}
                startActivity(launch);return;
            }
        }
        Toast.makeText(this,"Nie znaleziono Pomocnika Alfa.",Toast.LENGTH_LONG).show();
    }

    private void launchMessages(){
        String[] packages={"com.google.android.apps.messaging","com.android.mms","com.samsung.android.messaging"};
        for(String pkg:packages){Intent i=getPackageManager().getLaunchIntentForPackage(pkg);if(i!=null){startActivity(i);return;}}
        Intent i=new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"));
        try{startActivity(i);}catch(ActivityNotFoundException e){Toast.makeText(this,"Nie znaleziono aplikacji Wiadomości.",Toast.LENGTH_SHORT).show();}
    }

    private void launchCamera(){
        Intent i=new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        try{startActivity(i);}catch(ActivityNotFoundException e){Toast.makeText(this,"Nie znaleziono aparatu.",Toast.LENGTH_SHORT).show();}
    }

    private void openReader(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/pdf","application/epub+zip","application/x-mobipocket-ebook"});
        try{startActivityForResult(i,REQ_READER);}catch(ActivityNotFoundException e){Toast.makeText(this,"Brak systemowego wyboru plików.",Toast.LENGTH_SHORT).show();}
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);

        if(requestCode==REQ_PICK_WIDGET){
            int id=data!=null?data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1):-1;
            if(resultCode!=RESULT_OK||id<0){if(id>=0)widgetHost.deleteAppWidgetId(id);return;}
            AppWidgetProviderInfo info=widgetManager.getAppWidgetInfo(id);
            if(info!=null&&info.configure!=null){
                Intent cfg=new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
                cfg.setComponent(info.configure);cfg.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
                try{startActivityForResult(cfg,REQ_CONFIGURE_WIDGET);}catch(Exception e){saveWidgetId(id);loadWorkspaceWidgets();}
            }else{saveWidgetId(id);loadWorkspaceWidgets();}
            return;
        }

        if(requestCode==REQ_CONFIGURE_WIDGET){
            int id=data!=null?data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1):-1;
            if(resultCode==RESULT_OK&&id>=0){saveWidgetId(id);loadWorkspaceWidgets();}
            else if(id>=0)widgetHost.deleteAppWidgetId(id);
            return;
        }

        if(requestCode!=REQ_READER||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();String type=getContentResolver().getType(uri);
        Intent view=new Intent(Intent.ACTION_VIEW);view.setDataAndType(uri,type!=null?type:"*/*");view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivity(view);}catch(ActivityNotFoundException e){
            new AlertDialog.Builder(this).setTitle("Brak czytnika")
                    .setMessage("Na tym telefonie nie ma aplikacji obsługującej ten format.")
                    .setPositiveButton("OK",null).show();
        }
    }

    @Override public void onBackPressed(){
        if(flipper!=null&&flipper.getDisplayedChild()!=1){showPage(1,flipper.getDisplayedChild()<1);return;}
    }
}
