package online.manubook.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** The beat status body against the server's rules (manubook/CONTRACT.md §1). */
public class BeatStatusTest {

    private static final Set<String> FIELDS = new HashSet<>(Arrays.asList(
            "app", "version", "build", "os", "osBuild", "patch", "sms", "notify", "battery",
            "unusedExempt", "bgRestricted", "bucket", "pending", "failed", "lastError",
            "lastErrorAt", "lastSmsAt", "smsOff"));
    private static final Pattern INSTANT = Pattern.compile(
            "20\\d{2}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d+)?)?(?:Z|[+-](?:0\\d|1[0-5]):[0-5]\\d)");

    private static BeatStatus.Snapshot full() {
        BeatStatus.Snapshot s = new BeatStatus.Snapshot();
        s.version = "0.1.0";
        s.build = 1;
        s.os = "16";
        s.osBuild = "BP2A.250605.031.A3.G996BXXSEHZJ1";
        s.patch = "2026-10-01";
        s.sms = true;
        s.notify = true;
        s.battery = false;
        s.bgRestricted = false;
        s.bucket = 10;
        s.pending = 0;
        s.failed = 2;
        s.lastError = "HTTP_401";
        s.lastErrorAt = 1760494360123L;
        s.unusedExempt = true;
        s.lastSmsAt = 1760494300000L;
        s.smsOff = SmsOffPeriods.parse("1760490000000:1760491000000,1760492000000:0", 0).list();
        return s;
    }

    // Every field at its longest: still within the server's 1,024 bytes.
    private static BeatStatus.Snapshot longest() {
        BeatStatus.Snapshot s = full();
        s.version = repeat('9', 32);
        s.build = Integer.MAX_VALUE;
        s.os = repeat('9', 32);
        s.osBuild = repeat('9', 64);
        s.pending = 100000;
        s.failed = 100000;
        s.lastError = repeat('E', 40);
        s.smsOff = SmsOffPeriods.parse("1:2,3:4,5:6,7:8,9:10,11:12", 0).list();
        return s;
    }

    private static String repeat(char c, int n) {
        return new String(new char[n]).replace('\0', c);
    }

    @Test
    public void testFullStatus() throws Exception {
        JSONObject json = new JSONObject(BeatStatus.toJson(full()));
        assertEquals("android", json.getString("app"));
        assertEquals("0.1.0", json.getString("version"));
        assertEquals(1, json.getInt("build"));
        assertEquals("16", json.getString("os"));
        assertEquals("2026-10-01", json.getString("patch"));
        assertFalse(json.getBoolean("battery"));
        assertEquals(10, json.getInt("bucket"));
        assertEquals(2, json.getInt("failed"));
        assertEquals("HTTP_401", json.getString("lastError"));
        assertTrue(INSTANT.matcher(json.getString("lastErrorAt")).matches());
        for (java.util.Iterator<String> keys = json.keys(); keys.hasNext(); ) {
            String key = keys.next();
            assertTrue(key, FIELDS.contains(key));
        }
        assertTrue(json.getBoolean("unusedExempt"));
        assertTrue(INSTANT.matcher(json.getString("lastSmsAt")).matches());
        assertEquals(2, json.getJSONArray("smsOff").length());
        JSONObject closed = json.getJSONArray("smsOff").getJSONObject(0);
        assertTrue(INSTANT.matcher(closed.getString("from")).matches());
        assertTrue(INSTANT.matcher(closed.getString("to")).matches());
        assertTrue(json.getJSONArray("smsOff").getJSONObject(1).isNull("to"));
        assertTrue(BeatStatus.toJson(full()).getBytes("UTF-8").length <= 1024);
    }

    @Test
    public void testLongestStatusFits() throws Exception {
        String body = BeatStatus.toJson(longest());
        assertTrue(body.length() + " bytes", body.getBytes("UTF-8").length <= 1024);
        assertEquals(SmsOffPeriods.MAX, new JSONObject(body).getJSONArray("smsOff").length());
    }

    @Test
    public void testNothingKnownLeavesOptionalFieldsOut() throws Exception {
        BeatStatus.Snapshot s = full();
        s.lastSmsAt = 0;
        s.smsOff = new java.util.ArrayList<>();
        JSONObject json = new JSONObject(BeatStatus.toJson(s));
        assertFalse(json.has("lastSmsAt"));
        assertEquals(0, json.getJSONArray("smsOff").length());
    }

    @Test
    public void testBadOptionalFieldsAreLeftOut() throws Exception {
        BeatStatus.Snapshot s = full();
        s.osBuild = "build#withéodd chars";
        s.patch = "October";
        s.bucket = 0;
        s.lastError = "bad code!";
        s.pending = null;
        s.sms = null;
        JSONObject json = new JSONObject(BeatStatus.toJson(s));
        assertFalse(json.has("osBuild"));
        assertFalse(json.has("patch"));
        assertFalse(json.has("bucket"));
        assertFalse(json.has("lastError"));
        assertFalse(json.has("lastErrorAt"));
        assertFalse(json.has("pending"));
        assertFalse(json.has("sms"));
    }

    @Test
    public void testRequiredFieldsAreCleanedNotDropped() throws Exception {
        BeatStatus.Snapshot s = full();
        s.os = "16 (Beta/1)";
        s.version = null;
        s.build = 0;
        JSONObject json = new JSONObject(BeatStatus.toJson(s));
        assertTrue(BeatStatus.OS.matcher(json.getString("os")).matches());
        assertTrue(BeatStatus.VERSION.matcher(json.getString("version")).matches());
        assertEquals(1, json.getInt("build"));
    }

    @Test
    public void testInstantUsesAsciiDigits() {
        java.util.Locale saved = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG"));
            assertTrue(INSTANT.matcher(BeatStatus.instant(1760494360123L)).matches());
        } finally {
            java.util.Locale.setDefault(saved);
        }
    }
}
