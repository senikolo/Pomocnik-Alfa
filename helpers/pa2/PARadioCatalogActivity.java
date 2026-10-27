package com.ispina.lokalnie;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.util.TypedValue;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Searchable Polish station catalogue and editable on-device station list. */
public final class PARadioCatalogActivity extends Activity {
    private static final String PREFS = "radio_catalog_state";
    private static final String SERVICE = "com.ispina.lokalnie.radio.RadioService";
    private static final String ACTION_PLAY = "com.ispina.lokalnie.radio.PLAY";
    private static final String ACTION_TOGGLE = "com.ispina.lokalnie.radio.TOGGLE";
    private static final String ACTION_STOP = "com.ispina.lokalnie.radio.STOP";

    private final ArrayList<Station> catalog = new ArrayList<>();
    private final ArrayList<Station> mine = new ArrayList<>();
    private final ArrayList<Station> visible = new ArrayList<>();
    private final Set<String> favorites = new HashSet<>();
    private final Handler handler = new Handler();
    private int selectedTab = 0;
    private EditText search;
    private TextView mineTab;
    private TextView favoritesTab;
    private TextView catalogTab;
    private TextView listHeading;
    private TextView emptyState;
    private TextView playerStatus;
    private TextView trackInfo;
    private TextView programInfo;
    private TextView infoSource;
    private ListView stationList;
    private StationAdapter adapter;

