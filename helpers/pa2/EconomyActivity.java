package com.ispina.lokalnie;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import org.json.JSONObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Budget meal planning and a filtered view of the existing offers repository. */
public final class EconomyActivity extends Activity {
    private static final String PREFS = "pa_economy";
    private static final String[] FALLBACK_STORES = {
        "Auchan", "Biedronka", "Lidl", "Aldi", "Netto",
        "Action", "MR.DIY", "Rossmann", "Pepco"
    };
    private static final int PURPLE = 0xff6846a4;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private String[] storeOptions = FALLBACK_STORES;
    private LinearLayout planRows, offerRows, dealCard;
    private ScrollView pageScroll;
    private EditText budgetInput, peopleInput, offerQuery;
    private CheckBox perPerson;
    private Spinner tipCategory;
    private TextView storeLabel, planSummary, shoppingListBody, offerStatus, tipBody;
    private Set<String> selectedStores = new LinkedHashSet<>();
    private int offerSearchRevision;
    private Future<?> offerSearchTask;

    private static final String[][] SEARCH_VARIANTS = {
        {"ziemniaki", "ziemniak", "ziemniaków"},
        {"pomidory", "pomidor", "pomidorów"},
        {"jajka", "jajko", "jajek"},
        {"jabłka", "jabłko", "jabłek"},
        {"marchew", "marchewka", "marchewki"},
        {"cebula", "cebuli", "cebulę"},
        {"ogórki", "ogórek", "ogórka"},
        {"mleko", "mleka", "mlekiem"},
        {"fasola", "fasoli", "fasolę"},
        {"ryż", "ryżu", "ryżem"},
        {"makaron", "makaronu", "makaronem"},
        {"kapusta", "kapusty", "kapustę"},
        {"jogurt", "jogurtu", "jogurty"}
    };

    private static final class Meal {
        final String title, ingredients, query;
        final double perPerson;
        Meal(String title, double perPerson, String ingredients, String query) {
            this.title = title;
            this.perPerson = perPerson;
            this.ingredients = ingredients;
            this.query = query;
        }
    }

    private static final Meal[] BREAKFAST = {
        new Meal("Owsianka z jabłkiem i cynamonem", 4.20,
            "płatki owsiane, mleko, jabłko, cynamon", "płatki owsiane"),
        new Meal("Jajka na miękko, pieczywo i pomidor", 5.10,
            "jajka, pieczywo, pomidor", "jajka"),
        new Meal("Kanapki z twarożkiem i ogórkiem", 5.50,
            "pieczywo, twaróg, ogórek", "twaróg")
    };
    private static final Meal[] LUNCH = {
        new Meal("Zupa z soczewicy, marchewki i ziemniaków", 9.10,
            "soczewica, marchew, ziemniaki, cebula, pieczywo", "soczewica"),
        new Meal("Makaron z pomidorami i fasolą", 9.40,
            "makaron, pomidory, fasola, cebula", "makaron"),
        new Meal("Ziemniaki, jajko i surówka z kapusty", 9.60,
            "ziemniaki, jajka, kapusta, marchew", "ziemniaki")
    };
    private static final Meal[] DINNER = {
        new Meal("Ryż z warzywami i jajkiem", 7.90,
            "ryż, mieszanka warzyw, jajko", "ryż"),
        new Meal("Kanapki z pastą z fasoli", 7.40,
            "pieczywo, fasola, cebula, ogórek kiszony", "fasola"),
        new Meal("Placki ziemniaczane z jogurtem", 8.30,
            "ziemniaki, jajko, mąka, jogurt naturalny", "ziemniaki")
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        storeOptions = supportedStores();
        selectedStores = readStores();
        buildUi();
    }

    private void buildUi() {
        pageScroll = new ScrollView(this);
        pageScroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(12), dp(18), dp(28));
        page.setBackgroundColor(color("soft", 0xfff6f3fa));
        pageScroll.addView(page);
        setContentView(pageScroll);

