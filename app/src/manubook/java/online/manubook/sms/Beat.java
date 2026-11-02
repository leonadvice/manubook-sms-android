package online.manubook.sms;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import tech.bogomolov.incomingsmsgateway.BuildConfig;
import tech.bogomolov.incomingsmsgateway.DeliveryResult;
import tech.bogomolov.incomingsmsgateway.RequestWorker;

/**
 * The app's own heartbeat (android review F1, F2; user decision D24): a status
 * post every 30 minutes from an inexact allow-while-idle alarm, re-armed at each
 * fire, so it keeps running while the phone sleeps. Upstream's heartbeat, whose
 * timer stops in deep sleep, stays off. A failed post is retried after 1, 5 and
 * 15 minutes through WorkManager; the next alarm replaces a pending retry. An
 * hourly watchdog re-arms a lost alarm.
 */
public final class Beat {

    static final long INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(30);
    // An inexact alarm may slip about 15 minutes in Doze; later than that, the
    // watchdog treats the alarm as lost.
    private static final long STALE_MILLIS = INTERVAL_MILLIS + TimeUnit.MINUTES.toMillis(15);
    static final long[] RETRY_DELAY_MINUTES = {1, 5, 15};
    private static final int TIMEOUT_MILLIS = 5000;

    static final String RETRY_WORK = "manubook-beat-retry";
    private static final String WATCHDOG_WORK = "manubook-beat-watchdog";
    private static final String TAG = "ManubookBeat";

    private Beat() {
    }

    /** Arms the next alarm one interval from now, replacing any earlier one. */
    public static void arm(Context context) {
        armIn(context, INTERVAL_MILLIS);
    }

