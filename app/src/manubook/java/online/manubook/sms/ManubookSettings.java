package online.manubook.sms;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The app's own settings, in app-private storage that is never backed up or
 * transferred (manifest, data extraction rules). The beat address holds the
 * heartbeat token; the key itself lives only in upstream's forwarding rule.
 */
public final class ManubookSettings {

    private static final String PREFERENCE = "manubook";
    private static final String KEY_BEAT_URL = "beat_url";
    private static final String KEY_KEY_TAIL = "key_tail";
    private static final String KEY_ACCOUNTS = "accounts";
    private static final String KEY_LAST_BEAT_AT = "last_beat_at";
    private static final String KEY_LAST_BEAT_CODE = "last_beat_code";
    private static final String KEY_LAST_ACKED_BEAT_AT = "last_acked_beat_at";

    private final SharedPreferences prefs;

    public ManubookSettings(Context context) {
        this.prefs = context.getSharedPreferences(PREFERENCE, Context.MODE_PRIVATE);
    }

    public boolean isSetUp() {
        return getBeatUrl() != null;
    }

    public void saveSetup(SetupCode code, List<String> accounts) {
        prefs.edit()
                .putString(KEY_BEAT_URL, code.beatUrl)
                .putString(KEY_KEY_TAIL, code.keyTail())
                .putString(KEY_ACCOUNTS, String.join(",", accounts))
                .apply();
    }

    public String getBeatUrl() {
        return prefs.getString(KEY_BEAT_URL, null);
    }

    public String getKeyTail() {
        return prefs.getString(KEY_KEY_TAIL, "");
    }

    public List<String> getAccounts() {
        String stored = prefs.getString(KEY_ACCOUNTS, "");
        return stored.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(stored.split(",")));
    }

    /** Records a beat attempt; code is the HTTP status, or -1 with no answer. */
    public void recordBeat(long at, int code) {
        SharedPreferences.Editor editor = prefs.edit()
                .putLong(KEY_LAST_BEAT_AT, at)
                .putInt(KEY_LAST_BEAT_CODE, code);
        if (code == 204) {
            editor.putLong(KEY_LAST_ACKED_BEAT_AT, at);
        }
        editor.apply();
    }

    public long getLastBeatAt() {
        return prefs.getLong(KEY_LAST_BEAT_AT, 0);
    }

    public int getLastBeatCode() {
        return prefs.getInt(KEY_LAST_BEAT_CODE, -1);
    }

    /** When a beat last answered 204; the status's failed count starts there. */
    public long getLastAckedBeatAt() {
        return prefs.getLong(KEY_LAST_ACKED_BEAT_AT, 0);
    }
}
