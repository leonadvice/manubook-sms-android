package tech.bogomolov.incomingsmsgateway;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pure-JVM tests for {@link Request#isRetryableStatus}: a 4xx answer is final
 * (resending a wrong body or a revoked key cannot succeed), except 408 and 429,
 * which are temporary; 5xx retries.
 */
public class RequestRetryableStatusTest {

    @Test
    public void testClientErrorsAreFinal() {
        assertFalse(Request.isRetryableStatus(400));
        assertFalse(Request.isRetryableStatus(401));
        assertFalse(Request.isRetryableStatus(403));
        assertFalse(Request.isRetryableStatus(404));
        assertFalse(Request.isRetryableStatus(413));
        assertFalse(Request.isRetryableStatus(499));
    }

    @Test
    public void testTimeoutAndRateLimitRetry() {
        assertTrue(Request.isRetryableStatus(408));
        assertTrue(Request.isRetryableStatus(429));
    }

    @Test
    public void testServerErrorsRetry() {
        assertTrue(Request.isRetryableStatus(500));
        assertTrue(Request.isRetryableStatus(503));
    }
}
