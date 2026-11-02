package tech.bogomolov.incomingsmsgateway;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The outcome of the latest delivery attempt and of the latest failed one, kept in
 * their own small SharedPreferences file so a status screen (or a heartbeat body)
 * can show "last delivery" without parsing logcat. Holds no URL, header or text.
 */
public class DeliveryResult {

    private static final String PREFERENCE = "delivery_result";
    private static final String KEY_LAST_AT = "last_at";
    private static final String KEY_LAST_CODE = "last_code";
    private static final String KEY_LAST_ERROR = "last_error";
    private static final String KEY_LAST_ERROR_AT = "last_error_at";

    private final long lastAt;
    private final int lastCode;
    private final String lastError;
    private final long lastErrorAt;

    private DeliveryResult(long lastAt, int lastCode, String lastError, long lastErrorAt) {
        this.lastAt = lastAt;
        this.lastCode = lastCode;
        this.lastError = lastError;
        this.lastErrorAt = lastErrorAt;
    }

    // responseCode is the HTTP status, or -1 when no answer arrived; errorClass is
    // Request#getErrorClass() (null after a success).
    public static void record(Context context, int responseCode, String errorClass) {
        long now = System.currentTimeMillis();
        SharedPreferences.Editor editor = getPreference(context).edit()
                .putLong(KEY_LAST_AT, now)
                .putInt(KEY_LAST_CODE, responseCode);
        if (errorClass != null) {
            editor.putString(KEY_LAST_ERROR, errorClass).putLong(KEY_LAST_ERROR_AT, now);
        }
        editor.apply();
    }

    public static DeliveryResult load(Context context) {
        SharedPreferences prefs = getPreference(context);
        return new DeliveryResult(
                prefs.getLong(KEY_LAST_AT, 0),
                prefs.getInt(KEY_LAST_CODE, -1),
                prefs.getString(KEY_LAST_ERROR, null),
                prefs.getLong(KEY_LAST_ERROR_AT, 0));
    }

    // 0 when nothing was delivered yet.
    public long getLastAt() {
        return lastAt;
    }

    // HTTP status of the latest attempt, or -1 when it got no answer.
    public int getLastCode() {
        return lastCode;
    }

    // Failure class of the latest failed attempt ("HTTP_401", "NETWORK", ...), or
    // null when no attempt has failed yet.
    public String getLastError() {
        return lastError;
    }

    public long getLastErrorAt() {
        return lastErrorAt;
    }

    private static SharedPreferences getPreference(Context context) {
        return context.getSharedPreferences(PREFERENCE, Context.MODE_PRIVATE);
    }
}
