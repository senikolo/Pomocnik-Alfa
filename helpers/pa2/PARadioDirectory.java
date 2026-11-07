package com.ispina.lokalnie;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * Online directory for Polish radio: stations located in Poland and Polish-language
 * stations located abroad. Existing bundled stations are retained as an offline fallback.
 *
 * Radio Browser is a community directory, not a complete licensing registry. Missing
 * stations and regional restrictions are possible, even when a stream is listed as working.
 */
final class PARadioDirectory {
    static final String CACHE_FILE = "pa_radio_directory_v1.json";
    static final long REFRESH_INTERVAL_MS = 24L * 60 * 60 * 1000;
    private static final int PAGE_SIZE = 1000;
    private static final int MAX_ROWS_PER_QUERY = 15000;
    private static final int MAX_RESPONSE_BYTES = 12 * 1024 * 1024;
    private static final String[] MIRRORS = {
            "https://de1.api.radio-browser.info",
            "https://nl1.api.radio-browser.info"
    };
    // The language search deliberately includes multilingual records.
    private static final String[] QUERIES = {
            "/json/stations/bycountrycodeexact/PL",
            "/json/stations/bylanguage/polish",
            "/json/stations/bylanguage/polski"
    };

    private PARadioDirectory() { }

    static JSONArray readCached(Context context) {
        File file = new File(context.getFilesDir(), CACHE_FILE);
        if (!file.isFile() || file.length() > MAX_RESPONSE_BYTES) return new JSONArray();
        try (FileInputStream input = new FileInputStream(file)) {
            JSONObject root = new JSONObject(readUtf8(input, MAX_RESPONSE_BYTES));
            JSONArray stations = root.optJSONArray("stations");
            return stations == null ? new JSONArray() : stations;
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    static long lastRefresh(Context context) {
        File file = new File(context.getFilesDir(), CACHE_FILE);
        return file.isFile() ? file.lastModified() : 0L;
    }

    static void writeCached(Context context, JSONArray stations) throws IOException {
        File target = new File(context.getFilesDir(), CACHE_FILE);
        File temp = new File(context.getFilesDir(), CACHE_FILE + ".tmp");
        JSONObject wrapper = new JSONObject();
        try {
            wrapper.put("source", "Radio Browser");
            wrapper.put("updatedEpochMs", System.currentTimeMillis());
            wrapper.put("stations", stations);
        } catch (Exception ex) {
            throw new IOException("Nie udało się przygotować katalogu", ex);
        }
        byte[] content = wrapper.toString().getBytes("UTF-8");
        if (content.length > MAX_RESPONSE_BYTES) throw new IOException("Katalog przekracza limit pamięci");
        try (FileOutputStream output = new FileOutputStream(temp)) {
            output.write(content);
            output.getFD().sync();
        }
        if (!temp.renameTo(target)) {
            if (target.exists() && !target.delete()) throw new IOException("Nie udało się zastąpić pamięci katalogu");
            if (!temp.renameTo(target)) throw new IOException("Nie udało się zapisać katalogu");
        }
    }

    static JSONArray fetchDirectory() throws IOException {
        LinkedHashMap<String, JSONObject> found = new LinkedHashMap<>();
        HashSet<String> streams = new HashSet<>();
        int plRows = 0;
        for (int queryIndex = 0; queryIndex < QUERIES.length; queryIndex++) {
            for (int offset = 0; offset < MAX_ROWS_PER_QUERY; offset += PAGE_SIZE) {
                String path = QUERIES[queryIndex]
                        + "?hidebroken=true&is_https=true&order=votes&reverse=true"
                        + "&limit=" + PAGE_SIZE + "&offset=" + offset;
                JSONArray page = fetchPage(path);
                if (page == null) {
                    // Never silently replace a complete cached directory with partial data.
                    throw new IOException("Nie można pobrać wszystkich części katalogu");
                }
                for (int i = 0; i < page.length(); i++) {
                    JSONObject source = page.optJSONObject(i);
                    if (source == null || source.optBoolean("lastcheckok", true) == false) continue;
                    String name = source.optString("name", "").trim();
                    String country = source.optString("countrycode", "").trim().toUpperCase(Locale.ROOT);
                    String url = httpsStream(source.optString("url_resolved", ""));
                    if (url.isEmpty()) url = httpsStream(source.optString("url", ""));
                    if (name.isEmpty() || url.isEmpty()) continue;
                    // Foreign stations in the language query are labelled separately.
                    if (queryIndex != 0 && "PL".equals(country)) continue;
                    if (queryIndex == 0 && !"PL".equals(country)) continue;
                    String id = source.optString("stationuuid", "").trim();
                    if (id.isEmpty()) id = "url:" + url;
                    if (found.containsKey(id) || !streams.add(url)) continue;
                    JSONObject row = new JSONObject();
                    try {
                        row.put("id", id);
                        row.put("name", name);
                        row.put("url", url);
                        row.put("homepage", source.optString("homepage", ""));
                        row.put("favicon", httpsStream(source.optString("favicon", "")));
                        row.put("codec", source.optString("codec", ""));
                        row.put("bitrate", source.optInt("bitrate", 0));
                        row.put("votes", source.optInt("votes", 0));
                        row.put("tags", source.optString("tags", ""));
                        row.put("countryCode", country);
                        row.put("polishAbroad", queryIndex != 0);
                        found.put(id, row);
                        if (queryIndex == 0) plRows++;
                    } catch (Exception ignored) {
                        streams.remove(url);
                    }
                }
                if (page.length() < PAGE_SIZE) break;
            }
        }
        if (plRows < 50) throw new IOException("Zbyt mało polskich stacji. Zachowano poprzedni katalog.");
        JSONArray array = new JSONArray();
        for (JSONObject station : found.values()) array.put(station);
        return array;
    }

    private static JSONArray fetchPage(String path) {
        for (String mirror : MIRRORS) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(mirror + path).openConnection();
                connection.setConnectTimeout(7500);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "PomocnikAlfa/2.0 RadioDirectory");
                connection.setInstanceFollowRedirects(true);
                if (connection.getResponseCode() != 200) continue;
                if (!"https".equalsIgnoreCase(connection.getURL().getProtocol())) continue;
                try (InputStream input = connection.getInputStream()) {
                    return new JSONArray(readUtf8(input, MAX_RESPONSE_BYTES));
                }
            } catch (Exception ignored) {
                // Retry once on a different public Radio Browser mirror.
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        return null;
    }

    private static String httpsStream(String value) {
        String candidate = value == null ? "" : value.trim();
        try {
            URL url = new URL(candidate);
            if ("https".equalsIgnoreCase(url.getProtocol()) && !url.getHost().isEmpty()) {
                return candidate;
            }
        } catch (Exception ignored) { }
        return "";
    }

    private static String readUtf8(InputStream stream, int maximumBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            if (output.size() + count > maximumBytes) throw new IOException("Odpowiedź zbyt duża");
            output.write(buffer, 0, count);
        }
        return output.toString("UTF-8");
    }
}
