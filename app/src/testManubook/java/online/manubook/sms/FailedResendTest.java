package online.manubook.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import org.json.JSONObject;
import org.junit.Test;

/** Resending SMS that failed with an old key: the stored request is rewritten. Synthetic values only. */
public class FailedResendTest {

    private static final String URL = "https://api.manubook.online/api/sms/batch";
    private static final String NEW = "{\"Authorization\":\"Bearer NNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNN\"}";
    private static final String OLD = "{\"Authorization\":\"Bearer OOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOO\"}";

    private static String stored(String headers) throws Exception {
        return new JSONObject()
                .put("URL", URL)
                .put("TEXT", "{\"messages\":[]}")
                .put("HEADERS", headers)
                .put("STORE_FAILED", true)
                .put("MAX_RETRIES", 30)
                .toString();
    }

    @Test
    public void testOldKeyIsRewritten() throws Exception {
        JSONObject json = FailedResend.rewrite(stored(OLD), URL, NEW, false);
        assertEquals(NEW, json.getString("HEADERS"));
        assertEquals(URL, json.getString("URL"));
        assertEquals("{\"messages\":[]}", json.getString("TEXT"));
        assertEquals(30, json.getInt("MAX_RETRIES"));
    }

    @Test
    public void testCurrentKeyIsLeftAlone() throws Exception {
        assertNull(FailedResend.rewrite(stored(NEW), URL, NEW, false));
    }

    @Test
    public void testResendAllTakesCurrentOnesToo() throws Exception {
        JSONObject json = FailedResend.rewrite(stored(NEW), URL, NEW, true);
        assertEquals(NEW, json.getString("HEADERS"));
    }

    @Test
    public void testNewAddressIsRewritten() throws Exception {
        JSONObject json = FailedResend.rewrite(stored(NEW), "https://example.test/api/sms/batch", NEW, false);
        assertEquals("https://example.test/api/sms/batch", json.getString("URL"));
        assertFalse(json.isNull("HEADERS"));
    }
}