    private final Runnable metadataTicker = new Runnable() {
        @Override public void run() {
            refreshPlayerInfo();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        loadCatalog();
        loadMyStations();
        loadFavorites();
        buildScreen();
        updateList();
        refreshPlayerInfo();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(metadataTicker);
        handler.post(metadataTicker);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(metadataTicker);
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(metadataTicker);
        super.onDestroy();
    }

    private void loadCatalog() {
        try {
            InputStream in = getAssets().open("pa_regions/radio_catalog.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) json.append(line);
            reader.close();
            JSONObject root = new JSONObject(json.toString());
            JSONArray rows = root.optJSONArray("stations");
            if (rows != null) {
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.optJSONObject(i);
                    if (row == null) continue;
                    Station station = Station.fromJson(row);
                    if (!station.name.isEmpty() && station.url.startsWith("https://")) catalog.add(station);
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Nie udało się wczytać katalogu stacji.", Toast.LENGTH_LONG).show();
        }
    }

    private void loadMyStations() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String saved = prefs.getString("my_stations", null);
        if (saved == null) {
            mine.addAll(catalog);
            saveMyStations();
            return;
        }
        try {
            JSONArray rows = new JSONArray(saved);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                Station station = Station.fromJson(row);
                if (!station.name.isEmpty() && !station.url.isEmpty() && !containsId(station.id)) mine.add(station);
            }
        } catch (Exception ignored) {
            mine.clear();
            mine.addAll(catalog);
            saveMyStations();
        }
    }

    private void saveMyStations() {
        JSONArray rows = new JSONArray();
        for (Station station : mine) rows.put(station.toJson());
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("my_stations", rows.toString()).apply();
    }

    private void loadFavorites() {
        Set<String> saved = getSharedPreferences(PREFS, MODE_PRIVATE).getStringSet("favorites", null);
        if (saved != null) favorites.addAll(saved);
    }

    private void saveFavorites() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putStringSet("favorites", new HashSet<>(favorites)).apply();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 249, 247));
        root.setPadding(dp(16), dp(8), dp(16), dp(12));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = actionButton("‹", Color.WHITE, accentColor(), true);
        back.setTextSize(28f);
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        back.setOnClickListener(v -> finish());
        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.setPadding(dp(12), 0, 0, 0);
        titleBlock.addView(text("POMOCNIK ALFA", 10f, true, accentColor()));
        titleBlock.addView(text("Radio", 25f, true));
        header.addView(titleBlock, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(header);

        LinearLayout playerCard = card();
        playerCard.setPadding(dp(17), dp(15), dp(17), dp(14));
        playerCard.setBackground(playerBackground());
        TextView nowPlaying = text("♫   TERAZ W RADIU", 11f, true, Color.WHITE);
        playerCard.addView(nowPlaying);
        addGap(playerCard, 7);
        playerStatus = text("Wybierz stację, by rozpocząć", 18f, true, Color.WHITE);
        trackInfo = text("", 14f, false, Color.WHITE);
        programInfo = text("", 13f, true, Color.WHITE);
        infoSource = text("Włącz ulubioną stację i ciesz się muzyką.", 11f, false, Color.WHITE);
        infoSource.setAlpha(0.82f);
        playerCard.addView(playerStatus);
        addGap(playerCard, 3);
        playerCard.addView(trackInfo);
        playerCard.addView(programInfo);
        addGap(playerCard, 3);
        playerCard.addView(infoSource);
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        TextView toggle = actionButton("Ⅱ   Pauza / Wznów", blend(Color.WHITE, 0.17f), Color.WHITE, false);
        TextView stop = actionButton("■   Zatrzymaj", blend(Color.WHITE, 0.17f), Color.WHITE, false);
        LinearLayout.LayoutParams controlLp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        controlLp.rightMargin = dp(6);
        controls.addView(toggle, controlLp);
        controls.addView(stop, new LinearLayout.LayoutParams(0, dp(40), 1f));
        toggle.setOnClickListener(v -> sendCommand(ACTION_TOGGLE, null));
        stop.setOnClickListener(v -> sendCommand(ACTION_STOP, null));
        LinearLayout.LayoutParams controlsLp = new LinearLayout.LayoutParams(-1, -2);
        controlsLp.topMargin = dp(12);
        playerCard.addView(controls, controlsLp);
        LinearLayout.LayoutParams playerLp = new LinearLayout.LayoutParams(-1, -2);
        playerLp.topMargin = dp(12);
        playerLp.bottomMargin = dp(12);
        root.addView(playerCard, playerLp);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER);
        mineTab = tab("Moje stacje");
        favoritesTab = tab("♡ Ulubione");
        catalogTab = tab("Katalog 200");
        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        tabLp.rightMargin = dp(5);
        tabs.addView(mineTab, new LinearLayout.LayoutParams(tabLp));
        tabs.addView(favoritesTab, new LinearLayout.LayoutParams(tabLp));
        tabs.addView(catalogTab, new LinearLayout.LayoutParams(0, dp(42), 1f));
        mineTab.setOnClickListener(v -> setTab(0));
        favoritesTab.setOnClickListener(v -> setTab(1));
        catalogTab.setOnClickListener(v -> setTab(2));
        root.addView(tabs);

        TextView helper = text("Wybierz stację na dobry nastrój", 12f, false);
        helper.setTextColor(Color.rgb(104, 119, 111));
        LinearLayout.LayoutParams helperLp = new LinearLayout.LayoutParams(-1, -2);
        helperLp.topMargin = dp(12);
        helperLp.bottomMargin = dp(6);
        root.addView(helper, helperLp);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(14f);
        search.setHint("Szukaj stacji radiowej");
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(dp(13), 0, dp(10), 0);
        search.setBackground(rounded(Color.WHITE, Color.rgb(225, 234, 228), 15));
        search.setTextColor(Color.rgb(39, 54, 46));
        search.setHintTextColor(Color.rgb(132, 145, 137));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        searchLp.rightMargin = dp(6);
        searchRow.addView(search, searchLp);
        TextView add = actionButton("+ Dodaj", accentColor(), Color.WHITE, false);
        searchRow.addView(add, new LinearLayout.LayoutParams(dp(82), dp(46)));
        add.setOnClickListener(v -> addTypedStation());
        root.addView(searchRow);

        TextView custom = actionButton("＋   Dodaj własną stację", blend(accentColor(), 0.11f), accentColor(), false);
        custom.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        custom.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams customLp = new LinearLayout.LayoutParams(-1, dp(40));
        customLp.topMargin = dp(5);
        customLp.bottomMargin = dp(9);
        root.addView(custom, customLp);
        custom.setOnClickListener(v -> showCustomStationDialog(search.getText().toString().trim()));

        listHeading = text("Moje stacje", 17f, true);
        LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(-1, -2);
        headingLp.bottomMargin = dp(7);
        root.addView(listHeading, headingLp);

        stationList = new ListView(this);
        stationList.setDividerHeight(dp(8));
        stationList.setCacheColorHint(Color.TRANSPARENT);
        stationList.setBackgroundColor(Color.TRANSPARENT);
        stationList.setSelector(android.R.color.transparent);
        stationList.setClipToPadding(false);
        stationList.setPadding(0, 0, 0, dp(4));
        adapter = new StationAdapter();
        stationList.setAdapter(adapter);
        emptyState = text("", 14f, false);
        emptyState.setGravity(Gravity.CENTER);
        emptyState.setPadding(dp(20), dp(18), dp(20), dp(18));
        emptyState.setBackground(rounded(Color.WHITE, Color.rgb(230, 237, 232), 18));
        stationList.setEmptyView(emptyState);
        root.addView(emptyState, new LinearLayout.LayoutParams(-1, -2));
        root.addView(stationList, new LinearLayout.LayoutParams(-1, 0, 1f));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateList(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                addTypedStation();
                return true;
            }
            return false;
        });

        setTab(0);
        setContentView(root);
    }

    private void setTab(boolean catalogTabSelected) { setTab(catalogTabSelected ? 2 : 0); }

    private void setTab(int tab) {
        selectedTab = tab;
        styleTab(mineTab, selectedTab == 0);
        styleTab(favoritesTab, selectedTab == 1);
        styleTab(catalogTab, selectedTab == 2);
        updateList();
    }

    private void updateList() {
        if (adapter == null) return;
        String query = search == null ? "" : normalize(search.getText().toString().trim());
        ArrayList<Station> source = selectedTab == 2 ? catalog : selectedTab == 1 ? favoriteStations() : mine;
        visible.clear();
        for (Station station : source) {
            if (query.isEmpty() || normalize(station.name).contains(query) || normalize(station.tags).contains(query)) visible.add(station);
        }
        if (listHeading != null) {
            if (selectedTab == 2) listHeading.setText("Odkryj 200 polskich stacji");
            else if (selectedTab == 1) listHeading.setText("Twoje ulubione · " + favoriteStations().size());
            else listHeading.setText("Twoje stacje · " + mine.size());
        }
        if (mineTab != null) mineTab.setText("Moje stacje");
        if (favoritesTab != null) favoritesTab.setText("♡ Ulubione");
        if (catalogTab != null) catalogTab.setText("Katalog 200");
        if (emptyState != null) {
            if (selectedTab == 1) emptyState.setText("Tu pojawią się Twoje ulubione stacje.\nDotknij serduszka przy wybranej stacji.");
            else if (selectedTab == 0) emptyState.setText("Twoja lista jest pusta.\nDodaj stacje z katalogu albo wyszukaj nazwę.");
            else emptyState.setText("Nie znaleźliśmy takiej stacji.\nSpróbuj wpisać inną nazwę.");
        }
        adapter.notifyDataSetChanged();
    }

    private ArrayList<Station> favoriteStations() {
        ArrayList<Station> result = new ArrayList<>();
        Set<String> added = new HashSet<>();
        for (Station station : catalog) if (favorites.contains(station.id) && added.add(station.id)) result.add(station);
        for (Station station : mine) if (favorites.contains(station.id) && added.add(station.id)) result.add(station);
        return result;
    }

    private void addTypedStation() {
        String query = search.getText().toString().trim();
        String key = normalize(query);
        if (key.isEmpty()) {
            Toast.makeText(this, "Wpisz nazwę stacji.", Toast.LENGTH_SHORT).show();
            return;
        }
        for (Station station : catalog) {
            if (normalize(station.name).equals(key)) {
                addToMine(station);
                return;
            }
        }
        for (Station station : mine) {
            if (normalize(station.name).equals(key)) {
                Toast.makeText(this, "Ta stacja jest już na liście.", Toast.LENGTH_SHORT).show();
                setTab(false);
                return;
            }
        }
        Toast.makeText(this, "Wybierz pasującą pozycję z katalogu albo dodaj własny strumień.", Toast.LENGTH_LONG).show();
        setTab(true);
    }

    private void addToMine(Station station) {
        if (containsId(station.id)) {
            Toast.makeText(this, "Ta stacja jest już na liście.", Toast.LENGTH_SHORT).show();
            setTab(false);
            return;
        }
        mine.add(station.copy());
        saveMyStations();
        Toast.makeText(this, "Dodano: " + station.name, Toast.LENGTH_SHORT).show();
        setTab(false);
    }

    private void removeFromMine(Station station) {
        for (int i = 0; i < mine.size(); i++) {
            if (mine.get(i).id.equals(station.id)) {
                mine.remove(i);
                break;
            }
        }
        if (station.custom) favorites.remove(station.id);
        saveMyStations();
        saveFavorites();
        updateList();
    }

    private void toggleFavorite(Station station) {
        boolean added;
        if (favorites.contains(station.id)) {
            favorites.remove(station.id);
            added = false;
        } else {
            favorites.add(station.id);
            added = true;
        }
        saveFavorites();
        updateList();
        Toast.makeText(this, added ? "Dodano do ulubionych ♥" : "Usunięto z ulubionych", Toast.LENGTH_SHORT).show();
    }

    private void showCustomStationDialog(String suggestedName) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);
        EditText nameField = new EditText(this);
        nameField.setSingleLine(true);
        nameField.setHint("Nazwa stacji");
        if (suggestedName != null && !suggestedName.isEmpty()) nameField.setText(suggestedName);
        EditText urlField = new EditText(this);
        urlField.setSingleLine(true);
        urlField.setHint("Adres strumienia HTTPS");
        urlField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        form.addView(nameField);
        form.addView(urlField);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Dodaj własną stację")
                .setView(form)
                .setNegativeButton("Anuluj", null)
                .setPositiveButton("Dodaj", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = nameField.getText().toString().trim();
            String url = urlField.getText().toString().trim();
            if (name.isEmpty()) {
                nameField.setError("Wpisz nazwę stacji");
                return;
            }
            if (!url.startsWith("https://")) {
                urlField.setError("Wpisz adres strumienia HTTPS");
                return;
            }
            Station station = new Station("custom:" + System.nanoTime(), name, url, "", 0, "", 0, "", true);
            if (containsName(name)) {
                urlField.setError("Ta nazwa jest już na liście");
                return;
            }
            mine.add(station);
            saveMyStations();
            dialog.dismiss();
            search.setText("");
            setTab(false);
            Toast.makeText(this, "Dodano własną stację.", Toast.LENGTH_SHORT).show();
        }));
        dialog.show();
    }

    private void playStation(Station station) {
        sendCommand(ACTION_PLAY, station);
        Toast.makeText(this, "Uruchamiam: " + station.name, Toast.LENGTH_SHORT).show();
    }

    private void sendCommand(String action, Station station) {
        try {
            Intent intent = new Intent();
            intent.setClassName(this, SERVICE);
            intent.setAction(action);
            if (station != null) {
                intent.putExtra("name", station.name);
                intent.putExtra("url", station.url);
            }
            if (ACTION_PLAY.equals(action) && Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
            else startService(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Nie udało się uruchomić odtwarzacza.", Toast.LENGTH_LONG).show();
        }
    }

    private void refreshPlayerInfo() {
        if (playerStatus == null) return;
        SharedPreferences state = getSharedPreferences("radio_state", MODE_PRIVATE);
        String name = state.getString("name", "");
        boolean playing = state.getBoolean("playing", false);
        String error = state.getString("error", "");
        String artist = state.getString("artist", "");
        String track = state.getString("track", "");
        String program = state.getString("program", "");
        String source = state.getString("info_source", "");
        boolean infoPlus = state.getBoolean("info_plus", false);

        if (!error.trim().isEmpty()) playerStatus.setText("Problem z transmisją: " + error.trim());
        else if (name.trim().isEmpty()) playerStatus.setText("Wybierz stację, by rozpocząć");
        else playerStatus.setText((playing ? "Teraz gra: " : "Wybrano: ") + name);

        String trackText = "";
        if (!track.trim().isEmpty()) trackText = "Utwór: " + track.trim();
        if (!artist.trim().isEmpty()) trackText = trackText.isEmpty() ? "Wykonawca: " + artist.trim() : "Wykonawca: " + artist.trim() + " · " + trackText;
        trackInfo.setText(trackText);
        trackInfo.setVisibility(trackText.isEmpty() ? View.GONE : View.VISIBLE);

        String programText = program.trim().isEmpty() ? "" : "Audycja: " + program.trim();
        programInfo.setText(programText);
        programInfo.setVisibility(programText.isEmpty() ? View.GONE : View.VISIBLE);

        if (!trackText.isEmpty() || !programText.isEmpty()) {
            String detail = infoPlus && !source.trim().isEmpty() ? "Źródło informacji: " + source.trim() : "Metadane ze strumienia stacji";
            infoSource.setText(detail);
        } else {
            infoSource.setText("Ta stacja nie udostępnia teraz informacji o utworze ani audycji.");
        }
    }

    private boolean containsId(String id) {
        for (Station station : mine) if (station.id.equals(id)) return true;
        return false;
    }

    private boolean containsName(String name) {
        String key = normalize(name);
        for (Station station : mine) if (normalize(station.name).equals(key)) return true;
        return false;
    }

    private static String normalize(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "").toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[^a-z0-9]+", "");
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(cardBackground());
        return card;
    }

    private GradientDrawable cardBackground() {
        return rounded(Color.WHITE, Color.rgb(230, 237, 232), 18);
    }

    private GradientDrawable playerBackground() {
        int brand = accentColor();
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{darken(brand, 0.04f), darken(brand, 0.34f)});
        drawable.setCornerRadius(dp(22));
        return drawable;
    }

    private GradientDrawable rounded(int color, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private TextView tab(String label) {
        TextView view = actionButton(label, Color.WHITE, Color.rgb(83, 99, 90), true);
        view.setTextSize(11.5f);
        view.setMaxLines(1);
        view.setPadding(dp(2), 0, dp(2), 0);
        return view;
    }

    private TextView actionButton(String label, int background, int foreground, boolean outlined) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(foreground);
        view.setTextSize(12f);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setBackground(rounded(background, outlined ? Color.rgb(227, 234, 229) : 0, 14));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private void styleTab(TextView view, boolean selected) {
        if (view == null) return;
        view.setTextColor(selected ? Color.WHITE : Color.rgb(83, 99, 90));
        view.setBackground(rounded(selected ? accentColor() : Color.WHITE,
                selected ? 0 : Color.rgb(230, 237, 232), 14));
        view.setElevation(selected ? dp(2) : 0);
    }

    private TextView text(String value, float size, boolean bold) {
        return text(value, size, bold, themeColor(android.R.attr.textColorPrimary, Color.rgb(40, 53, 45)));
    }

    private TextView text(String value, float size, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void addGap(LinearLayout parent, int heightDp) {
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private int themeColor(int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (!getTheme().resolveAttribute(attr, value, true)) return fallback;
        if (value.resourceId != 0) {
            try { return getResources().getColor(value.resourceId, getTheme()); }
            catch (Exception ignored) { return fallback; }
        }
        return value.data;
    }

    private int blend(int color, float alpha) {
        return Color.argb((int) (255 * alpha), Color.red(color), Color.green(color), Color.blue(color));
    }

    private int accentColor() {
        return themeColor(android.R.attr.colorAccent, Color.rgb(24, 125, 93));
    }

    private int darken(int color, float amount) {
        float factor = 1f - amount;
        return Color.rgb((int) (Color.red(color) * factor), (int) (Color.green(color) * factor), (int) (Color.blue(color) * factor));
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private final class StationAdapter extends BaseAdapter {
        @Override public int getCount() { return visible.size(); }
        @Override public Object getItem(int position) { return visible.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            Station station = visible.get(position);
            LinearLayout row;
            TextView title;
            TextView details;
            TextView play;
            TextView action;
            TextView favorite;
            TextView badge;
            if (convertView == null) {
                row = new LinearLayout(PARadioCatalogActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(13), dp(12), dp(13), dp(11));
                row.setBackground(cardBackground());

                LinearLayout top = new LinearLayout(PARadioCatalogActivity.this);
                top.setGravity(Gravity.CENTER_VERTICAL);
                badge = text("", 18f, true, accentColor());
                badge.setGravity(Gravity.CENTER);
                badge.setBackground(rounded(blend(accentColor(), 0.12f), 0, 14));
                top.addView(badge, new LinearLayout.LayoutParams(dp(42), dp(42)));

                LinearLayout labels = new LinearLayout(PARadioCatalogActivity.this);
                labels.setOrientation(LinearLayout.VERTICAL);
                labels.setPadding(dp(10), 0, dp(4), 0);
                title = text("", 15f, true);
                title.setMaxLines(1);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                details = text("", 11.5f, false, Color.rgb(119, 132, 124));
                details.setPadding(0, dp(3), 0, 0);
                labels.addView(title, new LinearLayout.LayoutParams(-1, -2));
                labels.addView(details, new LinearLayout.LayoutParams(-1, -2));
                top.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

                favorite = actionButton("♡", Color.WHITE, Color.rgb(128, 143, 132), true);
                favorite.setTextSize(21f);
                top.addView(favorite, new LinearLayout.LayoutParams(dp(42), dp(40)));
                row.addView(top);

                LinearLayout buttons = new LinearLayout(PARadioCatalogActivity.this);
                buttons.setGravity(Gravity.CENTER_VERTICAL);
                play = actionButton("▶   Słuchaj", accentColor(), Color.WHITE, false);
                LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(0, dp(38), 1f);
                playLp.rightMargin = dp(7);
                buttons.addView(play, playLp);
                action = actionButton("", blend(accentColor(), 0.11f), accentColor(), false);
                buttons.addView(action, new LinearLayout.LayoutParams(dp(103), dp(38)));
                LinearLayout.LayoutParams buttonsLp = new LinearLayout.LayoutParams(-1, -2);
                buttonsLp.topMargin = dp(10);
                row.addView(buttons, buttonsLp);
                row.setTag(new View[]{title, details, play, action, favorite, badge});
            } else {
                row = (LinearLayout) convertView;
                View[] parts = (View[]) row.getTag();
                title = (TextView) parts[0];
                details = (TextView) parts[1];
                play = (TextView) parts[2];
                action = (TextView) parts[3];
                favorite = (TextView) parts[4];
                badge = (TextView) parts[5];
            }
            title.setText(station.name);
            details.setText(station.details());
            badge.setText(station.name.isEmpty() ? "♫" : station.name.substring(0, 1).toUpperCase(Locale.ROOT));
            boolean isFavorite = favorites.contains(station.id);
            favorite.setText(isFavorite ? "♥" : "♡");
            favorite.setTextColor(isFavorite ? Color.rgb(222, 86, 105) : Color.rgb(128, 143, 132));
            favorite.setContentDescription(isFavorite ? "Usuń z ulubionych" : "Dodaj do ulubionych");
            favorite.setOnClickListener(v -> toggleFavorite(station));
            play.setOnClickListener(v -> playStation(station));
            boolean added = containsId(station.id);
            if (selectedTab == 2) {
                action.setText(added ? "✓ Na liście" : "+ Do listy");
                action.setTextColor(added ? Color.rgb(114, 128, 118) : accentColor());
                action.setEnabled(!added);
                action.setOnClickListener(v -> addToMine(station));
            } else if (selectedTab == 1) {
                action.setText(added ? "Usuń z listy" : "+ Do listy");
                action.setTextColor(accentColor());
                action.setEnabled(true);
                action.setOnClickListener(v -> {
                    if (containsId(station.id)) removeFromMine(station);
                    else addToMine(station);
                });
            } else {
                action.setText("Usuń");
                action.setTextColor(Color.rgb(166, 84, 82));
                action.setEnabled(true);
                action.setOnClickListener(v -> removeFromMine(station));
            }
            return row;
        }
    }

    private static final class Station {
        final String id, name, url, homepage, codec, tags;
        final int votes, bitrate;
        final boolean custom;

        Station(String id, String name, String url, String homepage, int votes, String codec, int bitrate, String tags, boolean custom) {
            this.id = id == null ? "" : id;
            this.name = name == null ? "" : name;
            this.url = url == null ? "" : url;
            this.homepage = homepage == null ? "" : homepage;
            this.votes = votes;
            this.codec = codec == null ? "" : codec;
            this.bitrate = bitrate;
            this.tags = tags == null ? "" : tags;
            this.custom = custom;
        }

        static Station fromJson(JSONObject row) {
            return new Station(row.optString("id", row.optString("stationuuid", "")), row.optString("name", ""), row.optString("url", ""), row.optString("homepage", ""), row.optInt("votes"), row.optString("codec", ""), row.optInt("bitrate"), row.optString("tags", ""), row.optBoolean("custom", false));
        }

        Station copy() { return new Station(id, name, url, homepage, votes, codec, bitrate, tags, custom); }

        JSONObject toJson() {
            JSONObject row = new JSONObject();
            try {
                row.put("id", id); row.put("name", name); row.put("url", url); row.put("homepage", homepage);
                row.put("votes", votes); row.put("codec", codec); row.put("bitrate", bitrate); row.put("tags", tags); row.put("custom", custom);
            } catch (Exception ignored) { }
            return row;
        }

        String details() {
            return custom ? "Twoja stacja internetowa" : "Polska stacja radiowa";
        }
    }
}