        TextView title = text("Budżet i posiłki", 25, color("ink", 0xff292239));
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        page.addView(title);
        page.addView(text("Zaplanuj dzień, sprawdź składniki w jednym źródle ofert i wybierz sklepy, które Cię interesują.",
            14, color("muted", 0xff645e77)));

        LinearLayout budgetCard = card();
        page.addView(budgetCard, margins(0, 14, 0, 0));
        budgetCard.addView(text("Budżet na dzień", 18, color("ink", 0xff292239)));
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.HORIZONTAL);
        budgetCard.addView(fields, margins(0, 8, 0, 0));
        budgetInput = input(String.valueOf(prefs.getFloat("budget", 30f)), "zł");
        peopleInput = input(String.valueOf(prefs.getInt("people", 1)), "osoby");
        budgetInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        peopleInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        fields.addView(weightedField("Kwota (zł)", budgetInput), new LinearLayout.LayoutParams(0, -2, 1));
        fields.addView(weightedField("Liczba osób", peopleInput), new LinearLayout.LayoutParams(0, -2, 1));
        perPerson = new CheckBox(this);
        perPerson.setText("Budżet jest na osobę");
        perPerson.setTextColor(color("ink", 0xff292239));
        perPerson.setChecked(prefs.getBoolean("per_person", false));
        budgetCard.addView(perPerson);
        Button createPlan = button("Ułóż plan na dziś");
        budgetCard.addView(createPlan, margins(0, 6, 0, 0));
        createPlan.setOnClickListener(v -> createPlan());

        LinearLayout storesCard = card();
        page.addView(storesCard, margins(0, 12, 0, 0));
        storesCard.addView(text("Twoje sklepy", 18, color("ink", 0xff292239)));
        storeLabel = text("", 13, color("muted", 0xff645e77));
        storesCard.addView(storeLabel, margins(0, 4, 0, 0));
        Button storesButton = button("Edytuj listę sklepów");
        storesCard.addView(storesButton, margins(0, 8, 0, 0));
        storesButton.setOnClickListener(v -> editStores());
        updateStoreLabel();

        LinearLayout planCard = card();
        page.addView(planCard, margins(0, 12, 0, 0));
        planCard.addView(text("Jadłospis", 18, color("ink", 0xff292239)));
        planSummary = text("Podaj budżet i liczbę osób, aby ułożyć plan.", 14, color("muted", 0xff645e77));
        planCard.addView(planSummary, margins(0, 4, 0, 0));
        planRows = new LinearLayout(this);
        planRows.setOrientation(LinearLayout.VERTICAL);
        planCard.addView(planRows);
        planCard.addView(text("Lista składników na dziś", 15, color("ink", 0xff292239)), margins(0, 8, 0, 0));
        shoppingListBody = text("Ułóż plan, aby zebrać składniki wszystkich posiłków.", 13, color("muted", 0xff645e77));
        planCard.addView(shoppingListBody, margins(0, 3, 0, 0));
        Button legacySavings = outlineButton("Cele oszczędnościowe i bilety");
        planCard.addView(legacySavings, margins(0, 9, 0, 0));
        legacySavings.setOnClickListener(v -> openActivity("com.ispina.lokalnie.SavingsActivity"));

        dealCard = card();
        page.addView(dealCard, margins(0, 12, 0, 0));
        dealCard.addView(text("Promocje do składników", 18, color("ink", 0xff292239)));
        dealCard.addView(text("Wyszukiwanie korzysta z tego samego repozytorium co Łowca promocji. Wyniki zależą od dostępności gazetki i wybranego sklepu.",
            13, color("muted", 0xff645e77)), margins(0, 3, 0, 5));
        offerQuery = input("", "np. płatki owsiane");
        dealCard.addView(weightedField("Wpisz składnik lub produkt", offerQuery));
        Button findOffers = button("Szukaj w wybranych sklepach");
        dealCard.addView(findOffers, margins(0, 7, 0, 0));
        findOffers.setOnClickListener(v -> findOffers());
        offerStatus = text("", 13, color("muted", 0xff645e77));
        dealCard.addView(offerStatus, margins(0, 6, 0, 0));
        offerRows = new LinearLayout(this);
        offerRows.setOrientation(LinearLayout.VERTICAL);
        dealCard.addView(offerRows);
        Button openDeals = outlineButton("Otwórz Łowcę promocji");
        dealCard.addView(openDeals, margins(0, 8, 0, 0));
        openDeals.setOnClickListener(v -> {
            String query = offerQuery.getText().toString().trim();
            Intent i = new Intent();
            i.setClassName(getPackageName(), "com.ispina.lokalnie.DealsActivity");
            if (!query.isEmpty()) i.putExtra("query", query);
            startActivity(i);
        });

        LinearLayout tipsCard = card();
        page.addView(tipsCard, margins(0, 12, 0, 0));
        tipsCard.addView(text("Tipy według kategorii", 18, color("ink", 0xff292239)));
        String[] categories = {"Zakupy spożywcze", "Dom i energia", "Abonamenty", "Transport"};
        tipCategory = new Spinner(this);
        tipCategory.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categories));
        tipsCard.addView(tipCategory, margins(0, 4, 0, 0));
        tipBody = text("", 14, color("ink", 0xff292239));
        tipsCard.addView(tipBody, margins(0, 4, 0, 0));
        tipCategory.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                showTip(position);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        page.addView(text("Koszt jadłospisu jest orientacyjnym szacunkiem, nie gwarancją ceny w sklepie. Promocje pokazujemy z nazwą źródła i warunkami, jeśli są dostępne.",
            12, color("muted", 0xff645e77)), margins(3, 12, 3, 0));
    }

    private void createPlan() {
        double budget;
        int people;
        try {
            budget = Double.parseDouble(budgetInput.getText().toString().trim().replace(',', '.'));
            people = Integer.parseInt(peopleInput.getText().toString().trim());
        } catch (Exception e) {
            planSummary.setText("Wpisz poprawną kwotę i liczbę osób.");
            return;
        }
        if (budget <= 0 || budget > 10000 || people < 1 || people > 12) {
            planSummary.setText("Podaj budżet większy od zera i liczbę osób od 1 do 12.");
            return;
        }
        prefs.edit().putFloat("budget", (float) budget)
            .putInt("people", people).putBoolean("per_person", perPerson.isChecked()).apply();
        double cap = budget * (perPerson.isChecked() ? people : 1);
        Meal[] best = null;
        double bestCost = Double.MAX_VALUE;
        double target = cap * 0.86;
        for (Meal b : BREAKFAST) for (Meal l : LUNCH) for (Meal d : DINNER) {
            double estimate = (b.perPerson + l.perPerson + d.perPerson) * people * 1.12;
            if (estimate <= cap && Math.abs(target - estimate) < Math.abs(target - bestCost)) {
                best = new Meal[]{b, l, d};
                bestCost = estimate;
            }
        }
        if (best == null) {
            for (Meal b : BREAKFAST) for (Meal l : LUNCH) for (Meal d : DINNER) {
                double estimate = (b.perPerson + l.perPerson + d.perPerson) * people * 1.12;
                if (estimate < bestCost) { best = new Meal[]{b, l, d}; bestCost = estimate; }
            }
        }
        planRows.removeAllViews();
        LinkedHashSet<String> shoppingList = new LinkedHashSet<>();
        for (Meal meal : best) {
            for (String ingredient : meal.ingredients.split(",")) {
                String trimmed = ingredient.trim();
                if (!trimmed.isEmpty()) shoppingList.add(trimmed);
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(10), dp(8), dp(10), dp(8));
            row.setBackground(round(0xfff7f4fb, lineColor(), 14));
            TextView name = text(meal.title, 15, color("ink", 0xff292239));
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            row.addView(name);
            row.addView(text(meal.ingredients, 13, color("muted", 0xff645e77)), margins(0, 2, 0, 0));
            row.addView(text(money(meal.perPerson * people * 1.12) + " · szacunek dla " + people + (people == 1 ? " osoby" : " osób"),
                13, PURPLE), margins(0, 3, 0, 0));
            Button search = outlineButton("Znajdź najniższą cenę: " + meal.query);
            row.addView(search, margins(0, 5, 0, 0));
            search.setOnClickListener(v -> searchFromPlan(meal.query));
            planRows.addView(row, margins(0, 8, 0, 0));
        }
        shoppingListBody.setText(join(shoppingList) + " · bez gramatury; dostosuj ilości do apetytu i zapasów w domu.");
        planSummary.setText("Łącznie około " + money(bestCost) + " z budżetu " + money(cap) + "." +
            (bestCost > cap ? " Najtańszy zapisany plan przekracza limit; sprawdź aktualne oferty albo zwiększ budżet." :
                " Plan mieści się w limicie z 12% rezerwą na różnice cen."));
        offerQuery.setText(best[1].query);
    }

    private void editStores() {
        boolean[] checked = new boolean[storeOptions.length];
        for (int i = 0; i < storeOptions.length; i++) checked[i] = containsStore(selectedStores, storeOptions[i]);
        new AlertDialog.Builder(this)
            .setTitle("Sklepy do sprawdzania")
            .setMultiChoiceItems(storeOptions, checked, (dialog, which, isChecked) -> {
                if (isChecked) selectedStores.add(storeOptions[which]);
                else removeStore(storeOptions[which]);
            })
            .setNegativeButton("Anuluj", (dialog, which) -> {
                selectedStores = readStores();
                updateStoreLabel();
            })
            .setPositiveButton("Zapisz", (dialog, which) -> {
                saveStores();
                updateStoreLabel();
            }).show();
    }

    private Set<String> readStores() {
        String value = prefs.getString("stores", "");
        Set<String> result = new LinkedHashSet<>();
        if (!prefs.getBoolean("stores_configured", false)) {
            result.addAll(Arrays.asList(storeOptions));
        } else {
            for (String item : value.split("\\u001f")) if (!item.trim().isEmpty()) result.add(item.trim());
        }
        return result;
    }
    private void saveStores() {
        StringBuilder out = new StringBuilder();
        for (String store : storeOptions) if (containsStore(selectedStores, store)) {
            if (out.length() > 0) out.append('\u001f');
            out.append(store);
        }
        prefs.edit().putString("stores", out.toString()).putBoolean("stores_configured", true).apply();
    }
    private void removeStore(String store) {
        String found = null;
        for (String item : selectedStores) if (normalize(item).equals(normalize(store))) found = item;
        if (found != null) selectedStores.remove(found);
    }
    private static boolean containsStore(Set<String> stores, String value) {
        for (String store : stores) if (normalize(store).equals(normalize(value))) return true;
        return false;
    }
    private String[] supportedStores() {
        try {
            Object value = Class.forName("com.ispina.lokalnie.deals.DealsParser")
                .getMethod("filters").invoke(null);
            if (value instanceof String[]) {
                ArrayList<String> available = new ArrayList<>();
                for (String store : (String[]) value) {
                    if (store != null && !store.trim().isEmpty() &&
                        !"wszystkie".equals(normalize(store)) && !"allegro".equals(normalize(store))) {
                        available.add(store.trim());
                    }
                }
                if (!available.isEmpty()) return available.toArray(new String[0]);
            }
        } catch (Exception ignored) { }
        return FALLBACK_STORES.clone();
    }
    private void updateStoreLabel() {
        if (storeLabel == null) return;
        storeLabel.setText(selectedStores.isEmpty() ? "Nie wybrano sklepów." :
            "Wybrane: " + join(selectedStores) + ". Możesz zmienić listę w dowolnym momencie.");
    }
    private static String join(Set<String> values) {
        StringBuilder b = new StringBuilder();
        for (String value : values) { if (b.length() > 0) b.append(", "); b.append(value); }
        return b.toString();
    }

    private void findOffers() {
        final String query = offerQuery.getText().toString().trim();
        if (query.isEmpty()) { offerStatus.setText("Wpisz składnik lub produkt."); return; }
        final Set<String> filter = new HashSet<>();
        for (String store : selectedStores) filter.add(normalize(store));
        final boolean hasStoreFilter = !filter.isEmpty();
        // DealsRepository treats comma-separated words as alternative products
        // and searches them together. Keep all grammatical variants in one call
        // so its source queries run in parallel under a single deadline.
        final String repositoryQuery = expandedSearchQuery(query);
        final int revision = ++offerSearchRevision;
        if (offerSearchTask != null) offerSearchTask.cancel(true);
        scrollToOfferResults();
        offerStatus.setText("Szukam ofert i porównuję ceny, uwzględniając odmiany nazwy produktu…");
        offerRows.removeAllViews();
        offerSearchTask = worker.submit(() -> {
            try {
                Class<?> repository = Class.forName("com.ispina.lokalnie.deals.DealsRepository");
                Method search = repository.getMethod("search", Context.class, String.class);
                LinkedHashMap<String, JSONObject> unique = new LinkedHashMap<>();
                LinkedHashSet<String> notes = new LinkedHashSet<>();
                Object result = search.invoke(null, this, repositoryQuery);
                Field offersField = result.getClass().getField("offers");
                List<?> offers = (List<?>) offersField.get(result);
                if (offers != null) for (Object offer : offers) {
                    JSONObject json = (JSONObject) offer.getClass().getMethod("json").invoke(offer);
                    String name = json.optString("name").trim();
                    String store = json.optString("store").trim();
                    if (name.isEmpty() || store.isEmpty()) continue;
                    String key = offerKey(json, name, store);
                    if (!unique.containsKey(key)) unique.put(key, json);
                }
                try {
                    String note = String.valueOf(result.getClass().getField("note").get(result)).trim();
                    if (!note.isEmpty() && !"null".equalsIgnoreCase(note)) notes.add(note);
                } catch (Exception ignored) { }
                boolean stale = false;
                long checked = 0;
                try { stale = result.getClass().getField("stale").getBoolean(result); } catch (Exception ignored) { }
                try { checked = result.getClass().getField("checked").getLong(result); } catch (Exception ignored) { }
                ArrayList<JSONObject> matched = new ArrayList<>();
                ArrayList<JSONObject> outsideSelection = new ArrayList<>();
                for (JSONObject json : unique.values()) {
                    String store = normalize(json.optString("store"));
                    boolean allowed = !hasStoreFilter;
                    if (hasStoreFilter) for (String selected : filter) if (!store.isEmpty() && (store.contains(selected) || selected.contains(store))) {
                        allowed = true; break;
                    }
                    if (allowed) matched.add(json);
                    else outsideSelection.add(json);
                }
                // Preserve DealsRepository's ordering: it ranks by comparable
                // unit price and keeps unlike units (for example kg and szt.)
                // in separate groups instead of comparing them as equal prices.
                boolean showingOtherStores = hasStoreFilter && matched.isEmpty() && !outsideSelection.isEmpty();
                List<JSONObject> shown = showingOtherStores ? outsideSelection : matched;
                ArrayList<JSONObject> finalOffers = new ArrayList<>(shown.subList(0, Math.min(8, shown.size())));
                final String finalNote = join(notes);
                final boolean finalStale = stale;
                final long finalChecked = checked;
                runOnUiThread(() -> {
                    if (revision != offerSearchRevision) return;
                    renderOffers(finalOffers, finalNote, finalStale, finalChecked, showingOtherStores, !hasStoreFilter);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (revision != offerSearchRevision) return;
                    offerStatus.setText("Wyszukiwanie ofert nie powiodło się. Otwórz Łowcę promocji, aby sprawdzić, czy źródła ofert odpowiadają. Szczegóły: " + rootMessage(e));
                });
            }
        });
    }

    private void renderOffers(List<JSONObject> offers, String note, boolean stale, long checked,
                              boolean showingOtherStores, boolean allStores) {
        offerRows.removeAllViews();
        String freshness = "";
        if (checked > 0) {
            String time = new SimpleDateFormat("dd.MM, HH:mm", Locale.getDefault()).format(new Date(checked));
            freshness = " Sprawdzenie: " + time + (stale ? " · dane mogą być nieaktualne." : ".");
        }
        if (offers.isEmpty()) {
            offerStatus.setText("Nie znaleziono dopasowanych ofert w obsługiwanych źródłach. To nie potwierdza braku produktu ani promocji w sklepach." +
                freshness + (note == null || note.trim().isEmpty() ? "" : "\n" + note.trim()));
            return;
        }
        String prefix = showingOtherStores
            ? "Brak dopasowania w wybranych sklepach. Pokazuję oferty z innych obsługiwanych sieci · "
            : allStores ? "Brak filtrów sklepów. Pokazuję oferty ze wszystkich obsługiwanych sieci · "
            : "Znalezione oferty; ceny za tę samą jednostkę sortowane rosnąco · ";
        offerStatus.setText(prefix + offers.size() + freshness +
            (note == null || note.trim().isEmpty() ? "" : "\n" + note.trim()));
        if (showingOtherStores) {
            Button addStores = outlineButton("Dodaj pokazane sklepy do mojej listy");
            offerRows.addView(addStores, margins(0, 5, 0, 0));
            addStores.setOnClickListener(v -> {
                for (JSONObject offer : offers) {
                    String store = offer.optString("store", "").trim();
                    for (String option : storeOptions) if (normalize(option).equals(normalize(store))) selectedStores.add(option);
                }
                saveStores();
                updateStoreLabel();
                addStores.setText("Zapisano sklepy na liście");
            });
        }
        for (JSONObject offer : offers) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(10), dp(8), dp(10), dp(8));
            row.setBackground(round(0xfff7f4fb, lineColor(), 14));
            String name = offer.optString("name", "Oferta");
            String store = offer.optString("store", "Sklep");
            double price = offer.optDouble("price", -1);
            String unit = offer.optString("unit", "").trim();
            double unitPrice = offer.optDouble("unitPrice", 0);
            String validity = offer.optString("start", "");
            String end = offer.optString("end", "");
            if (!end.isEmpty()) validity += (validity.isEmpty() ? "" : " – ") + end;
            row.addView(text(name, 14, color("ink", 0xff292239)));
            String line = store + (price >= 0 ? " · " + money(price) + " za opakowanie" : "") +
                (unitPrice > 0 && !unit.isEmpty() ? " · " + money(unitPrice) + " / " + unit : " · cena jednostkowa nieznana") +
                (offer.optBoolean("card") ? " · karta/aplikacja" : "") +
                (offer.optInt("minimum", 1) > 1 ? " · min. " + offer.optInt("minimum", 1) + " szt." : "") +
                (!offer.optBoolean("details", true) ? " · warunki do sprawdzenia" : "") +
                (validity.isEmpty() ? "" : " · " + validity);
            row.addView(text(line, 12, color("muted", 0xff645e77)), margins(0, 3, 0, 0));
            String url = offer.optString("url", "");
            if (url.startsWith("https://")) {
                Button link = outlineButton("Otwórz źródło oferty");
                row.addView(link, margins(0, 4, 0, 0));
                link.setOnClickListener(v -> {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                    catch (Exception ignored) { }
                });
            }
            offerRows.addView(row, margins(0, 7, 0, 0));
        }
        scrollToOfferItems();
    }

    private void showTip(int position) {
        if (tipBody == null) return;
        String[] tips = {
            "Zaplanuj listę przed zakupami. Porównuj cenę za kilogram lub litr i sprawdzaj warunki promocji, np. kartę albo zakup kilku sztuk.",
            "Ustaw stały dzień przeglądu rachunków. Zmiana temperatury prania i wyłączanie urządzeń w trybie czuwania ograniczają zużycie bez kupowania sprzętu.",
            "Zapisuj datę odnowienia i koszt każdej usługi. Raz w miesiącu sprawdź, czy używasz wszystkich płatnych pakietów.",
            "Porównaj koszt biletu okresowego z liczbą planowanych przejazdów. W aplikacji zostaje też kalkulator biletów 30/90 dni."
        };
        tipBody.setText(tips[Math.max(0, Math.min(position, tips.length - 1))]);
    }

    private void openActivity(String className) {
        try { Intent i = new Intent(); i.setClassName(getPackageName(), className); startActivity(i); }
        catch (Exception e) { new AlertDialog.Builder(this).setMessage("Ten ekran jest niedostępny w tej wersji.").setPositiveButton("OK", null).show(); }
    }
    private EditText input(String value, String hint) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setText(value);
        field.setHint(hint);
        field.setTextColor(color("ink", 0xff292239));
        field.setHintTextColor(color("muted", 0xff645e77));
        field.setPadding(dp(10), dp(7), dp(10), dp(7));
        field.setBackground(round(color("cardColor", Color.WHITE), lineColor(), 12));
        return field;
    }
    private LinearLayout weightedField(String label, EditText field) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(label, 12, color("muted", 0xff645e77));
        box.addView(name);
        box.addView(field, margins(0, 2, 8, 0));
        return box;
    }
    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setBackground(round(PURPLE, PURPLE, 14));
        return b;
    }
    private Button outlineButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(PURPLE);
        b.setAllCaps(false);
        b.setBackground(round(color("cardColor", Color.WHITE), lineColor(), 14));
        return b;
    }
    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(13), dp(14), dp(13));
        box.setBackground(round(color("cardColor", Color.WHITE), lineColor(), 18));
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
    private int color(String method, int fallback) {
        try { return (Integer) Class.forName("com.ispina.lokalnie.NativeUi").getMethod(method, Context.class).invoke(null, this); }
        catch (Exception ignored) { return fallback; }
    }
    private int lineColor() { return color("line", 0xffe5ddec); }
    private GradientDrawable round(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }
    private LinearLayout.LayoutParams margins(int left, int top, int right, int bottom) {
        return new LinearLayout.LayoutParams(-1, -2, 0) {{ setMargins(dp(left), dp(top), dp(right), dp(bottom)); }};
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static String money(double value) {
        return String.format(Locale.GERMANY, "%.2f", value).replace('.', ',') + " zł";
    }
    private void searchFromPlan(String query) {
        offerQuery.setText(query);
        findOffers();
    }
    private void scrollToOfferResults() {
        if (pageScroll == null || dealCard == null) return;
        pageScroll.post(() -> pageScroll.smoothScrollTo(0, Math.max(0, dealCard.getTop() - dp(8))));
    }
    private void scrollToOfferItems() {
        if (pageScroll == null || dealCard == null || offerRows == null) return;
        pageScroll.post(() -> pageScroll.smoothScrollTo(0,
            Math.max(0, dealCard.getTop() + offerRows.getTop() - dp(8))));
    }
    private static String expandedSearchQuery(String query) {
        String normalized = normalize(query);
        for (String[] variants : SEARCH_VARIANTS) {
            for (String variant : variants) {
                if (!normalize(variant).equals(normalized)) continue;
                StringBuilder alternatives = new StringBuilder();
                for (String option : variants) {
                    if (alternatives.length() > 0) alternatives.append(", ");
                    alternatives.append(option);
                }
                return alternatives.toString();
            }
        }
        return query;
    }
    private static String offerKey(JSONObject offer, String name, String store) {
        return normalize(store) + "|" + normalize(name) + "|" + offer.optString("price", "") +
            "|" + normalize(offer.optString("unit", "")) + "|" + offer.optString("start", "") +
            "|" + offer.optString("end", "") + "|" + offer.optString("url", "");
    }
    private static String rootMessage(Exception error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null || message.trim().isEmpty() ? cause.getClass().getSimpleName() : message;
    }
    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT).replace('ł', 'l').trim();
    }
    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
