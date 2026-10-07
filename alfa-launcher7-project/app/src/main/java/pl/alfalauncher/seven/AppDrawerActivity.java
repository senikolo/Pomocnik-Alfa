package pl.alfalauncher.seven;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class AppDrawerActivity extends Activity {
    private final ArrayList<AppEntry> allEntries = new ArrayList<>();
    private final ArrayList<AppEntry> visibleEntries = new ArrayList<>();
    private AppAdapter adapter;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        loadApps();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(20, 27, 31));
        root.setPadding(dp(10), dp(8), dp(10), 0);

        TextView title = new TextView(this);
        title.setText("Aplikacje");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setPadding(dp(6), dp(5), dp(6), dp(8));
        root.addView(title);

        EditText search = new EditText(this);
        search.setHint("Szukaj aplikacji");
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.rgb(145, 163, 170));
        search.setTextSize(16);
        root.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        GridView grid = new GridView(this);
        grid.setNumColumns(phoneColumns());
        grid.setVerticalSpacing(dp(8));
        grid.setHorizontalSpacing(dp(2));
        grid.setPadding(dp(2), dp(9), dp(2), dp(14));
        grid.setClipToPadding(false);

        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> launch(visibleEntries.get(position)));
        root.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { filter(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        setContentView(root);
    }

    private void loadApps() {
        allEntries.clear();
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN, null);
        q.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(q, 0);

        for (ResolveInfo ri : apps) {
            if (getPackageName().equals(ri.activityInfo.packageName)) continue;
            allEntries.add(new AppEntry(ri, ri.loadLabel(pm).toString()));
        }

        Collections.sort(allEntries, new Comparator<AppEntry>() {
            @Override public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        visibleEntries.clear();
        visibleEntries.addAll(allEntries);
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void filter(String q) {
        String needle = q.trim().toLowerCase(new Locale("pl", "PL"));
        visibleEntries.clear();
        if (needle.isEmpty()) visibleEntries.addAll(allEntries);
        else {
            for (AppEntry e : allEntries) {
                if (e.label.toLowerCase(new Locale("pl", "PL")).contains(needle)) {
                    visibleEntries.add(e);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void launch(AppEntry e) {
        Intent i = getPackageManager().getLaunchIntentForPackage(e.info.activityInfo.packageName);
        if (i == null) {
            i = new Intent(Intent.ACTION_MAIN);
            i.setClassName(e.info.activityInfo.packageName, e.info.activityInfo.name);
        }
        startActivity(i);
    }

    private int phoneColumns() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float widthDp = dm.widthPixels / dm.density;
        return widthDp < 360f ? 3 : 4;
    }

    private class AppAdapter extends BaseAdapter {
        private final PackageManager pm = getPackageManager();
        @Override public int getCount() { return visibleEntries.size(); }
        @Override public Object getItem(int position) { return visibleEntries.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public android.view.View getView(int position, android.view.View convertView, ViewGroup parent) {
            LinearLayout cell;
            ImageView icon;
            TextView label;

            if (convertView == null) {
                cell = new LinearLayout(AppDrawerActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(dp(3), dp(7), dp(3), dp(7));

                icon = new ImageView(AppDrawerActivity.this);
                icon.setId(android.R.id.icon);
                cell.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

                label = new TextView(AppDrawerActivity.this);
                label.setId(android.R.id.text1);
                label.setTextColor(Color.WHITE);
                label.setTextSize(11);
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(2);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
                lp.setMargins(0, dp(4), 0, 0);
                cell.addView(label, lp);
            } else {
                cell = (LinearLayout) convertView;
                icon = (ImageView) cell.findViewById(android.R.id.icon);
                label = (TextView) cell.findViewById(android.R.id.text1);
            }

            AppEntry e = visibleEntries.get(position);
            icon.setImageDrawable(e.info.loadIcon(pm));
            label.setText(e.label);
            return cell;
        }
    }

    private static class AppEntry {
        final ResolveInfo info;
        final String label;
        AppEntry(ResolveInfo i, String l) { info = i; label = l; }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
