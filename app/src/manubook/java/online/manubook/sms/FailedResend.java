package online.manubook.sms;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.work.Data;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Map;

import tech.bogomolov.incomingsmsgateway.ForwardingConfig;
import tech.bogomolov.incomingsmsgateway.RequestWorker;

/**
 * After a new setup code, resends the SMS that failed with an old key (android
 * review F11). A revoked key answers 401, which is final, so those SMS sit in
 * upstream's failed-message store. Each one is rewritten with the current address
 * and key, queued, and only then removed from the store, one at a time, so a crash
 * loses none. Resending is safe: Manubook answers duplicate for an SMS it already
 * has. It runs at setup and at every beat, which also catches SMS that were still
 * queued with the old key when the new code was saved. SMS that failed with the
 * current key stay where they are until someone presses *Gửi lại* on the status
 * screen, which resends every stored one.
 */
public final class FailedResend {

    private static final String TAG = "ManubookResend";
    // Upstream's store (FailedMessage.PREFERENCE).
    private static final String STORE = "failed_messages";

    private FailedResend() {
    }

    /**
     * @param all false: only SMS sent with an old address or key; true: every stored one
     * @return how many messages were queued again
     */
    public static synchronized int run(Context context, boolean all) {
        ForwardingConfig rule = null;
        for (ForwardingConfig r : ForwardingConfig.getAll(context)) {
            rule = r;
        }
        if (rule == null) {
            return 0;
        }
        SharedPreferences store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
        int queued = 0;
        for (Map.Entry<String, ?> entry : store.getAll().entrySet()) {
            if (!(entry.getValue() instanceof String)) {
                continue;
            }
            try {
                JSONObject json = rewrite((String) entry.getValue(), rule.getUrl(), rule.getHeaders(), all);
                if (json == null) {
                    continue;
                }
                RequestWorker.enqueue(context, toData(json));
                store.edit().remove(entry.getKey()).commit();
                queued++;
            } catch (JSONException e) {
                Log.w(TAG, "entry unreadable: " + e.getClass().getSimpleName());
            }
        }
        if (queued > 0) {
            Log.i(TAG, "resent " + queued);
        }
        return queued;
    }

    /**
     * The stored request with the current address and key, or null when it already
     * used them and {@code all} is false.
     */
    static JSONObject rewrite(String stored, String url, String headers, boolean all) throws JSONException {
        JSONObject json = new JSONObject(stored);
        if (!all && headers.equals(json.optString(RequestWorker.DATA_HEADERS, null))
                && url.equals(json.optString(RequestWorker.DATA_URL, null))) {
            return null;
        }
        json.put(RequestWorker.DATA_URL, url);
        json.put(RequestWorker.DATA_HEADERS, headers);
        return json;
    }

    // The store holds the worker's input Data as JSON of strings, booleans and ints.
    private static Data toData(JSONObject json) throws JSONException {
        Data.Builder data = new Data.Builder();
        for (Iterator<String> keys = json.keys(); keys.hasNext(); ) {
            String key = keys.next();
            Object value = json.get(key);
            if (value instanceof Boolean) {
                data.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                data.putInt(key, (Integer) value);
            } else if (value instanceof String) {
                data.putString(key, (String) value);
            }
        }
        return data.build();
    }
}
