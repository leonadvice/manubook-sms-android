package online.manubook.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;
import java.util.regex.Pattern;

import tech.bogomolov.incomingsmsgateway.ForwardingConfig;

/**
 * The forwarding rule setup writes: one-item batches on the batch route (contract
 * §2), the key header, no SSL bypass, failed messages kept. Synthetic values only.
 */
public class ManubookSetupTest {

    private static final String CODE = "https://api.manubook.online/api/sms\n"
            + "Bearer AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\n"
            + "https://api.manubook.online/api/sms/beat/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";

    private static ForwardingConfig rule() {
        SetupCode code = SetupCode.parse(CODE, "api.manubook.online", false);
        return ManubookSetup.rule(null, code, Collections.singletonList("100000001"));
    }

    @Test
    public void testRule() {
        ForwardingConfig rule = rule();
        assertEquals("*", rule.getSender());
        assertEquals("https://api.manubook.online/api/sms/batch", rule.getUrl());
        assertEquals("{\"Authorization\":\"Bearer AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"}", rule.getHeaders());
        assertEquals(false, rule.getIgnoreSsl());
        assertEquals(true, rule.getStoreFailed());
        assertEquals(30, rule.getRetriesNumber());
    }

    @Test
    public void testBodyIsAOneItemBatch() throws Exception {
        String sms = "ACB: TK 100000001 +5,000,000 VND. So du 12,345,678 VND. ND: \"QUOTE\" \\ path";
        String body = rule().prepareMessage("ACB", sms, "sim1", 1760494360123L);
        JSONObject json = new JSONObject(body);
        assertEquals(1, json.length());
        JSONArray messages = json.getJSONArray("messages");
        assertEquals(1, messages.length());
        JSONObject item = messages.getJSONObject(0);
        assertEquals(2, item.length());
        assertEquals(sms, item.getString("text"));
        assertTrue(item.getString("receivedAt"), Pattern.matches(
                "20\\d{2}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}(Z|[+-]\\d{2}:\\d{2})",
                item.getString("receivedAt")));
    }
}
