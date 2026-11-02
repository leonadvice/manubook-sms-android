package online.manubook.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure parts of the beat: retry rule and the failed-message key time. */
public class BeatTest {

    @Test
    public void testRetryRule() {
        assertTrue(Beat.shouldRetry(-1));
        assertTrue(Beat.shouldRetry(408));
        assertTrue(Beat.shouldRetry(429));
        assertTrue(Beat.shouldRetry(503));
        assertFalse(Beat.shouldRetry(401));
        assertFalse(Beat.shouldRetry(413));
    }

    @Test
    public void testThirtyMinutes() {
        assertEquals(30 * 60 * 1000L, Beat.INTERVAL_MILLIS);
    }

    @Test
    public void testStoredAt() {
        assertEquals(1760494360123L, Beat.storedAt("1760494360123_123456"));
        assertEquals(0L, Beat.storedAt("oddkey"));
    }
}
