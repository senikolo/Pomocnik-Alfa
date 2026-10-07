package pl.alfalauncher.seven;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CalendarContract;
import android.provider.MediaStore;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_READER = 71;
    private static final String PREFS = "alfa_launcher_7";
    private static final String PREF_NOTE = "note";

    private final Handler clockHandler = new Handler();

    private TextView timeView, dateView;
    private TextView weatherIcon, temperatureView, weatherDescription, weatherStatus;
    private TextView feelsView, humidityView, pressureView, windView, sunView;
    private LinearLayout forecastRow;
    private TextView batteryView, ramView, calendarView, noteView;

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            updateClock();
            updateSystemWidgets();
            clockHandler.postDelayed(this, 30000L);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.argb(110, 0, 0, 0));
        getWindow().setNavigationBarColor(Color.argb(210, 0, 0, 0));
        setContentView(buildPhoneHome());
        clockHandler.post(clockTick);
        loadWeather();
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

    private View buildPhoneHome() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.argb(72, 0, 0, 0));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(8), dp(12), dp(18));

        timeView = new TextView(this);
        timeView.setTextColor(Color.WHITE);
        timeView.setTextSize(56);
        timeView.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        timeView.setGravity(Gravity.CENTER_HORIZONTAL);
        body.addView(timeView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dateView = new TextView(this);
        dateView.setTextColor(Color.rgb(220, 230, 235));
        dateView.setTextSize(15);
        dateView.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dateLp.setMargins(0, -dp(7), 0, dp(10));
        body.addView(dateView, dateLp);

        body.addView(buildWeatherWidget());

        TextView widgetsTitle = sectionTitle("WIDŻETY ALFA");
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

        TextView hint = new TextView(this);
        hint.setText("Alfa Launcher 7 v1.2 Widgets • Android 7+");
        hint.setTextColor(Color.argb(180, 255, 255, 255));
        hint.setTextSize(10);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(10), 0, dp(4));
        body.addView(hint);

        scroll.addView(body);
        root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    private View buildWeatherWidget() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(weatherBackground());
        card.setOnClickListener(v -> loadWeather());

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        weatherIcon = text("☁", 46, Color.WHITE, Gravity.CENTER);
        top.addView(weatherIcon, new LinearLayout.LayoutParams(dp(66), dp(72)));

        LinearLayout headline = new LinearLayout(this);
        headline.setOrientation(LinearLayout.VERTICAL);
        headline.setPadding(dp(6), 0, 0, 0);

        temperatureView = text("--°", 34, Color.WHITE, Gravity.START);
        temperatureView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        headline.addView(temperatureView);

        weatherDescription = text("Warszawa • pogoda", 15, Color.rgb(235, 242, 244), Gravity.START);
        headline.addView(weatherDescription);

        weatherStatus = text("Dotknij, aby odświeżyć", 10, Color.rgb(140, 220, 232), Gravity.START);
        headline.addView(weatherStatus);

        top.addView(headline, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(top);

        LinearLayout details1 = detailRow();
        feelsView = detail("Odczuwalna --°");
        humidityView = detail("Wilgotność --%");
        details1.addView(feelsView, detailLp());
        details1.addView(humidityView, detailLp());
        card.addView(details1);

        LinearLayout details2 = detailRow();
        pressureView = detail("Ciśnienie ---- hPa");
        windView = detail("Wiatr -- km/h");
        details2.addView(pressureView, detailLp());
        details2.addView(windView, detailLp());
        card.addView(details2);

        sunView = text("☀ Wschód --:--   •   Zachód --:--", 11, Color.rgb(220, 235, 238), Gravity.CENTER);
        sunView.setPadding(0, dp(5), 0, dp(4));
        card.addView(sunView);

        forecastRow = new LinearLayout(this);
        forecastRow.setOrientation(LinearLayout.HORIZONTAL);
        forecastRow.setGravity(Gravity.CENTER);
        for (int i = 0; i < 5; i++) forecastRow.addView(forecastCell("—", "☁", "--/--"), forecastLp());
        card.addView(forecastRow);

        return card;
    }

    private View buildSystemWidget() {
        LinearLayout card = miniCard();
        TextView title = miniTitle("SYSTEM");
        card.addView(title);

        batteryView = text("🔋 Bateria --%", 15, Color.WHITE, Gravity.START);
        batteryView.setPadding(0, dp(5), 0, dp(3));
        card.addView(batteryView);

        ramView = text("RAM -- / -- GB", 12, Color.rgb(205, 225, 230), Gravity.START);
        card.addView(ramView);

        TextView caption = text("Odświeża się automatycznie", 9, Color.rgb(130, 205, 217), Gravity.START);
        caption.setPadding(0, dp(5), 0, 0);
        card.addView(caption);
        return card;
    }

    private View buildCalendarWidget() {
        LinearLayout card = miniCard();
        card.setOnClickListener(v -> openCalendar());
        card.addView(miniTitle("KALENDARZ"));

        calendarView = text("", 14, Color.WHITE, Gravity.START);
        calendarView.setPadding(0, dp(5), 0, dp(4));
        card.addView(calendarView);

        TextView caption = text("Dotknij, aby otworzyć kalendarz", 9, Color.rgb(130, 205, 217), Gravity.START);
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
        noteView.setPadding(0, dp(5), 0, dp(4));
        card.addView(noteView);

        TextView caption = text("Dotknij, aby edytować", 9, Color.rgb(130, 205, 217), Gravity.START);
        card.addView(caption);
        updateNote();
        return card;
    }

    private View buildRadioWidget() {
        LinearLayout card = miniCard();
        card.setOnClickListener(v -> launchPomocnik());
        card.addView(miniTitle("RADIO"));

        TextView icon = text("◉  Alfa Radio", 15, Color.WHITE, Gravity.START);
        icon.setPadding(0, dp(5), 0, dp(4));
        card.addView(icon);

        TextView caption = text("Otwórz radio w Pomocniku Alfa", 9, Color.rgb(130, 205, 217), Gravity.START);
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
        card.setPadding(dp(10), dp(8), dp(10), dp(8));
        card.setMinimumHeight(dp(105));
        card.setBackground(panelBackground());
        return card;
    }

    private TextView miniTitle(String s) {
        TextView t = text(s, 10, Color.rgb(145, 220, 230), Gravity.START);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView sectionTitle(String s) {
        TextView t = text(s, 11, Color.rgb(152, 219, 230), Gravity.START);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(4), dp(13), 0, dp(4));
        return t;
    }

    private LinearLayout detailRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(3), 0, 0);
        return row;
    }

    private LinearLayout.LayoutParams detailLp() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private TextView detail(String s) {
        return text(s, 11, Color.rgb(220, 235, 238), Gravity.CENTER);
    }

    private LinearLayout forecastCell(String day, String symbol, String temps) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(2), dp(4), dp(2), dp(2));

        TextView d = text(day, 9, Color.rgb(185, 215, 220), Gravity.CENTER);
        TextView i = text(symbol, 20, Color.WHITE, Gravity.CENTER);
        TextView t = text(temps, 10, Color.WHITE, Gravity.CENTER);
        cell.addView(d);
        cell.addView(i);
        cell.addView(t);
        return cell;
    }

    private LinearLayout.LayoutParams forecastLp() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(86), 1f);
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
                new int[]{Color.argb(225, 24, 57, 70), Color.argb(195, 9, 26, 34)});
        g.setCornerRadius(dp(7));
        g.setStroke(dp(1), Color.argb(170, 78, 211, 229));
        return g;
    }

    private GradientDrawable panelBackground() {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(210, 31, 51, 60), Color.argb(188, 17, 29, 35)});
        g.setCornerRadius(dp(6));
        g.setStroke(dp(1), Color.argb(95, 255, 255, 255));
        return g;
    }

    private GradientDrawable tileBackground() {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(205, 39, 62, 73), Color.argb(190, 22, 36, 43)});
        g.setCornerRadius(dp(5));
        g.setStroke(dp(1), Color.argb(90, 255, 255, 255));
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
            String date = new SimpleDateFormat("d MMMM yyyy", new Locale("pl", "PL")).format(now);
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
            double total = mi.totalMem / 1073741824.0;
            ramView.setText(String.format(Locale.getDefault(), "RAM %.1f / %.1f GB wolne", avail, total));
        }
    }

    private void updateNote() {
        if (noteView == null) return;
        String note = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_NOTE, "");
        noteView.setText(note.trim().isEmpty() ? "Dotknij i zapisz krótką notatkę…" : note);
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

    private void loadWeather() {
        weatherStatus.setText("Odświeżanie…");
        new WeatherTask().execute();
    }

    private class WeatherTask extends AsyncTask<Void, Void, WeatherResult> {
        @Override protected WeatherResult doInBackground(Void... ignored) {
            HttpURLConnection c = null;
            try {
                String endpoint = "https://api.open-meteo.com/v1/forecast?latitude=52.2297&longitude=21.0122"
                        + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,pressure_msl,weather_code,wind_speed_10m"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset"
                        + "&forecast_days=5&timezone=Europe%2FWarsaw";
                URL u = new URL(endpoint);
                c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(7000);
                c.setReadTimeout(7000);
                c.setRequestProperty("User-Agent", "AlfaLauncher7/1.2");

                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject root = new JSONObject(sb.toString());
                JSONObject current = root.getJSONObject("current");
                JSONObject daily = root.getJSONObject("daily");

                WeatherResult r = new WeatherResult();
                r.temp = current.getDouble("temperature_2m");
                r.feels = current.getDouble("apparent_temperature");
                r.humidity = current.getInt("relative_humidity_2m");
                r.pressure = current.getDouble("pressure_msl");
                r.code = current.getInt("weather_code");
                r.wind = current.getDouble("wind_speed_10m");

                JSONArray times = daily.getJSONArray("time");
                JSONArray codes = daily.getJSONArray("weather_code");
                JSONArray max = daily.getJSONArray("temperature_2m_max");
                JSONArray min = daily.getJSONArray("temperature_2m_min");
                JSONArray sunrise = daily.getJSONArray("sunrise");
                JSONArray sunset = daily.getJSONArray("sunset");

                int count = Math.min(5, times.length());
                r.days = new String[count];
                r.codes = new int[count];
                r.max = new double[count];
                r.min = new double[count];

                SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                SimpleDateFormat shortDay = new SimpleDateFormat("EEE", new Locale("pl", "PL"));
                for (int i = 0; i < count; i++) {
                    Date d = iso.parse(times.getString(i));
                    r.days[i] = d != null ? shortDay.format(d) : "—";
                    r.codes[i] = codes.getInt(i);
                    r.max[i] = max.getDouble(i);
                    r.min[i] = min.getDouble(i);
                }

                r.sunrise = hhmm(sunrise.getString(0));
                r.sunset = hhmm(sunset.getString(0));
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
                weatherDescription.setText("Warszawa • brak danych");
                weatherStatus.setText("Dotknij, aby spróbować ponownie");
                return;
            }

            temperatureView.setText(String.format(Locale.getDefault(), "%.0f°", r.temp));
            weatherIcon.setText(weatherSymbol(r.code));
            weatherDescription.setText("Warszawa • " + weatherText(r.code));
            weatherStatus.setText("Aktualizacja online • dotknij, aby odświeżyć");
            feelsView.setText(String.format(Locale.getDefault(), "Odczuwalna %.0f°", r.feels));
            humidityView.setText("Wilgotność " + r.humidity + "%");
            pressureView.setText(String.format(Locale.getDefault(), "Ciśnienie %.0f hPa", r.pressure));
            windView.setText(String.format(Locale.getDefault(), "Wiatr %.0f km/h", r.wind));
            sunView.setText("☀ Wschód " + r.sunrise + "   •   Zachód " + r.sunset);

            forecastRow.removeAllViews();
            if (r.days != null) {
                for (int i = 0; i < r.days.length; i++) {
                    String temps = String.format(Locale.getDefault(), "%.0f/%.0f°", r.max[i], r.min[i]);
                    forecastRow.addView(forecastCell(r.days[i], weatherSymbol(r.codes[i]), temps), forecastLp());
                }
            }
        }
    }

    private static String hhmm(String iso) {
        int t = iso.indexOf('T');
        if (t >= 0 && iso.length() >= t + 6) return iso.substring(t + 1, t + 6);
        return "--:--";
    }

    private static class WeatherResult {
        double temp, feels, pressure, wind;
        int humidity, code;
        String sunrise = "--:--", sunset = "--:--", error;
        String[] days;
        int[] codes;
        double[] max, min;
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
        if (code == 0) return "bezchmurnie";
        if (code == 1) return "przeważnie pogodnie";
        if (code == 2) return "częściowe zachmurzenie";
        if (code == 3) return "pochmurno";
        if (code == 45 || code == 48) return "mgła";
        if (code >= 51 && code <= 57) return "mżawka";
        if (code >= 61 && code <= 67) return "deszcz";
        if (code >= 71 && code <= 77) return "śnieg";
        if (code >= 80 && code <= 82) return "przelotny deszcz";
        if (code >= 85 && code <= 86) return "przelotny śnieg";
        if (code >= 95) return "burza";
        return "warunki zmienne";
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
