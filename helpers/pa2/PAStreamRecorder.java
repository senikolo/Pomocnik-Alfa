package com.ispina.lokalnie;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/** Copies a direct radio stream or complete HLS segments without transcoding. */
final class PAStreamRecorder {
    private static final int TIMEOUT_MS = 6500;
    private static final int MAX_PLAYLIST_BYTES = 1024 * 1024;
    private static final int MAX_SEGMENT_BYTES = 16 * 1024 * 1024;
    private static final int TV_BITRATE = 1_600_000;

    interface Destination {
        OutputStream open(String extension, String mime) throws IOException;
    }

    private final String referer;
    private final BooleanSupplier stopped;

    PAStreamRecorder(String referer, BooleanSupplier stopped) {
        this.referer = referer;
        this.stopped = stopped;
    }

    void record(String address, boolean television, Destination destination) throws IOException {
        URL source = new URL(address);
        Playlist playlist;
        URL playlistSource = source;
        try (Response response = open(source)) {
            byte[] header = new byte[12];
            response.input.mark(header.length);
            int length = 0;
            while (length < header.length && !stopped.getAsBoolean()) {
                int count = response.input.read(header, length, header.length - length);
                if (count < 0) break;
                length += count;
            }
            response.input.reset();
            if (stopped.getAsBoolean()) return;
            String prefix = new String(header, 0, length, StandardCharsets.UTF_8);
            if (!prefix.replace("\uFEFF", "").startsWith("#EXTM3U")) {
                if (television) throw unsupported("Kanał nie udostępnia obsługiwanego strumienia HLS.");
                recordRadio(response, prefix, destination);
                return;
            }
            playlist = parse(response.url(), readPlaylist(response.input));
        }
        Variant variant = null;
        for (Variant candidate : playlist.variants) {
            if (playlist.externalAudio.contains(candidate.audioGroup)) continue;
            if (variant == null || bitrateScore(candidate) < bitrateScore(variant)) variant = candidate;
        }
        if (!playlist.variants.isEmpty()) {
            if (variant == null) throw unsupported("Kanał nadaje obraz i dźwięk w oddzielnych strumieniach.");
            if (stopped.getAsBoolean()) return;
            playlistSource = variant.url;
            playlist = fetchPlaylist(playlistSource);
        }
        recordHls(playlistSource, playlist, television, destination);
    }

    private static int bitrateScore(Variant variant) {
        if (variant.bitrate <= 0) return Integer.MAX_VALUE;
        if (variant.bitrate <= TV_BITRATE) return TV_BITRATE - variant.bitrate;
        return variant.bitrate;
    }

    private void recordRadio(Response response, String prefix, Destination destination) throws IOException {
        String type = response.connection.getContentType();
        if (type == null) type = "";
        type = type.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (prefix.trim().startsWith("<") || type.equals("text/html")) {
            throw unsupported("Adres stacji otwiera stronę zamiast transmisji audio.");
        }
        String path = response.url().getPath().toLowerCase(Locale.ROOT);
        String extension;
        if (type.equals("audio/mpeg") || type.equals("audio/mp3") || prefix.startsWith("ID3") || path.endsWith(".mp3")) {
            extension = "mp3";
            type = "audio/mpeg";
        } else if (type.equals("audio/aac") || type.equals("audio/aacp") || path.endsWith(".aac")) {
            extension = "aac";
            type = "audio/aac";
        } else if (type.equals("audio/ogg") || type.equals("application/ogg") || prefix.startsWith("OggS")) {
            extension = "ogg";
            type = "audio/ogg";
        } else if (type.equals("audio/flac") || type.equals("audio/x-flac") || prefix.startsWith("fLaC")) {
            extension = "flac";
            type = "audio/flac";
        } else {
            throw unsupported("Stacja nie udostępnia obsługiwanego strumienia audio.");
        }
        try (OutputStream output = destination.open(extension, type)) {
            byte[] buffer = new byte[8192];
            while (!stopped.getAsBoolean()) {
                int size = response.input.read(buffer);
                if (size == -1) return;
                output.write(buffer, 0, size);
            }
        }
    }

