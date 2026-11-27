package com.ispina.lokalnie;

import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.widget.Toast;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class PARecordingService extends Service {
    private static final String CHANNEL = "pa-live-recordings";
    private static final String STOP = "com.ispina.lokalnie.RECORD_STOP";
    private static final String FOLDER = "Pomocnik Alfa";
    private static final int RUNNING_ID = 27101;
    private static final int RESULT_ID = 27102;
    private static final int FLAGS = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;

    enum State { IDLE, CONNECTING, RECORDING, STOPPING, FINISHED }

    static final class Status {
        final State state;
        final String title;
        final PARecordingProgress progress;
        final String result;

        Status(State state, String title, PARecordingProgress progress, String result) {
            this.state = state;
            this.title = title;
            this.progress = progress;
            this.result = result;
        }

        boolean active() {
            return state == State.CONNECTING || state == State.RECORDING || state == State.STOPPING;
        }

        String heading() {
            switch (state) {
                case CONNECTING: return "Łączenie · " + title;
                case RECORDING: return "● Nagrywanie · " + title;
                case STOPPING: return "Zapisywanie · " + title;
                case FINISHED: return result;
                default: return "";
            }
        }

        String details() {
            if (state == State.FINISHED && progress.bytes == 0) return "";
            return progress.details();
        }
    }

    private static final Status IDLE = new Status(State.IDLE, "", null, "");
    static volatile Status status = IDLE;

    private RecordingFile activeFile;
    private volatile boolean stopped;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable progressTick = this::publishProgress;

    static void start(Context context, String title, String url, String referer, boolean television) {
        Intent command = new Intent(context, PARecordingService.class)
            .putExtra("title", title).putExtra("url", url).putExtra("referer", referer)
            .putExtra("television", television);
        context.startForegroundService(command);
    }

    static void stop(Context context) {
        context.startService(new Intent(context, PARecordingService.class).setAction(STOP));
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) {
            if (activeFile == null) stopSelf();
            else {
                stopped = true;
                publishProgress();
            }
            return START_NOT_STICKY;
        }
        if (activeFile != null) return START_NOT_STICKY;

        String title = intent.getStringExtra("title");
        String url = intent.getStringExtra("url");
        String referer = intent.getStringExtra("referer");
        boolean television = intent.getBooleanExtra("television", false);
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(
            CHANNEL, "Nagrania radia i telewizji", NotificationManager.IMPORTANCE_LOW));
        notifications.cancel(RESULT_ID);
        stopped = false;
        RecordingFile file = new RecordingFile(title);
        activeFile = file;
        status = new Status(State.CONNECTING, title, file.progress(), "");
        startForeground(RUNNING_ID, runningNotification(status), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        main.postDelayed(progressTick, 1000);
        new Thread(() -> record(file, url, referer, television), "PA-Nagrywanie").start();
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        stopped = true;
        main.removeCallbacks(progressTick);
        if (activeFile != null) status = IDLE;
        activeFile = null;
        super.onDestroy();
    }

    private void publishProgress() {
        main.removeCallbacks(progressTick);
        if (activeFile == null) return;
        PARecordingProgress progress = activeFile.progress();
        State state = stopped ? State.STOPPING : progress.bytes > 0 ? State.RECORDING : State.CONNECTING;
        status = new Status(state, activeFile.title, progress, "");
        getSystemService(NotificationManager.class).notify(RUNNING_ID, runningNotification(status));
        main.postDelayed(progressTick, 1000);
    }

    private void record(RecordingFile file, String url, String referer, boolean television) {
        PowerManager power = getSystemService(PowerManager.class);
        PowerManager.WakeLock wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PomocnikAlfa:Recording");
        String failure = null;
        try {
            wakeLock.acquire();
            new PAStreamRecorder(referer, () -> stopped).record(url, television, file);
        } catch (IOException | RuntimeException error) {
            if (!stopped || file.failed) {
                failure = error.getMessage() == null ? "Błąd połączenia lub zapisu." : error.getMessage();
            }
        } finally {
            if (wakeLock.isHeld()) wakeLock.release();
        }

        boolean saved = file.finish();
        String message;
        if (saved) {
            message = failure == null ? "Zapisano w Pobrane / " + FOLDER
                : "Nagranie przerwane. Zapisano nagraną część w Pobrane / " + FOLDER;
        } else if (file.failed) {
            message = "Nie udało się zapisać nagrania. Sprawdź wolne miejsce.";
        } else if (stopped) {
            message = "Anulowano nagrywanie.";
        } else {
            message = failure == null ? "Brak danych do zapisania." : failure;
        }
        PARecordingProgress progress = saved ? file.progress() : new PARecordingProgress(0, 0, null);
        Status result = new Status(State.FINISHED, file.title, progress, message);
        main.post(() -> {
            if (activeFile != file) return;
            main.removeCallbacks(progressTick);
            activeFile = null;
            status = result;
            stopForeground(STOP_FOREGROUND_REMOVE);
            showResult(result, saved);
            stopSelf();
        });
    }

    private Notification runningNotification(Status current) {
        PendingIntent stop = PendingIntent.getService(this, 0,
            new Intent(this, PARecordingService.class).setAction(STOP), FLAGS);
        return new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(current.heading()).setContentText(current.details())
            .setStyle(new Notification.BigTextStyle().bigText(current.details()))
            .setOnlyAlertOnce(true).setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "Zakończ nagranie", stop).build())
            .build();
    }

    private void showResult(Status result, boolean saved) {
        String details = result.details();
        String text = details.isEmpty() ? result.result : result.result + "\n" + details;
        Notification.Builder notification = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(saved ? android.R.drawable.stat_sys_download_done : android.R.drawable.stat_notify_error)
            .setContentTitle(result.title).setContentText(text).setAutoCancel(true)
            .setStyle(new Notification.BigTextStyle().bigText(text));
        Intent downloads = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
        if (saved && downloads.resolveActivity(getPackageManager()) != null) {
            notification.setContentIntent(PendingIntent.getActivity(this, 1, downloads, FLAGS));
        }
        getSystemService(NotificationManager.class).notify(RESULT_ID, notification.build());
        Toast.makeText(this, result.result, Toast.LENGTH_LONG).show();
    }

    private final class RecordingFile implements PAStreamRecorder.Destination {
        private final ContentResolver resolver = getContentResolver();
        private final String title;
        private Uri uri;
        private volatile long bytes;
        private volatile long firstWriteAt = -1;
        private volatile long lastWriteAt;
        private volatile long segmentMilliseconds;
        private volatile boolean segmented;
        private volatile String extension;
        private boolean failed;

        RecordingFile(String title) { this.title = title; }

        PARecordingProgress progress() {
            long length = bytes;
            long time = segmented ? segmentMilliseconds : length == 0 ? 0 : lastWriteAt - firstWriteAt;
            return new PARecordingProgress(time, length, extension);
        }

        @Override public void segmentWritten(long milliseconds) {
            segmentMilliseconds += milliseconds;
        }

        @Override public OutputStream open(String extension, String mime, boolean segmented) throws IOException {
            try {
                String time = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(new Date());
                String safeName = title.replaceAll("[^\\p{L}\\p{N} _.-]", "_").trim();
                if (safeName.length() > 70) safeName = safeName.substring(0, 70);
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, time + " " + safeName + "." + extension);
                values.put(MediaStore.Downloads.MIME_TYPE, mime);
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER);
                values.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri downloads = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                uri = resolver.insert(downloads, values);
                if (uri == null) throw new IOException("Nie udało się utworzyć pliku.");
                OutputStream stream = resolver.openOutputStream(uri);
                if (stream == null) throw new IOException("Nie udało się otworzyć pliku.");
                this.segmented = segmented;
                this.extension = extension;
                return new FilterOutputStream(stream) {
                    @Override public void write(int value) throws IOException {
                        byte[] single = {(byte) value};
                        write(single, 0, 1);
                    }

                    @Override public void write(byte[] buffer, int start, int count) throws IOException {
                        try {
                            out.write(buffer, start, count);
                            if (count > 0) {
                                lastWriteAt = SystemClock.elapsedRealtime();
                                if (firstWriteAt < 0) firstWriteAt = lastWriteAt;
                                bytes += count;
                            }
                        } catch (IOException | RuntimeException error) {
                            failed = true;
                            throw error;
                        }
                    }

                    @Override public void close() throws IOException {
                        try {
                            super.close();
                        } catch (IOException | RuntimeException error) {
                            failed = true;
                            throw error;
                        }
                    }
                };
            } catch (IOException | RuntimeException error) {
                failed = true;
                throw error;
            }
        }

        boolean finish() {
            if (uri == null) return false;
            if (bytes > 0 && !failed) {
                try {
                    ContentValues complete = new ContentValues();
                    complete.put(MediaStore.Downloads.IS_PENDING, 0);
                    if (resolver.update(uri, complete, null, null) == 1) return true;
                } catch (RuntimeException ignored) {
                    // A row still marked pending must not appear as a finished recording.
                }
                failed = true;
            }
            try {
                resolver.delete(uri, null, null);
            } catch (RuntimeException ignored) {
                // MediaStore expires abandoned pending rows.
            }
            return false;
        }
    }
}
