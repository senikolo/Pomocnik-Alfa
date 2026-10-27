package com.ispina.lokalnie;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.Html;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Daily economic-news lesson from the public tagesschau.de Wirtschaft RSS feed. */
public final class GermanNewsReceiver extends BroadcastReceiver {
    public static final String ACTION_DAILY = "com.ispina.lokalnie.action.DAILY_GERMAN";
    private static final String PREFS = "pa_german";
    private static final String CHANNEL = "daily_german_news";
    private static final int REQUEST_CODE = 77412;
    private static final int NOTIFICATION_ID = 77413;
    private static final String FEED = "https://www.tagesschau.de/wirtschaft/index~rss2.xml";

    public static final class Article {
        public final String title, description, link, date;
        Article(String title, String description, String link, String date) {
            this.title = title == null ? "" : title.trim();
            this.description = clean(description);
            this.link = link == null ? "" : link.trim();
            this.date = date == null ? "" : date.trim();
        }
        public String studyText(String level) {
            String text = description.isEmpty() ? title : description;
            String[] sentences = text.split("(?<=[.!?])\\s+");
            int maxSentences = "B1".equals(level) ? 2 : 4;
            int maxChars = "B1".equals(level) ? 360 : 760;
            StringBuilder out = new StringBuilder();
            for (String sentence : sentences) {
                String s = sentence.trim();
                if (s.isEmpty()) continue;
                if (out.length() > 0 && (out.length() + s.length() + 1 > maxChars || countSentences(out) >= maxSentences)) break;
                if (out.length() > 0) out.append(' ');
                out.append(s);
            }
            if (out.length() == 0) out.append(text.substring(0, Math.min(text.length(), maxChars)));
            return out.toString();
        }
        public String glossary(String level) {
            String text = (title + " " + description).toLowerCase(Locale.ROOT);
            LinkedHashMap<String, String> terms = new LinkedHashMap<>();
            terms.put("Umsatz", "obroty");
            terms.put("Gewinn", "zysk");
            terms.put("Verlust", "strata");
            terms.put("Verbraucher", "konsumenci");
            terms.put("Unternehmen", "przedsiębiorstwa");
            terms.put("Inflation", "inflacja");
            terms.put("Zinsen", "odsetki / stopy procentowe");
            terms.put("Preise", "ceny");
            terms.put("Löhne", "wynagrodzenia");
            terms.put("Arbeitsplätze", "miejsca pracy");
            terms.put("Energie", "energia");
            terms.put("Konjunktur", "koniunktura");
            terms.put("Wirtschaft", "gospodarka");
            ArrayList<String> found = new ArrayList<>();
            for (Map.Entry<String, String> term : terms.entrySet()) {
                if (text.contains(term.getKey().toLowerCase(Locale.ROOT))) found.add(term.getKey() + " – " + term.getValue());
                if (found.size() >= ("B1".equals(level) ? 4 : 6)) break;
            }
            if (found.isEmpty()) found.add("die Wirtschaft – gospodarka");
            found.add("der Verbraucher – konsument");
            return join(found);
        }
    }

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            if (prefs.getBoolean("enabled", false)) {
                schedule(context, prefs.getInt("hour", 8), prefs.getInt("minute", 0));
            }
            return;
        }
        if (!ACTION_DAILY.equals(action) || !prefs.getBoolean("enabled", false)) return;
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) return;
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                Article article = fetchLatest(context);
                if (article != null) notifyLesson(context, article, prefs.getString("level", "B1"));
            } catch (Exception ignored) {
                // Keep the notification quiet when the network or feed is unavailable.
            } finally { pending.finish(); }
        }, "PA-German-News").start();
    }

    public static void schedule(Context context, int hour, int minute) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, Math.max(0, Math.min(23, hour)));
        next.set(Calendar.MINUTE, Math.max(0, Math.min(59, minute)));
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) next.add(Calendar.DAY_OF_YEAR, 1);
        alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(),
            AlarmManager.INTERVAL_DAY, pendingBroadcast(context));
    }

    public static void cancel(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) alarms.cancel(pendingBroadcast(context));
    }

    private static PendingIntent pendingBroadcast(Context context) {
        Intent intent = new Intent(context, GermanNewsReceiver.class);
        intent.setAction(ACTION_DAILY);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags);
    }

    public static Article fetchLatest(Context context) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(FEED).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent", "PomocnikAlfa/2.0 (German practice; RSS reader)");
        connection.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml");
        try {
            int code = connection.getResponseCode();
            if (code != 200) throw new java.io.IOException("Feed HTTP " + code);
            try (InputStream input = new BufferedInputStream(connection.getInputStream())) {
                XmlPullParser parser = Xml.newPullParser();
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
                parser.setInput(input, "UTF-8");
                int event;
                while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                    if (event != XmlPullParser.START_TAG || !"item".equalsIgnoreCase(parser.getName())) continue;
                    String title = "", description = "", link = "", date = "";
                    int depth = parser.getDepth();
                    while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                        if (event == XmlPullParser.END_TAG && parser.getDepth() == depth) break;
                        if (event != XmlPullParser.START_TAG) continue;
                        String name = parser.getName();
                        if ("title".equalsIgnoreCase(name)) title = parser.nextText();
                        else if ("description".equalsIgnoreCase(name)) description = parser.nextText();
                        else if ("link".equalsIgnoreCase(name)) link = parser.nextText();
                        else if ("pubDate".equalsIgnoreCase(name)) date = parser.nextText();
                    }
                    if (!title.trim().isEmpty() && link.startsWith("https://")) {
                        Article article = new Article(title, description, link, date);
                        save(context, article);
                        return article;
                    }
                }
            }
            throw new java.io.IOException("Feed nie zawiera artykułu.");
        } finally { connection.disconnect(); }
    }

    public static Article cached(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String title = p.getString("news_title", "");
        if (title.isEmpty()) return null;
        return new Article(title, p.getString("news_description", ""),
            p.getString("news_link", ""), p.getString("news_date", ""));
    }

    private static void save(Context context, Article article) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("news_title", article.title)
            .putString("news_description", article.description)
            .putString("news_link", article.link)
            .putString("news_date", article.date)
            .putLong("news_cached_at", System.currentTimeMillis())
            .apply();
    }

    private static void notifyLesson(Context context, Article article, String level) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Niemiecki · wiadomości",
                NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Codzienny tekst gospodarczy do nauki niemieckiego.");
            manager.createNotificationChannel(channel);
        }
        Intent open;
        try {
            open = GermanNewsReader.createIntent(context, article, level);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        } catch (Exception e) {
            open = new Intent(context, GermanNewsActivity.class);
        }
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent content = PendingIntent.getActivity(context, REQUEST_CODE + 1, open, flags);
        String body = article.studyText(level);
        Notification.Builder builder = new Notification.Builder(context, CHANNEL);
        Notification notification = builder
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Deutsch B1/B2 · Wirtschaft")
            .setContentText(article.title)
            .setStyle(new Notification.BigTextStyle().bigText(body + "\n\n" + article.glossary(level)))
            .setContentIntent(content)
            .setAutoCancel(true)
            .build();
        manager.notify(NOTIFICATION_ID, notification);
    }

    private static String clean(String html) {
        if (html == null) return "";
        String plain;
        plain = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString();
        return plain.replaceAll("\\s+", " ").trim();
    }
    private static int countSentences(CharSequence text) {
        String s = text.toString().trim();
        if (s.isEmpty()) return 0;
        return s.split("[.!?]+").length;
    }
    private static String join(ArrayList<String> items) {
        StringBuilder b = new StringBuilder();
        for (String item : items) { if (b.length() > 0) b.append(" · "); b.append(item); }
        return b.toString();
    }
}