    private void recordHls(URL playlistSource, Playlist playlist, boolean television, Destination destination) throws IOException {
        if (playlist.segments.isEmpty()) throw unsupported("Playlista nie zawiera gotowych fragmentów transmisji.");
        long next = playlist.sequence + playlist.segments.size() - 1;
        URL initialization = null;
        OutputStream output = null;
        long lastSegment = System.nanoTime();
        try {
            while (!stopped.getAsBoolean()) {
                if (next < playlist.sequence) throw new IOException("Utracono fragmenty transmisji.");
                for (long index = next - playlist.sequence; index < playlist.segments.size(); index++) {
                    if (stopped.getAsBoolean()) return;
                    Segment segment = playlist.segments.get((int) index);
                    if (segment.unsupported != null) throw unsupported(segment.unsupported);
                    if (output != null && (segment.discontinuity || !sameUrl(initialization, segment.map))) {
                        throw new IOException("Nadawca zmienił format transmisji w trakcie nagrania.");
                    }
                    ByteArrayOutputStream data = fetchSegment(segment.url);
                    if (stopped.getAsBoolean()) return;
                    if (data.size() == 0) throw new IOException("Nadawca przesłał pusty fragment.");

                    if (output == null) {
                        initialization = segment.map;
                        // Packed AAC may contain timed ID3 tags. Make its output a separate audio file.
                        String path = segment.url.getPath().toLowerCase(Locale.ROOT);
                        boolean aac = !television && initialization == null && path.endsWith(".aac");
                        boolean mp3 = !television && initialization == null && path.endsWith(".mp3");
                        String extension = aac ? "aac" : mp3 ? "mp3" : initialization != null
                            ? (television ? "mp4" : "m4a") : "ts";
                        String type = aac ? "audio/aac" : mp3 ? "audio/mpeg" : initialization != null
                            ? (television ? "video/mp4" : "audio/mp4") : "video/mp2t";
                        ByteArrayOutputStream header = initialization != null ? fetchSegment(initialization) : null;
                        if (stopped.getAsBoolean()) return;
                        output = destination.open(extension, type);
                        if (header != null) header.writeTo(output);
                    }
                    data.writeTo(output);
                    next++;
                    lastSegment = System.nanoTime();
                }
                if (playlist.ended || stopped.getAsBoolean()) return;
                long stallLimit = Math.max(15L, playlist.targetSeconds * 3L) * 1_000_000_000L;
                if (System.nanoTime() - lastSegment > stallLimit) throw new IOException("Transmisja przestała się aktualizować.");
                pause(Math.min(3000, Math.max(750, playlist.targetSeconds * 500L)));
                if (stopped.getAsBoolean()) return;
                playlist = fetchPlaylist(playlistSource);
            }
        } finally {
            if (output != null) output.close();
        }
    }

    private static boolean sameUrl(URL first, URL second) {
        if (first == null || second == null) return first == second;
        return first.toExternalForm().equals(second.toExternalForm());
    }

    private Playlist fetchPlaylist(URL url) throws IOException {
        return retry(() -> {
            try (Response response = open(url)) {
                return parse(response.url(), readPlaylist(response.input));
            }
        });
    }

    private ByteArrayOutputStream fetchSegment(URL url) throws IOException {
        return retry(() -> {
            try (Response response = open(url)) {
                long expected = response.compressed ? -1 : response.connection.getContentLengthLong();
                ByteArrayOutputStream data = readBounded(response.input, MAX_SEGMENT_BYTES);
                if (!stopped.getAsBoolean() && expected >= 0 && data.size() != expected) {
                    throw new IOException("Niekompletny fragment transmisji.");
                }
                return data;
            }
        });
    }

    private String readPlaylist(InputStream input) throws IOException {
        return new String(readBounded(input, MAX_PLAYLIST_BYTES).toByteArray(), StandardCharsets.UTF_8);
    }