    private static void armIn(Context context, long delayMillis) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delayMillis, alarmIntent(context, 0));
    }

    /**
     * Starts the beat after setup, a reboot or an app update: one beat as soon as
     * the network allows, the alarm, and the watchdog.
     */
    public static void start(Context context) {
        arm(context);
        enqueueRetry(context, 0, 0, ExistingWorkPolicy.REPLACE);
        // The first check waits an hour: a check now would race the beat just queued.
        PeriodicWorkRequest watchdog =
                new PeriodicWorkRequest.Builder(BeatWatchdogWorker.class, 1, TimeUnit.HOURS)
                        .setInitialDelay(1, TimeUnit.HOURS)
                        .build();
        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WATCHDOG_WORK, ExistingPeriodicWorkPolicy.KEEP, watchdog);
    }

    /**
     * Arms the alarm only when none is pending. A clock or time zone change does
     * not move an elapsed-time alarm, so a live one is left alone.
     */
    static void armIfMissing(Context context) {
        if (alarmIntent(context, PendingIntent.FLAG_NO_CREATE) == null) {
            arm(context);
        }
    }

    /** Called by the watchdog: re-arms the alarm when it is missing or overdue. */
    static void checkAlarm(Context context) {
        long sinceLast = System.currentTimeMillis() - new ManubookSettings(context).getLastBeatAt();
        if (sinceLast > STALE_MILLIS) {
            Log.i(TAG, "beat overdue, restarting");
            arm(context);
            enqueueRetry(context, 0, 0, ExistingWorkPolicy.REPLACE);
        } else if (alarmIntent(context, PendingIntent.FLAG_NO_CREATE) == null) {
            Log.i(TAG, "alarm missing, re-arming");
            armIn(context, Math.max(0, INTERVAL_MILLIS - sinceLast));
        }
    }

    // flags: 0 to create or update the alarm intent, FLAG_NO_CREATE to only look it up.
    private static PendingIntent alarmIntent(Context context, int flags) {
        Intent intent = new Intent(context, BeatAlarmReceiver.class);
        int mode = flags == 0 ? PendingIntent.FLAG_UPDATE_CURRENT : flags;
        return PendingIntent.getBroadcast(context, 0, intent, mode | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Queues a beat through WorkManager once the phone has a network.
     *
     * @param attempt how many tries came before this one (0 = first try)
     * @param policy  REPLACE drops a pending retry (a new beat supersedes it);
     *                APPEND_OR_REPLACE queues behind the running retry worker
     */
    static void enqueueRetry(Context context, int attempt, long delayMinutes, ExistingWorkPolicy policy) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BeatRetryWorker.class)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setInputData(new Data.Builder().putInt(BeatRetryWorker.ATTEMPT, attempt).build())
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(RETRY_WORK, policy, request);
    }

    /**
     * After a try that answered {@code code}, queues the next retry when one is due:
     * 1, 5 and 15 minutes after tries 0, 1 and 2.
     */
    static void afterTry(Context context, int attempt, int code, ExistingWorkPolicy policy) {
        if (!shouldRetry(code) || attempt >= RETRY_DELAY_MINUTES.length) {
            return;
        }
        enqueueRetry(context, attempt + 1, RETRY_DELAY_MINUTES[attempt], policy);
    }

    /** Whether a failed beat with this answer is worth retrying (no answer, 408, 429, 5xx). */
    static boolean shouldRetry(int code) {
        return code == -1 || code == 408 || code == 429 || code >= 500;
    }

    /**
     * Posts one status beat; call off the main thread. It waits up to 1 second for
     * the pending count, then up to 5 seconds each to connect and to read the
     * answer, plus the DNS lookup.
     *
     * @return the HTTP status, or -1 when no answer arrived or the app is not set up
     */
    static int send(Context context) {
        ManubookSettings settings = new ManubookSettings(context);
        String beatUrl = settings.getBeatUrl();
        if (beatUrl == null) {
            return -1;
        }
        int code = -1;
        HttpURLConnection connection = null;
        try {
            byte[] body = BeatStatus.toJson(snapshot(context, settings)).getBytes(StandardCharsets.UTF_8);
            connection = (HttpURLConnection) new URL(beatUrl).openConnection();
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }
            code = connection.getResponseCode();
        } catch (IOException | org.json.JSONException e) {
            // Class name only: messages can quote the address, which holds the token.
            Log.w(TAG, "beat failed: " + e.getClass().getSimpleName());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        settings.recordBeat(System.currentTimeMillis(), code);
        Log.i(TAG, "beat answered " + code);
        return code;
    }

    static BeatStatus.Snapshot snapshot(Context context, ManubookSettings settings) {
        BeatStatus.Snapshot s = new BeatStatus.Snapshot();
        s.version = BuildConfig.VERSION_NAME;
        s.build = BuildConfig.VERSION_CODE;
        s.os = Build.VERSION.RELEASE;
        s.osBuild = Build.DISPLAY;
        s.patch = Build.VERSION.SECURITY_PATCH;
        s.sms = context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
        s.notify = context.getSystemService(NotificationManager.class).areNotificationsEnabled();
        s.battery = context.getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(context.getPackageName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            s.bgRestricted = context.getSystemService(ActivityManager.class).isBackgroundRestricted();
            s.bucket = context.getSystemService(UsageStatsManager.class).getAppStandbyBucket();
        }
        s.pending = pendingDeliveries(context);
        s.failed = failedSince(context, settings.getLastAckedBeatAt());
        DeliveryResult delivery = DeliveryResult.load(context);
        s.lastError = delivery.getLastError();
        s.lastErrorAt = delivery.getLastErrorAt();
        return s;
    }

    // SMS caught and not yet accepted: upstream's delivery jobs still queued or
    // running, found by the worker-class tag WorkManager adds to every request.
    private static Integer pendingDeliveries(Context context) {
        try {
            List<WorkInfo> jobs = WorkManager.getInstance(context)
                    .getWorkInfosByTag(RequestWorker.class.getName()).get(1, TimeUnit.SECONDS);
            int pending = 0;
            for (WorkInfo job : jobs) {
                WorkInfo.State state = job.getState();
                if (state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.RUNNING
                        || state == WorkInfo.State.BLOCKED) {
                    pending++;
                }
            }
            return pending;
        } catch (Exception e) {
            Log.w(TAG, "pending count failed: " + e.getClass().getSimpleName());
            return null;
        }
    }

    // SMS given up since the last beat answered 204. Upstream's FailedMessage store
    // ("failed_messages") keys each entry "<currentTimeMillis>_<random>".
    static int failedSince(Context context, long sinceMillis) {
        SharedPreferences failed = context.getSharedPreferences("failed_messages", Context.MODE_PRIVATE);
        int count = 0;
        for (String key : failed.getAll().keySet()) {
            if (storedAt(key) > sinceMillis) {
                count++;
            }
        }
        return count;
    }

    static long storedAt(String failedMessageKey) {
        int end = failedMessageKey.indexOf('_');
        try {
            return Long.parseLong(end < 0 ? failedMessageKey : failedMessageKey.substring(0, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
