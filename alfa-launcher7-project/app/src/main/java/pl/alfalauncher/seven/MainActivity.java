package pl.alfalauncher.seven;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
    private final Handler clockHandler = new Handler();

    private TextView timeView, dateView, weatherIcon, temperatureView, weatherDescription, weatherStatus;

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            updateClock();
            clockHandler.postDelayed(this, 1000L);
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
        body.setPadding(dp(14), dp(10), dp(14), dp(18));

        timeView = new TextView(this);
        timeView.setTextColor(Color.WHITE);
        timeView.setTextSize(58);
        timeView.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        timeView.setGravity(Gravity.CENTER_HORIZONTAL);
        body.addView(timeView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dateView = new TextView(this);
        dateView.setTextColor(Color.rgb(220, 230, 235));
        dateView.setTextSize(16);
        dateView.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dateLp.setMargins(0, -dp(7), 0, dp(12));
        body.addView(dateView, dateLp);

        LinearLayout weather = new LinearLayout(this);
        weather.setOrientation(LinearLayout.HORIZONTAL);
        weather.setGravity(Gravity.CENTER_VERTICAL);
        weather.setPadding(dp(14), dp(10), dp(14), dp(10));
        weather.setBackground(panelBackground());
        weather.setOnClickListener(v -> loadWeather());

        weatherIcon = new TextView(this);
        weatherIcon.setText("☁");
        weatherIcon.setTextColor(Color.WHITE);
        weatherIcon.setTextSize(44);
        weatherIcon.setGravity(Gravity.CENTER);
        weather.addView(weatherIcon, new LinearLayout.LayoutParams(dp(64), dp(70)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(8), 0, 0, 0);

        temperatureView = new TextView(this);
        temperatureView.setText("--°");
        temperatureView.setTextColor(Color.WHITE);
        temperatureView.setTextSize(34);
        temperatureView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        info.addView(temperatureView);

        weatherDescription = new TextView(this);
        weatherDescription.setText("Warszawa • pogoda");
        weatherDescription.setTextColor(Color.rgb(232, 238, 240));
        weatherDescription.setTextSize(15);
        info.addView(weatherDescription);

        weatherStatus = new TextView(this);
        weatherStatus.setText("Dotknij, aby odświeżyć");
        weatherStatus.setTextColor(Color.rgb(142, 215, 225));
        weatherStatus.setTextSize(11);
        info.addView(weatherStatus);

        weather.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        body.addView(weather, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView alfa = new TextView(this);
        alfa.setText("ALFA");
        alfa.setTextColor(Color.rgb(152, 219, 230));
        alfa.setTextSize(12);
        alfa.setTypeface(Typeface.DEFAULT_BOLD);
        alfa.setPadding(dp(4), dp(14), 0, dp(5));
        body.addView(alfa);

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
        hint.setText("Alfa Launcher 7 • telefon • Android 7+");
        hint.setTextColor(Color.argb(180, 255, 255, 255));
        hint.setTextSize(11);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(12), 0, dp(4));
        body.addView(hint);

        scroll.addView(body);
        root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    private LinearLayout tileRow(View left, View right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(90), 1f);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        row.addView(left, lp);
        row.addView(right, lp);
        return row;
    }

    private Button tile(String symbol, String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(symbol + "\n" + label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setGravity(Gravity.CENTER);
        b.setAllCaps(false);
        b.setPadding(dp(5), dp(5), dp(5), dp(5));
        b.setBackground(tileBackground());
        b.setOnClickListener(listener);
        return b;
    }

    private GradientDrawable panelBackground() {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(210, 20, 43, 54), Color.argb(185, 12, 25, 32)});
        g.setCornerRadius(dp(6));
        g.setStroke(dp(1), Color.argb(140, 85, 210, 225));
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
    }

    private void loadWeather() {
        weatherStatus.setText("Odświeżanie…");
        new WeatherTask().execute();
    }

    private class WeatherTask extends AsyncTask<Void, Void, WeatherResult> {
        @Override protected WeatherResult doInBackground(Void... ignored) {
            HttpURLConnection c = null;
            try {
                URL u = new URL("https://api.open-meteo.com/v1/forecast?latitude=52.2297&longitude=21.0122&current=temperature_2m,weather_code&timezone=Europe%2FWarsaw");
                c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(7000);
                c.setReadTimeout(7000);
                c.setRequestProperty("User-Agent", "AlfaLauncher7/1.1");
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                JSONObject current = new JSONObject(sb.toString()).getJSONObject("current");
                return new WeatherResult(current.getDouble("temperature_2m"), current.getInt("weather_code"), null);
            } catch (Exception e) {
                return new WeatherResult(0, -1, e.getClass().getSimpleName());
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
            weatherStatus.setText("Bieżąca pogoda • dotknij, aby odświeżyć");
        }
    }

    private static class WeatherResult {
        final double temp; final int code; final String error;
        WeatherResult(double t, int c, String e) { temp = t; code = c; error = e; }
    }

    private String weatherSymbol(int code) {
        if (code == 0) return "☀";
        if (code <= 3) return "☁";
        if (code == 45 || code == 48) return "≋";
        if (code >= 51 && code <= 67) return "☂";
        if (code >= 71 && code <= 77) return "❄";
        if (code >= 80 && code <= 82) return "☂";
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