    private ByteArrayOutputStream readBounded(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!stopped.getAsBoolean()) {
            int read = input.read(buffer);
            if (read == -1) break;
            if (result.size() + read > limit) throw unsupported("Fragment transmisji jest za duży.");
            result.write(buffer, 0, read);
        }
        return result;
    }

    private interface Fetch<T> { T run() throws IOException; }

    private <T> T retry(Fetch<T> fetch) throws IOException {
        for (int attempt = 0; ; attempt++) {
            try {
                return fetch.run();
            } catch (IOException error) {
                if (attempt == 2 || error instanceof UnsupportedStream || stopped.getAsBoolean()) throw error;
                pause(350L * (attempt + 1));
                if (stopped.getAsBoolean()) throw error;
            }
        }
    }

    private static void pause(long milliseconds) throws IOException {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Nagranie zatrzymane.", error);
        }
    }

    private Response open(URL url) throws IOException {
        if (!"https".equals(url.getProtocol()) && !"http".equals(url.getProtocol())) {
            throw unsupported("Transmisja wymaga adresu HTTP lub HTTPS.");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setUseCaches(false);
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) PomocnikAlfa/1.6.9");
        connection.setRequestProperty("Icy-MetaData", "0");
        connection.setRequestProperty("Accept-Encoding", "identity");
        if (referer != null && !referer.isEmpty()) connection.setRequestProperty("Referer", referer);
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("Serwer transmisji zwrócił HTTP " + status + ".");
            return new Response(connection);
        } catch (IOException | RuntimeException error) {
            connection.disconnect();
            throw error;
        }
    }

    private static Playlist parse(URL url, String text) throws IOException {
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        String[] lines = text.split("\\r?\\n");
        if (!lines[0].trim().equals("#EXTM3U")) throw unsupported("Odpowiedź nadawcy nie jest playlistą HLS.");
        Playlist result = new Playlist();
        String variant = null;
        boolean duration = false;
        boolean discontinuity = false;
        boolean encrypted = false;
        boolean range = false;
        boolean mapRange = false;
        boolean gap = false;
        URL map = null;
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                variant = line.substring(line.indexOf(':') + 1);
            } else if (line.startsWith("#EXT-X-MEDIA:")) {
                if ("AUDIO".equals(attribute(line, "TYPE")) && !attribute(line, "URI").isEmpty()) {
                    result.externalAudio.add(attribute(line, "GROUP-ID"));
                }
            } else if (line.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                result.sequence = number(line.substring(line.indexOf(':') + 1), 0);
            } else if (line.startsWith("#EXT-X-TARGETDURATION:")) {
                result.targetSeconds = (int) number(line.substring(line.indexOf(':') + 1), 4);
            } else if (line.startsWith("#EXT-X-KEY:")) {
                encrypted = !"NONE".equals(attribute(line, "METHOD"));
            } else if (line.startsWith("#EXT-X-MAP:")) {
                String address = attribute(line, "URI");
                if (address.isEmpty()) throw unsupported("Brak nagłówka strumienia MP4.");
                map = new URL(url, address);
                mapRange = !attribute(line, "BYTERANGE").isEmpty();
            } else if (line.startsWith("#EXTINF:")) {
                duration = true;
            } else if (line.startsWith("#EXT-X-BYTERANGE:")) {
                range = true;
            } else if (line.equals("#EXT-X-DISCONTINUITY")) {
                discontinuity = true;
            } else if (line.equals("#EXT-X-GAP")) {
                gap = true;
            } else if (line.equals("#EXT-X-ENDLIST")) {
                result.ended = true;
            } else if (!line.isEmpty() && !line.startsWith("#")) {
                if (variant != null) {
                    int bitrate = (int) number(attribute(variant, "AVERAGE-BANDWIDTH"),
                        number(attribute(variant, "BANDWIDTH"), 0));
                    result.variants.add(new Variant(new URL(url, line), bitrate, attribute(variant, "AUDIO")));
                    variant = null;
                } else if (duration) {
                    String issue = null;
                    if (encrypted) issue = "Szyfrowana transmisja nie jest obsługiwana.";
                    else if (range || mapRange) issue = "Kanał korzysta z nieobsługiwanego formatu fragmentów.";
                    else if (gap) issue = "Nadawca zgłosił brak fragmentu transmisji.";
                    result.segments.add(new Segment(new URL(url, line), map, discontinuity, issue));
                    duration = false;
                    discontinuity = false;
                    range = false;
                    gap = false;
                }
            }
        }
        return result;
    }

    private static long number(String value, long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private static String attribute(String attributes, String name) {
        Matcher match = Pattern.compile("(?:^|[:,])" + name + "=(?:\"([^\"]*)\"|([^,]*))").matcher(attributes);
        if (!match.find()) return "";
        return match.group(1) != null ? match.group(1) : match.group(2).trim();
    }

    private static UnsupportedStream unsupported(String message) { return new UnsupportedStream(message); }

    static final class UnsupportedStream extends IOException {
        private static final long serialVersionUID = 1L;

        UnsupportedStream(String message) { super(message); }
    }

    private static final class Response implements Closeable {
        final HttpURLConnection connection;
        final BufferedInputStream input;
        final boolean compressed;

        Response(HttpURLConnection connection) throws IOException {
            this.connection = connection;
            compressed = "gzip".equalsIgnoreCase(connection.getContentEncoding());
            InputStream body = connection.getInputStream();
            input = new BufferedInputStream(compressed ? new GZIPInputStream(body) : body);
        }

        URL url() { return connection.getURL(); }

        @Override public void close() throws IOException {
            try {
                input.close();
            } finally {
                connection.disconnect();
            }
        }
    }

    private static final class Playlist {
        final List<Variant> variants = new ArrayList<>();
        final List<String> externalAudio = new ArrayList<>();
        final List<Segment> segments = new ArrayList<>();
        long sequence;
        int targetSeconds = 4;
        boolean ended;
    }

    private static final class Variant {
        final URL url;
        final int bitrate;
        final String audioGroup;

        Variant(URL url, int bitrate, String audioGroup) {
            this.url = url;
            this.bitrate = bitrate;
            this.audioGroup = audioGroup;
        }
    }

    private static final class Segment {
        final URL url;
        final URL map;
        final boolean discontinuity;
        final String unsupported;

        Segment(URL url, URL map, boolean discontinuity, String unsupported) {
            this.url = url;
            this.map = map;
            this.discontinuity = discontinuity;
            this.unsupported = unsupported;
        }
    }
}
