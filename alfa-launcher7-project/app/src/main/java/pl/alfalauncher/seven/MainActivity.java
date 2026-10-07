package pl.alfalauncher.seven;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
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
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_READER = 71;
    private static final int REQ_LOCATION = 72;
    private static final String PREFS = "alfa_launcher_7";
    private static final String PREF_NOTE = "note";
    private static final String PREF_GPS = "weather_gps";
    private static final String PREF_PLACE = "weather_place";
    private static final String PREF_LAT = "weather_lat";
    private static final String PREF_LON = "weather_lon";

    private final Handler clockHandler = new Handler();

    private TextView timeView, dateView;
    private TextView locationChip, weatherIcon, temperatureView, weatherDescription, daySummaryView, weatherStatus;
    private TextView batteryView, ramView, calendarView, noteView;

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

        loadLocationPrefs();
        setContentView(buildPhoneHome());
        clockHandler.post(clockTick);
        offerDefaultLauncherIfNeeded();

        if (gpsMode) resolveGps(true);
        else loadWeather();
    }

    @Override protected void onResume() {
        super.onResume();
        updateClock();
        updateSystemWidgets();
        updateNote();
    }

    @Override protected void onDestroy() {
        clockHandler.removeCallbacks(clockTick);
        super.onDestroy();
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

    private View buildPhoneHome() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.argb(60, 0, 0, 0));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(13), dp(10), dp(13), dp(20));

        timeView = text("", 55, Color.WHITE, Gravity.CENTER_HORIZONTAL);
        timeView.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        body.addView(timeView);

        dateView = text("", 15, Color.rgb(221, 231, 234), Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dateLp.setMargins(0, -dp(6), 0, dp(12));
        body.addView(dateView, dateLp);

        body.addView(buildWeatherWidget());

        TextView widgetsTitle = sectionTitle("NA DZIŚ");
        body.addView(widgetsTitle);
        body.addView(twoPanelRow(buildSystemWidget(), buildCalendarWidget()));
        body.addView(twoPanelRow(buildNoteWidget(), buildRadioWidget()));

        TextView shortcutsTitle = sectionTitle("SKRÓTY");
        body.addView(shortcutsTitle);

        body.addView(tileRow(
                tile("α", "Pomocnik Alfa", v -> launchPomocnik()),
                tile("☎", "Telefon", v -> startActivity(new Intent(Intent.ACTION_DIAL)))
        ));
        body.addView(tileRow(
                tile("✉", "Wiadomości", v -> launchMessages()),
                tile("◉", "Aparat", v -> launchCamera())
        ));
        body.addView(tileRow(
                tile("▤", "PDF / EPUB", v -> openReader()),
                tile("▦", "Aplikacje", v -> startActivity(new Intent(this, AppDrawerActivity.class)))
        ));
        body.addView(tileRow(
                tile("▣", "Widżety", v -> startActivity(new Intent(this, WidgetBoardActivity.class))),
                tile("⌂", "Ekran główny", v -> openHomeSettings())
        ));

        TextView hint = text("Alfa Launcher 7 v1.3 • telefon • Android 7+", 10,
                Color.argb(165, 255, 255, 255), Gravity.CENTER);
        hint.setPadding(0, dp(12), 0, dp(4));
        body.addView(hint);

        scroll.addView(body);
        root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
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

        locationChip = text((gpsMode ? "📍 GPS" : "⌂ " + weatherPlace) + "  ▾", 12,
                Color.rgb(160, 226, 235), Gravity.START);
        locationChip.setTypeface(Typeface.DEFAULT_BOLD);
        locationChip.setPadding(dp(2), dp(4), dp(8), dp(4));
        locationChip.setOnClickListener(v -> showLocationChooser());
        header.addView(locationChip, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView refresh = text("↻", 22, Color.rgb(210, 235, 238), Gravity.CENTER);
        refresh.setPadding(dp(12), 0, dp(4), 0);
        refresh.setOnClickListener(v -> {
            if (gpsMode) resolveGps(false); else loadWeather();
        });
        header.addView(refresh, new LinearLayout.LayoutParams(dp(46), dp(42)));
        card.addView(header);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
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

        weatherDescription = text("Pobieram pogodę…", 15, Color.rgb(237, 243, 245), Gravity.START);
        headline.addView(weatherDescription);

        hero.addView(headline, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(hero);

        daySummaryView = text("Za chwilę podsumuję pogodę do końca dnia.", 13,
                Color.rgb(219, 235, 238), Gravity.START);
        daySummaryView.setPadding(dp(3), dp(5), dp(3), dp(5));
        card.addView(daySummaryView);

        weatherStatus = text("Dotknij pogodę po szczegóły", 10,
                Color.rgb(135, 207, 217), Gravity.START);
        weatherStatus.setPadding(dp(3), dp(2), 0, 0);
        card.addView(weatherStatus);

        return card;
    }

    private View buildSystemWidget() {
        LinearLayout card = miniCard();
        card.addView(miniTitle("TELEFON"));

        batteryView = text("🔋 --%", 15, Color.WHITE, Gravity.START);
        batteryView.setPadding(0, dp(6), 0, dp(3));
        card.addView(batteryView);

        ramView = text("RAM -- GB wolne", 11, Color.rgb(207, 225, 229), Gravity.START);
        card.addView(ramView);
        return card;
    }

    private View buildCalendarWidget() {
        LinearLayout card = miniCard();
        card.setOnClickListener(v -> openCalendar());
        card.addView(miniTitle("KALENDARZ"));

        calendarView = text("", 14, Color.WHITE, Gravity.START);
        calendarView.setPadding(0, dp(6), 0, dp(3));
        card.addView(calendarView);

        TextView caption = text("Otwórz kalendarz", 9, Color.rgb(135, 207, 217), Gravity.START);
        card.addView(caption);
        return card;
    }

    private View buildNoteWidget() {
        LinearLayout card = miniCard();
        card.setOnClickListener(v -> editNote());
        card.addView(miniTitle("NOTATKA"));

        noteView = text("", 13, Color.WHITE, Gravity.START);
        noteView.setMinLines(2);
        noteView.setMaxLines(3);
        noteView.setPadding(0, dp(6), 0, dp(3));
        card.addView(noteView);

        TextView caption = text("Dotknij, aby edytować", 9, Color.rgb(135, 207, 217), Gravity.START);
        card.addView(caption);
        updateNote();
        return card;
    }

    private View buildRadioWidget() {
        LinearLayout card = miniCard();
        card.setOnClickListener(v -> launchPomocnik());
        card.addView(miniTitle("RADIO"));

        TextView icon = text("◉  Alfa Radio", 15, Color.WHITE, Gravity.START);
        icon.setPadding(0, dp(6), 0, dp(3));
        card.addView(icon);

        TextView caption = text("Radio w Pomocniku Alfa", 9, Color.rgb(135, 207, 217), Gravity.START);
        card.addView(caption);
        return card;
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
        TextView t = text(s, 10, Color.rgb(151, 219, 228), Gravity.START);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView sectionTitle(String s) {
        TextView t = text(s, 11, Color.rgb(157, 220, 229), Gravity.START);
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
                new int[]{Color.argb(225, 26, 57, 69), Color.argb(197, 11, 28, 35)});
        g.setCornerRadius(dp(9));
        g.setStroke(dp(1), Color.argb(155, 92, 203, 219));
        return g;
    }

    private GradientDrawable panelBackground() {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(200, 32, 49, 57), Color.argb(181, 18, 29, 34)});
        g.setCornerRadius(dp(8));
        g.setStroke(dp(1), Color.argb(72, 255, 255, 255));
        return g;
    }

    private GradientDrawable tileBackground() {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(200, 37, 59, 69), Color.argb(184, 23, 36, 42)});
        g.setCornerRadius(dp(7));
        g.setStroke(dp(1), Color.argb(78, 255, 255, 255));
        return g;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void updateClock() {
        Date now = new Date();
        timeView.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
        dateView.setText(new SimpleDateFormat("EEEE, d MMMM", new Locale("pl", "PL")).format(now));
        if (calendarView != null) {
            String day = new SimpleDateFormat("EEEE", new Locale("pl", "PL")).format(now);
            String date = new SimpleDateFormat("d MMMM", new Locale("pl", "PL")).format(now);
            calendarView.setText(capitalize(day) + "\n" + date);
        }
    }

    private String capitalize(String s) {
        if (s == null || s.length() == 0) return "";
        return s.substring(0,1).toUpperCase(new Locale("pl", "PL")) + s.substring(1);
    }

    private void updateSystemWidgets() {
        if (batteryView == null || ramView == null) return;

        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        int level = bm != null ? bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) : -1;
        batteryView.setText(level >= 0 ? "🔋 Bateria " + level + "%" : "🔋 Bateria --%");

        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) {
            am.getMemoryInfo(mi);
            double avail = mi.availMem / 1073741824.0;
            ramView.setText(String.format(Locale.getDefault(), "RAM %.1f GB wolne", avail));
        }
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
                .setNeutralButton("Wyczyść", (d, w) -> {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(PREF_NOTE).apply();
                    updateNote();
                })
                .setPositiveButton("Zapisz", (d, w) -> {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_NOTE, input.getText().toString()).apply();
                    updateNote();
                })
                .show();
    }

    private void openCalendar() {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build());
        try { startActivity(i); }
        catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Nie znaleziono aplikacji Kalendarz.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showLocationChooser() {
        final String[] options = {"📍 GPS telefonu", "Warszawa", "Ispina", "Inna miejscowość…"};
        new AlertDialog.Builder(this)
                .setTitle("Pogoda — lokalizacja")
                .setItems(options, (d, which) -> {
                    if (which == 0) selectGps();
                    else if (which == 1) geocodePlace("Warszawa");
                    else if (which == 2) geocodePlace("Ispina");
                    else askCustomPlace();
                })
                .setNegativeButton("Anuluj", null)
                .show();
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
                .setPositiveButton("Ustaw", (d, w) -> {
                    String q = input.getText().toString().trim();
                    if (!q.isEmpty()) geocodePlace(q);
                })
                .show();
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
        weatherPlace = "GPS";
        saveLocationPrefs();
        updateLocationChip();
        resolveGps(false);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            boolean ok = false;
            for (int result : grantResults) if (result == PackageManager.PERMISSION_GRANTED) ok = true;
            if (ok) selectGps();
            else Toast.makeText(this, "GPS nie został włączony. Możesz wybrać miejscowość ręcznie.", Toast.LENGTH_LONG).show();
        }
    }

    private void updateLocationChip() {
        if (locationChip != null) {
            locationChip.setText((gpsMode ? "📍 GPS" : "⌂ " + weatherPlace) + "  ▾");
        }
    }

    private void resolveGps(boolean quiet) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (!quiet) selectGps();
            return;
        }

        if (weatherStatus != null) weatherStatus.setText("Ustalam pozycję GPS…");
        final LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) {
            loadWeather();
            return;
        }

        Location best = null;
        try {
            Location a = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            Location b = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (a != null) best = a;
            if (b != null && (best == null || b.getTime() > best.getTime())) best = b;
        } catch (Exception ignored) {}

        if (best != null) {
            applyGpsLocation(best);
            return;
        }

        String provider = null;
        try {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) provider = LocationManager.NETWORK_PROVIDER;
            else if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) provider = LocationManager.GPS_PROVIDER;
        } catch (Exception ignored) {}

        if (provider == null) {
            if (!quiet) {
                new AlertDialog.Builder(this)
                        .setTitle("Lokalizacja jest wyłączona")
                        .setMessage("Włącz lokalizację telefonu albo wybierz miejscowość ręcznie.")
                        .setNegativeButton("Anuluj", null)
                        .setPositiveButton("Ustawienia", (d, w) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                        .show();
            }
            loadWeather();
            return;
        }

        try {
            lm.requestSingleUpdate(provider, new LocationListener() {
                @Override public void onLocationChanged(Location location) { applyGpsLocation(location); }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
            }, Looper.getMainLooper());
        } catch (Exception e) {
            loadWeather();
        }
    }

    private void applyGpsLocation(Location location) {
        weatherLat = location.getLatitude();
        weatherLon = location.getLongitude();
        gpsMode = true;
        weatherPlace = "GPS";
        saveLocationPrefs();
        updateLocationChip();
        new ReverseGeocodeTask().execute(weatherLat, weatherLon);
        loadWeather();
    }

    private class ReverseGeocodeTask extends AsyncTask<Double, Void, String> {
        @Override protected String doInBackground(Double... p) {
            try {
                Geocoder geocoder = new Geocoder(MainActivity.this, new Locale("pl", "PL"));
                List<Address> list = geocoder.getFromLocation(p[0], p[1], 1);
                if (list != null && !list.isEmpty()) {
                    Address a = list.get(0);
                    if (a.getLocality() != null) return a.getLocality();
                    if (a.getSubAdminArea() != null) return a.getSubAdminArea();
                }
            } catch (Exception ignored) {}
            return null;
        }

        @Override protected void onPostExecute(String place) {
            if (place != null && gpsMode) {
                weatherPlace = place;
                saveLocationPrefs();
                if (locationChip != null) locationChip.setText("📍 " + place + "  ▾");
                if (lastWeather != null) weatherDescription.setText(weatherText(lastWeather.code));
            }
        }
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
                c.setConnectTimeout(7000);
                c.setReadTimeout(7000);
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject root = new JSONObject(sb.toString());
                JSONArray results = root.optJSONArray("results");
                if (results == null || results.length() == 0) return new PlaceResult(null, 0, 0, "Brak wyników");
                JSONObject x = results.getJSONObject(0);
                return new PlaceResult(x.getString("name"), x.getDouble("latitude"), x.getDouble("longitude"), null);
            } catch (Exception e) {
                return new PlaceResult(null, 0, 0, "Błąd wyszukiwania");
            } finally {
                if (c != null) c.disconnect();
            }
        }

        @Override protected void onPostExecute(PlaceResult r) {
            if (r.error != null) {
                Toast.makeText(MainActivity.this, r.error, Toast.LENGTH_LONG).show();
                weatherStatus.setText("Dotknij pogodę po szczegóły");
                return;
            }
            gpsMode = false;
            weatherPlace = r.name;
            weatherLat = r.lat;
            weatherLon = r.lon;
            saveLocationPrefs();
            updateLocationChip();
            loadWeather();
        }
    }

    private static class PlaceResult {
        final String name, error;
        final double lat, lon;
        PlaceResult(String n, double la, double lo, String e) { name=n; lat=la; lon=lo; error=e; }
    }

    private void loadWeather() {
        if (weatherStatus != null) weatherStatus.setText("Odświeżam pogodę…");
        new WeatherTask().execute();
    }

    private class WeatherTask extends AsyncTask<Void, Void, WeatherResult> {
        @Override protected WeatherResult doInBackground(Void... ignored) {
            HttpURLConnection c = null;
            try {
                String endpoint = "https://api.open-meteo.com/v1/forecast?latitude=" + weatherLat
                        + "&longitude=" + weatherLon
                        + "&current=temperature_2m,weather_code"
                        + "&hourly=temperature_2m,weather_code,precipitation_probability"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                        + "&forecast_days=5&timezone=auto";
                URL u = new URL(endpoint);
                c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(7000);
                c.setReadTimeout(7000);
                c.setRequestProperty("User-Agent", "AlfaLauncher7/1.3");

                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
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

                String datePrefix = r.currentTime.substring(0, 10);
                double min = 999, max = -999;
                int firstWet = -1, firstWetCode = -1, maxPop = 0;

                for (int i = 0; i < ht.length(); i++) {
                    String time = ht.getString(i);
                    if (!time.startsWith(datePrefix) || time.compareTo(r.currentTime) < 0) continue;
                    double t = htemp.getDouble(i);
                    min = Math.min(min, t);
                    max = Math.max(max, t);
                    int pop = hpop.optInt(i, 0);
                    maxPop = Math.max(maxPop, pop);
                    if (firstWet < 0 && pop >= 40) {
                        firstWet = i;
                        firstWetCode = hcode.optInt(i, r.code);
                    }
                }

                if (min == 999 || max == -999) { min = r.temp; max = r.temp; }
                r.dayMin = min;
                r.dayMax = max;
                r.maxPop = maxPop;
                if (firstWet >= 0) {
                    String ft = ht.getString(firstWet);
                    r.firstWetHour = ft.length() >= 16 ? ft.substring(11,16) : "";
                    r.firstWetCode = firstWetCode;
                }

                JSONArray dtime = daily.getJSONArray("time");
                JSONArray dcode = daily.getJSONArray("weather_code");
                JSONArray dmax = daily.getJSONArray("temperature_2m_max");
                JSONArray dmin = daily.getJSONArray("temperature_2m_min");
                int count = Math.min(5, dtime.length());
                r.days = new String[count];
                r.codes = new int[count];
                r.max = new double[count];
                r.min = new double[count];

                SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                SimpleDateFormat shortDay = new SimpleDateFormat("EEE", new Locale("pl", "PL"));
                for (int i = 0; i < count; i++) {
                    Date d = iso.parse(dtime.getString(i));
                    r.days[i] = d != null ? capitalize(shortDay.format(d)) : "—";
                    r.codes[i] = dcode.getInt(i);
                    r.max[i] = dmax.getDouble(i);
                    r.min[i] = dmin.getDouble(i);
                }
                return r;
            } catch (Exception e) {
                WeatherResult r = new WeatherResult();
                r.error = e.getClass().getSimpleName();
                return r;
            } finally {
                if (c != null) c.disconnect();
            }
        }

        @Override protected void onPostExecute(WeatherResult r) {
            if (isFinishing()) return;
            if (r.error != null) {
                temperatureView.setText("--°");
                weatherIcon.setText("☁");
                weatherDescription.setText("Brak danych pogodowych");
                daySummaryView.setText("Sprawdź internet lub odśwież pogodę.");
                weatherStatus.setText("Dotknij ↻, aby spróbować ponownie");
                return;
            }

            lastWeather = r;
            temperatureView.setText(String.format(Locale.getDefault(), "%.0f°", r.temp));
            weatherIcon.setText(weatherSymbol(r.code));
            weatherDescription.setText(weatherText(r.code));
            daySummaryView.setText(buildDaySummary(r));
            weatherStatus.setText("Dotknij pogodę po prognozę 5-dniową");
        }
    }

    private static class WeatherResult {
        double temp, dayMin, dayMax;
        int code, maxPop, firstWetCode = -1;
        String currentTime, firstWetHour, error;
        String[] days;
        int[] codes;
        double[] max, min;
    }

    private String buildDaySummary(WeatherResult r) {
        String temps = String.format(Locale.getDefault(), "%.0f–%.0f°C", r.dayMin, r.dayMax);
        if (r.firstWetHour != null && !r.firstWetHour.isEmpty()) {
            String event;
            if (r.firstWetCode >= 95) event = "możliwa burza";
            else if (r.firstWetCode >= 71 && r.firstWetCode <= 86) event = "możliwy śnieg";
            else event = "możliwy deszcz";
            return "Do końca dnia: po " + r.firstWetHour + " " + event + " • " + temps;
        }
        if (r.maxPop < 30) return "Do końca dnia: bez większych opadów • " + temps;
        return "Do końca dnia: możliwe przelotne opady • " + temps;
    }

    private void showWeatherDetails() {
        if (lastWeather == null) {
            loadWeather();
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append((gpsMode ? "📍 " : "⌂ ")).append(weatherPlace).append("\n\n");
        sb.append(String.format(Locale.getDefault(), "%.0f°C • %s\n", lastWeather.temp, weatherText(lastWeather.code)));
        sb.append(buildDaySummary(lastWeather)).append("\n\n");
        sb.append("Najbliższe dni:\n");
        if (lastWeather.days != null) {
            for (int i = 0; i < lastWeather.days.length; i++) {
                sb.append(lastWeather.days[i]).append("  ")
                        .append(weatherSymbol(lastWeather.codes[i])).append("  ")
                        .append(String.format(Locale.getDefault(), "%.0f / %.0f°C", lastWeather.max[i], lastWeather.min[i]))
                        .append("\n");
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Pogoda")
                .setMessage(sb.toString())
                .setNeutralButton("Lokalizacja", (d, w) -> showLocationChooser())
                .setNegativeButton("Zamknij", null)
                .setPositiveButton("Odśwież", (d, w) -> {
                    if (gpsMode) resolveGps(false); else loadWeather();
                })
                .show();
    }

    private String weatherSymbol(int code) {
        if (code == 0) return "☀";
        if (code <= 3) return "☁";
        if (code == 45 || code == 48) return "≋";
        if (code >= 51 && code <= 67) return "☂";
        if (code >= 71 && code <= 77) return "❄";
        if (code >= 80 && code <= 82) return "☂";
        if (code >= 85 && code <= 86) return "❄";
        if (code >= 95) return "ϟ";
        return "☁";
    }

    private String weatherText(int code) {
        if (code == 0) return "Bezchmurnie";
        if (code == 1) return "Przeważnie pogodnie";
        if (code == 2) return "Częściowe zachmurzenie";
        if (code == 3) return "Pochmurno";
        if (code == 45 || code == 48) return "Mgła";
        if (code >= 51 && code <= 57) return "Mżawka";
        if (code >= 61 && code <= 67) return "Deszcz";
        if (code >= 71 && code <= 77) return "Śnieg";
        if (code >= 80 && code <= 82) return "Przelotny deszcz";
        if (code >= 85 && code <= 86) return "Przelotny śnieg";
        if (code >= 95) return "Burza";
        return "Warunki zmienne";
    }

    private boolean isDefaultLauncher() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && getPackageName().equals(ri.activityInfo.packageName);
    }

    private void offerDefaultLauncherIfNeeded() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (isDefaultLauncher() || p.getBoolean("home_prompt_shown", false)) return;
        p.edit().putBoolean("home_prompt_shown", true).apply();

        new AlertDialog.Builder(this)
                .setTitle("Ustawić Alfa Launcher jako ekran główny?")
                .setMessage("Wtedy przycisk HOME będzie zawsze otwierał Alfa Launcher zamiast dotychczasowego pulpitu.")
                .setNegativeButton("Później", null)
                .setPositiveButton("Ustaw", (d, w) -> openHomeSettings())
                .show();
    }

    private void openHomeSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void launchPomocnik() {
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN, null);
        q.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(q, 0);
        for (ResolveInfo ri : apps) {
            String label = ri.loadLabel(pm).toString().toLowerCase(Locale.ROOT);
            if (label.contains("pomocnik alfa") || label.equals("pomocnik")) {
                Intent launch = pm.getLaunchIntentForPackage(ri.activityInfo.packageName);
                if (launch == null) {
                    launch = new Intent();
                    launch.setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
                }
                startActivity(launch);
                return;
            }
        }
        Toast.makeText(this, "Nie znaleziono Pomocnika Alfa.", Toast.LENGTH_LONG).show();
    }

    private void launchMessages() {
        String[] packages = {"com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging"};
        for (String pkg : packages) {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) { startActivity(i); return; }
        }
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"));
        try { startActivity(i); }
        catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Nie znaleziono aplikacji Wiadomości.", Toast.LENGTH_SHORT).show();
        }
    }

    private void launchCamera() {
        Intent i = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        try { startActivity(i); }
        catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Nie znaleziono aparatu.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openReader() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/pdf", "application/epub+zip", "application/x-mobipocket-ebook"
        });
        try { startActivityForResult(i, REQ_READER); }
        catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Brak systemowego wyboru plików.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_READER || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String type = getContentResolver().getType(uri);
        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setDataAndType(uri, type != null ? type : "*/*");
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(view); }
        catch (ActivityNotFoundException e) {
            new AlertDialog.Builder(this)
                    .setTitle("Brak czytnika")
                    .setMessage("Na tym telefonie nie ma aplikacji obsługującej ten format.")
                    .setPositiveButton("OK", null).show();
        }
    }

    @Override public void onBackPressed() {
        // HOME pozostaje ekranem głównym.
    }
}
