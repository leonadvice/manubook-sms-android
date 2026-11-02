package online.manubook.sms;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The status body of a heartbeat (contract §1, Manubook bank-sms-import decision
 * 32). The server refuses the whole status when one field is bad, so every optional
 * field is checked here against the server's pattern and left out when it fails.
 * Required fields are cleaned instead, so a status is always readable.
 * <p>
 * W6a sends the fields below; W6b adds unusedExempt, lastSmsAt and smsOff.
 */
public final class BeatStatus {

    // The server's patterns (apps/api/src/bank-sms/beat-status.ts); see manubook/CONTRACT.md.
    static final Pattern VERSION = Pattern.compile("[0-9A-Za-z.+-]{1,32}");
    static final Pattern OS = Pattern.compile("[0-9A-Za-z .+_-]{1,32}");
    static final Pattern OS_BUILD = Pattern.compile("[0-9A-Za-z .+_(),/-]{1,64}");
    static final Pattern ERROR_CODE = Pattern.compile("[0-9A-Za-z_.:-]{1,40}");
    static final Pattern PATCH = Pattern.compile("20\\d{2}-\\d{2}-\\d{2}");

    /** What the phone knows at beat time; null means unknown and is left out. */
    public static final class Snapshot {
        public String version;
        public int build;
        public String os;
        public String osBuild;
        public String patch;
        public Boolean sms;
        public Boolean notify;
        public Boolean battery;
        public Boolean bgRestricted;
        public Integer bucket;
        public Integer pending;
        public Integer failed;
        public String lastError;
        public long lastErrorAt;
    }

    private BeatStatus() {
    }

    public static String toJson(Snapshot s) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("app", "android");
        json.put("version", clean(s.version, VERSION, 32, "0"));
        json.put("build", Math.max(1, s.build));
        json.put("os", clean(s.os, OS, 32, "unknown"));
        putIfMatches(json, "osBuild", s.osBuild, OS_BUILD);
        putIfMatches(json, "patch", s.patch, PATCH);
        putIfKnown(json, "sms", s.sms);
        putIfKnown(json, "notify", s.notify);
        putIfKnown(json, "battery", s.battery);
        putIfKnown(json, "bgRestricted", s.bgRestricted);
        if (s.bucket != null && s.bucket >= 5 && s.bucket <= 50) {
            json.put("bucket", s.bucket);
        }
        putCount(json, "pending", s.pending);
        putCount(json, "failed", s.failed);
        if (s.lastError != null && s.lastErrorAt > 0 && ERROR_CODE.matcher(s.lastError).matches()) {
            json.put("lastError", s.lastError);
            json.put("lastErrorAt", instant(s.lastErrorAt));
        }
        return json.toString();
    }

    /** An ISO 8601 instant with the device's offset, ASCII digits whatever the locale. */
    public static String instant(long epochMillis) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(new Date(epochMillis));
    }

    // Replaces characters the server refuses and cuts to its length limit.
    static String clean(String value, Pattern pattern, int maxLength, String fallback) {
        String v = value == null ? "" : value.trim();
        if (pattern.matcher(v).matches()) {
            return v;
        }
        v = v.replaceAll("[^0-9A-Za-z.+_-]", "_");
        if (v.length() > maxLength) {
            v = v.substring(0, maxLength);
        }
        return pattern.matcher(v).matches() ? v : fallback;
    }

    private static void putIfMatches(JSONObject json, String name, String value, Pattern pattern)
            throws JSONException {
        if (value != null && pattern.matcher(value).matches()) {
            json.put(name, value);
        }
    }

    private static void putIfKnown(JSONObject json, String name, Boolean value) throws JSONException {
        if (value != null) {
            json.put(name, value.booleanValue());
        }
    }

    private static void putCount(JSONObject json, String name, Integer value) throws JSONException {
        if (value != null && value >= 0) {
            json.put(name, Math.min(value, 100000));
        }
    }
}
