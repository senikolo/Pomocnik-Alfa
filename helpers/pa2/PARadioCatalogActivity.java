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
import android.widget.Button;
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
import java.util.Collections;
import java.util.Locale;

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
    private final Handler handler = new Handler();
    private boolean showingCatalog = false;
    private EditText search;
    private TextView mineTab;
    private TextView catalogTab;
    private TextView listHeading;
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

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(themeColor(android.R.attr.colorBackground, Color.WHITE));
        root.setPadding(dp(12), dp(8), dp(12), dp(10));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = button("‹");
        back.setTextSize(26f);
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(46)));
        back.setOnClickListener(v -> finish());
        TextView title = text("Radio", 22f, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1f);
        titleLp.leftMargin = dp(8);
        header.addView(title, titleLp);
        root.addView(header);

        LinearLayout playerCard = card();
        playerStatus = text("Radio nie gra", 15f, true);
        trackInfo = text("", 14f, false);
        programInfo = text("", 14f, false);
        infoSource = text("Metadane utworu i audycji zależą od tego, co udostępnia stacja.", 12f, false);
        infoSource.setTextColor(themeColor(android.R.attr.textColorSecondary, Color.DKGRAY));
        playerCard.addView(playerStatus);
        addGap(playerCard, 4);
        playerCard.addView(trackInfo);
        playerCard.addView(programInfo);
        addGap(playerCard, 3);
        playerCard.addView(infoSource);
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        Button toggle = button("Pauza / Wznów");
        Button stop = button("Stop");
        LinearLayout.LayoutParams controlLp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        controlLp.rightMargin = dp(6);
        controls.addView(toggle, controlLp);
        controls.addView(stop, new LinearLayout.LayoutParams(0, dp(42), 1f));
        toggle.setOnClickListener(v -> sendCommand(ACTION_TOGGLE, null));
        stop.setOnClickListener(v -> sendCommand(ACTION_STOP, null));
        LinearLayout.LayoutParams controlsLp = new LinearLayout.LayoutParams(-1, -2);
        controlsLp.topMargin = dp(8);
        playerCard.addView(controls, controlsLp);
        LinearLayout.LayoutParams playerLp = new LinearLayout.LayoutParams(-1, -2);
        playerLp.topMargin = dp(5);
        playerLp.bottomMargin = dp(8);
        root.addView(playerCard, playerLp);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER);
        mineTab = tab("Lista główna");
        catalogTab = tab("Repozytorium 200");
        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        tabLp.rightMargin = dp(5);
        tabs.addView(mineTab, tabLp);
        tabs.addView(catalogTab, new LinearLayout.LayoutParams(0, dp(44), 1f));
        mineTab.setOnClickListener(v -> setTab(false));
        catalogTab.setOnClickListener(v -> setTab(true));
        root.addView(tabs);

        TextView note = text("Repozytorium 200 stacji według głosów Radio Browser, stan na 27.10.2026. Lista główna jest edytowalna, a zmiany zapisują się na tym urządzeniu.", 12f, false);
        note.setTextColor(themeColor(android.R.attr.textColorSecondary, Color.DKGRAY));
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.topMargin = dp(5);
        noteLp.bottomMargin = dp(5);
        root.addView(note, noteLp);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(15f);
        search.setHint("Wpisz nazwę stacji");
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(dp(10), 0, dp(8), 0);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        searchLp.rightMargin = dp(6);
        searchRow.addView(search, searchLp);
        Button add = button("Dodaj");
        searchRow.addView(add, new LinearLayout.LayoutParams(dp(82), dp(46)));
        add.setOnClickListener(v -> addTypedStation());
        root.addView(searchRow);

        Button custom = button("Dodaj własną stację z adresem strumienia");
        LinearLayout.LayoutParams customLp = new LinearLayout.LayoutParams(-1, dp(42));
        customLp.topMargin = dp(4);
        customLp.bottomMargin = dp(5);
        root.addView(custom, customLp);
        custom.setOnClickListener(v -> showCustomStationDialog(search.getText().toString().trim()));

        listHeading = text("Moje stacje", 16f, true);
        LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(-1, -2);
        headingLp.topMargin = dp(2);
        headingLp.bottomMargin = dp(4);
        root.addView(listHeading, headingLp);

        stationList = new ListView(this);
        stationList.setDividerHeight(dp(5));
        stationList.setCacheColorHint(Color.TRANSPARENT);
        stationList.setBackgroundColor(Color.TRANSPARENT);
        adapter = new StationAdapter();
        stationList.setAdapter(adapter);
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

        setTab(false);
        setContentView(root);
    }

    private void setTab(boolean catalogTabSelected) {
        showingCatalog = catalogTabSelected;
        mineTab.setTextColor(themeColor(android.R.attr.textColorPrimary, Color.BLACK));
        catalogTab.setTextColor(themeColor(android.R.attr.textColorPrimary, Color.BLACK));
        mineTab.setBackground(tabBackground(!showingCatalog));
        catalogTab.setBackground(tabBackground(showingCatalog));
        updateList();
    }

    private void updateList() {
        if (adapter == null) return;
        String query = search == null ? "" : normalize(search.getText().toString().trim());
        ArrayList<Station> source = showingCatalog ? catalog : mine;
        visible.clear();
        for (Station station : source) {
            if (query.isEmpty() || normalize(station.name).contains(query) || normalize(station.tags).contains(query)) visible.add(station);
        }
        if (listHeading != null) {
            listHeading.setText(showingCatalog
                    ? "Repozytorium · " + catalog.size() + " stacji polskich"
                    : "Lista główna · " + mine.size());
        }
        if (mineTab != null) mineTab.setText("Lista główna (" + mine.size() + ")");
        if (catalogTab != null) catalogTab.setText("Repozytorium (" + catalog.size() + ")");
        adapter.notifyDataSetChanged();
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
        saveMyStations();
        updateList();
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
        else if (name.trim().isEmpty()) playerStatus.setText("Radio nie gra");
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
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(themeColor(android.R.attr.colorBackground, Color.WHITE));
        drawable.setCornerRadius(dp(12));
        drawable.setStroke(dp(1), blend(themeColor(android.R.attr.textColorPrimary, Color.BLACK), 0.18f));
        return drawable;
    }

    private GradientDrawable tabBackground(boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(12));
        drawable.setColor(selected ? blend(themeColor(android.R.attr.colorAccent, Color.rgb(24, 105, 90)), 0.18f) : Color.TRANSPARENT);
        drawable.setStroke(dp(1), selected ? themeColor(android.R.attr.colorAccent, Color.rgb(24, 105, 90)) : blend(themeColor(android.R.attr.textColorPrimary, Color.BLACK), 0.2f));
        return drawable;
    }

    private Button tab(String label) {
        Button view = button(label);
        view.setAllCaps(false);
        return view;
    }

    private Button button(String label) {
        Button view = new Button(this);
        view.setText(label);
        view.setAllCaps(false);
        view.setTextSize(13f);
        return view;
    }

    private TextView text(String value, float size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(themeColor(android.R.attr.textColorPrimary, Color.BLACK));
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
            Button play;
            Button action;
            if (convertView == null) {
                row = new LinearLayout(PARadioCatalogActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(11), dp(8), dp(11), dp(8));
                row.setBackground(cardBackground());
                title = text("", 15f, true);
                row.addView(title, new LinearLayout.LayoutParams(-1, -2));
                LinearLayout buttons = new LinearLayout(PARadioCatalogActivity.this);
                buttons.setGravity(Gravity.CENTER_VERTICAL);
                details = text("", 11f, false);
                details.setTextColor(themeColor(android.R.attr.textColorSecondary, Color.DKGRAY));
                buttons.addView(details, new LinearLayout.LayoutParams(0, dp(42), 1f));
                play = button("Odtwórz");
                buttons.addView(play, new LinearLayout.LayoutParams(dp(92), dp(42)));
                action = button("");
                LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(dp(92), dp(38));
                actionLp.gravity = Gravity.RIGHT;
                actionLp.topMargin = dp(3);
                row.addView(buttons);
                row.addView(action, actionLp);
                row.setTag(new View[]{title, details, play, action});
            } else {
                row = (LinearLayout) convertView;
                View[] parts = (View[]) row.getTag();
                title = (TextView) parts[0];
                details = (TextView) parts[1];
                play = (Button) parts[2];
                action = (Button) parts[3];
            }
            title.setText(station.name);
            details.setText(station.details());
            play.setOnClickListener(v -> playStation(station));
            if (showingCatalog) {
                boolean added = containsId(station.id);
                action.setText(added ? "Dodana" : "+ Dodaj");
                action.setEnabled(!added);
                action.setOnClickListener(v -> addToMine(station));
            } else {
                action.setText("Usuń");
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
            ArrayList<String> parts = new ArrayList<>();
            if (!codec.isEmpty()) parts.add(codec);
            if (bitrate > 0) parts.add(bitrate + " kb/s");
            if (votes > 0) parts.add(votes + " głosów");
            if (custom) parts.add("własna stacja");
            return parts.isEmpty() ? "Radio internetowe" : join(parts, " · ");
        }

        private static String join(ArrayList<String> values, String delimiter) {
            StringBuilder out = new StringBuilder();
            for (String value : values) {
                if (out.length() > 0) out.append(delimiter);
                out.append(value);
            }
            return out.toString();
        }
    }
}
