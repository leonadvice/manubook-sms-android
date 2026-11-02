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
    private static final String KEY_LAST_BEAT_CHECKS = "last_beat_checks";
    private static final String KEY_LAST_SMS_AT = "last_sms_at";
    private static final String KEY_SMS_OFF = "sms_off";
    private static final String KEY_SMS_SEEN_AT = "sms_seen_at";
    private static final String KEY_TEST_AT = "test_at";
    private static final String KEY_TEST_CODE = "test_code";
    private static final String KEY_TEST_KEY_TAIL = "test_key_tail";

    // The SMS-off periods are read, changed and written by the beat thread and the
    // screen; one lock keeps those steps whole.
    private static final Object SMS_OFF_LOCK = new Object();
    private static final long SEEN_WRITE_MILLIS = 60_000;

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

    /**
     * Records a beat attempt; code is the HTTP status, or -1 with no answer. checks
     * is {@link PhoneChecks#signature()} of the settings the beat reported.
     */
    public void recordBeat(long at, int code, String checks) {
        SharedPreferences.Editor editor = prefs.edit()
                .putLong(KEY_LAST_BEAT_AT, at)
                .putInt(KEY_LAST_BEAT_CODE, code)
                .putString(KEY_LAST_BEAT_CHECKS, checks);
        if (code == 204) {
            editor.putLong(KEY_LAST_ACKED_BEAT_AT, at);
        }
        editor.apply();
    }

    /** The settings the latest beat reported, so the screen can tell when they changed. */
    public String getLastBeatChecks() {
        return prefs.getString(KEY_LAST_BEAT_CHECKS, "");
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

    /** When the latest SMS matching the rule was caught; 0 before the first. */
    public long getLastSmsAt() {
        return prefs.getLong(KEY_LAST_SMS_AT, 0);
    }

    public void recordSms(long at) {
        prefs.edit().putLong(KEY_LAST_SMS_AT, at).apply();
    }

    /**
     * Records one look at the SMS permission and returns the periods to report. The
     * last-seen-granted time is written at most once a minute, so the screen, which
     * follows this store, is not woken by its own look; a period may then start up to
     * a minute early, which the contract allows ("at or before the real start").
     */
    public List<SmsOffPeriods.Period> observeSms(boolean granted, long now) {
        synchronized (SMS_OFF_LOCK) {
            SmsOffPeriods periods = loadSmsOff();
            String before = periods.format();
            long seenBefore = periods.getLastSeenGrantedAt();
            periods.observe(granted, now);
            if (!before.equals(periods.format())
                    || periods.getLastSeenGrantedAt() - seenBefore >= SEEN_WRITE_MILLIS) {
                saveSmsOff(periods);
            }
            return periods.list();
        }
    }

    /** After a beat answered 204: forgets the closed periods it carried. */
    public void acknowledgeSmsOff(List<SmsOffPeriods.Period> sent) {
        synchronized (SMS_OFF_LOCK) {
            SmsOffPeriods periods = loadSmsOff();
            periods.acknowledge(sent);
            saveSmsOff(periods);
        }
    }

    private SmsOffPeriods loadSmsOff() {
        return SmsOffPeriods.parse(prefs.getString(KEY_SMS_OFF, ""), prefs.getLong(KEY_SMS_SEEN_AT, 0));
    }

    private void saveSmsOff(SmsOffPeriods periods) {
        prefs.edit()
                .putString(KEY_SMS_OFF, periods.format())
                .putLong(KEY_SMS_SEEN_AT, periods.getLastSeenGrantedAt())
                .commit();
    }

    /** Records a Gửi thử answer for the key it used; code is the HTTP status or -1. */
    public void recordTest(long at, int code, String keyTail) {
        prefs.edit()
                .putLong(KEY_TEST_AT, at)
                .putInt(KEY_TEST_CODE, code)
                .putString(KEY_TEST_KEY_TAIL, keyTail)
                .apply();
    }

    public long getTestAt() {
        return prefs.getLong(KEY_TEST_AT, 0);
    }

    public int getTestCode() {
        return prefs.getInt(KEY_TEST_CODE, -1);
    }

    /** Whether a Gửi thử with the saved key reached Manubook. */
    public boolean testPassed() {
        return getTestCode() == 202 && getKeyTail().equals(prefs.getString(KEY_TEST_KEY_TAIL, null));
    }
}
