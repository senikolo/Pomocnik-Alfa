package pl.alfalauncher.seven;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public class WidgetBoardActivity extends Activity {
    private static final int HOST_ID = 7007;
    private static final int REQ_PICK = 201;
    private static final int REQ_CONFIGURE = 202;
    private static final String PREFS = "alfa_launcher_7";
    private static final String KEY_WIDGET_IDS = "system_widget_ids";

    private AppWidgetHost host;
    private AppWidgetManager manager;
    private LinearLayout widgetContainer;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        host = new AppWidgetHost(this, HOST_ID);
        manager = AppWidgetManager.getInstance(this);
        setContentView(buildUi());
        loadWidgets();
    }

    @Override protected void onStart() {
        super.onStart();
        try { host.startListening(); } catch (Exception ignored) {}
    }

    @Override protected void onStop() {
        try { host.stopListening(); } catch (Exception ignored) {}
        super.onStop();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(18, 26, 31));
        root.setPadding(dp(12), dp(10), dp(12), dp(12));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Widżety systemowe");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button add = new Button(this);
        add.setText("+ Dodaj");
        add.setAllCaps(false);
        add.setOnClickListener(v -> pickWidget());
        top.addView(add, new LinearLayout.LayoutParams(dp(100), dp(48)));
        root.addView(top);

        TextView hint = new TextView(this);
        hint.setText("Tu możesz dodać zwykłe widżety Androida 7. Przytrzymaj widżet, aby go usunąć.");
        hint.setTextColor(Color.rgb(176, 197, 203));
        hint.setTextSize(12);
        hint.setPadding(0, dp(4), 0, dp(10));
        root.addView(hint);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        widgetContainer = new LinearLayout(this);
        widgetContainer.setOrientation(LinearLayout.VERTICAL);
        widgetContainer.setPadding(0, dp(4), 0, dp(24));
        scroll.addView(widgetContainer);

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void pickWidget() {
        int id = host.allocateAppWidgetId();
        Intent pick = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
        pick.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        try {
            startActivityForResult(pick, REQ_PICK);
        } catch (Exception e) {
            host.deleteAppWidgetId(id);
            Toast.makeText(this, "Ten telefon nie udostępnia wyboru widżetów.", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        int id = data != null
                ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                : -1;

        if (requestCode == REQ_PICK) {
            if (resultCode != RESULT_OK || id < 0) {
                if (id >= 0) host.deleteAppWidgetId(id);
                return;
            }
            AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
            if (info != null && info.configure != null) {
                Intent configure = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
                configure.setComponent(info.configure);
                configure.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
                try {
                    startActivityForResult(configure, REQ_CONFIGURE);
                } catch (Exception e) {
                    addWidget(id);
                }
            } else {
                addWidget(id);
            }
        } else if (requestCode == REQ_CONFIGURE) {
            if (resultCode == RESULT_OK && id >= 0) addWidget(id);
            else if (id >= 0) host.deleteAppWidgetId(id);
        }
    }

    private void addWidget(int id) {
        AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
        if (info == null) {
            host.deleteAppWidgetId(id);
            return;
        }
        saveWidgetId(id);
        addHostView(id, info);
    }

    private void loadWidgets() {
        widgetContainer.removeAllViews();
        Set<String> ids = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getStringSet(KEY_WIDGET_IDS, new LinkedHashSet<String>());
        ArrayList<Integer> stale = new ArrayList<>();

        for (String s : ids) {
            try {
                int id = Integer.parseInt(s);
                AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
                if (info != null) addHostView(id, info);
                else stale.add(id);
            } catch (Exception ignored) {}
        }
        for (int id : stale) removeWidgetId(id, false);

        if (widgetContainer.getChildCount() == 0) {
            TextView empty = new TextView(this);
            empty.setText("Brak widżetów. Dotknij „+ Dodaj”, aby umieścić pierwszy.");
            empty.setTextColor(Color.rgb(170, 194, 201));
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(18), dp(40), dp(18), dp(40));
            widgetContainer.addView(empty);
        }
    }

    private void addHostView(final int id, AppWidgetProviderInfo info) {
        AppWidgetHostView view = host.createView(this, id, info);
        view.setAppWidget(id, info);
        view.setPadding(dp(4), dp(6), dp(4), dp(6));
        view.setOnLongClickListener(v -> {
            confirmRemove(id);
            return true;
        });

        int minHeight = Math.max(dp(90), dp(Math.max(90, info.minHeight)));
        widgetContainer.addView(view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, minHeight));
    }

    private void confirmRemove(final int id) {
        new AlertDialog.Builder(this)
                .setTitle("Usunąć widżet?")
                .setMessage("Widżet zniknie z pulpitu Alfa Launcher.")
                .setNegativeButton("Anuluj", null)
                .setPositiveButton("Usuń", (d, w) -> {
                    removeWidgetId(id, true);
                    loadWidgets();
                })
                .show();
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
        if (deleteHostId) {
            try { host.deleteAppWidgetId(id); } catch (Exception ignored) {}
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
