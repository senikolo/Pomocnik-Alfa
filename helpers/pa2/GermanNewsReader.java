package com.ispina.lokalnie;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Creates a local lesson file and opens it in the existing Alfa Reader. */
public final class GermanNewsReader {
    private GermanNewsReader() { }

    public static Intent createIntent(Context context, GermanNewsReceiver.Article article, String level)
            throws Exception {
        String safeLevel = "B2".equals(level) ? "B2" : "B1";
        File directory = new File(context.getFilesDir(), "german-news-reader");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("Nie można przygotować pliku lekcji.");
        }
        File lesson = new File(directory, "lektion-" + System.currentTimeMillis() + ".html");
        String html = html(article, safeLevel);
        try (FileOutputStream out = new FileOutputStream(lesson)) {
            out.write(html.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        Intent open = new Intent(Intent.ACTION_VIEW);
        open.setClassName(context.getPackageName(), "com.ispina.lokalnie.ReaderActivity");
        open.setData(Uri.fromFile(lesson));
        return open;
    }

    private static String html(GermanNewsReceiver.Article article, String level) {
        StringBuilder page = new StringBuilder(1600);
        page.append("<!doctype html><html lang=\"de\"><head><meta charset=\"utf-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
            .append("<title>").append(escape(article.title)).append("</title>")
            .append("<style>body{font-family:sans-serif;line-height:1.65;padding:8px;max-width:42em;margin:auto}")
            .append("h1{font-size:1.5em}h2{font-size:1.2em;margin-top:1.5em}.meta{color:#666}")
            .append(".vocab{padding-left:1.2em}hr{border:0;border-top:1px solid #bbb}</style></head><body>")
            .append("<h1>Deutsch · Wirtschaft</h1><p class=\"meta\">Niveau ")
            .append(level).append(" · ").append(escape(article.date.isEmpty()
                ? "Aktuelle Nachricht" : article.date)).append("</p><hr><h2>")
            .append(escape(article.title)).append("</h2><p>")
            .append(escape(article.studyText(level))).append("</p><h2>Wortschatz · Słownictwo</h2><ul class=\"vocab\">");

        String[] terms = article.glossary(level).split(" · ");
        for (String term : terms) page.append("<li>").append(escape(term)).append("</li>");
        page.append("</ul><h2>Übung · Ćwiczenie</h2>")
            .append("<p>Was ist die wichtigste Nachricht? Fasse sie in einem Satz zusammen.</p>")
            .append("<p class=\"meta\">Źródło materiału: tagesschau.de · RSS Wirtschaft</p>")
            .append("</body></html>");
        return page.toString();
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
