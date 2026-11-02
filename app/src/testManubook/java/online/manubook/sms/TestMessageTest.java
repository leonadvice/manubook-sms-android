package online.manubook.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.regex.Pattern;

/** Gửi thử: a test text Manubook reads as Tin thử, as a one-item batch. Synthetic values only. */
public class TestMessageTest {

    @Test
    public void testTextIsATestAndMatchesTheFilter() {
        String text = TestMessage.text(1760494360123L, "100000001");
        assertTrue(text, Pattern.matches("MANUBOOK TEST \\d{2}:\\d{2}:\\d{2} TK 100000001 So du", text));
        assertTrue(Pattern.compile(AccountFilter.regex(java.util.Collections.singletonList("100000001")))
                .matcher(text).find());
        // A second test a second later is a new text, never a duplicate.
        assertNotEquals(text, TestMessage.text(1760494361123L, "100000001"));
    }

    @Test
    public void testBodyIsAOneItemBatch() throws Exception {
        JSONObject json = new JSONObject(TestMessage.body("MANUBOOK TEST", 1760494360123L));
        assertEquals(1, json.length());
        assertEquals(1, json.getJSONArray("messages").length());
        JSONObject item = json.getJSONArray("messages").getJSONObject(0);
        assertEquals(2, item.length());
        assertEquals("MANUBOOK TEST", item.getString("text"));
        assertEquals(BeatStatus.instant(1760494360123L), item.getString("receivedAt"));
    }
}
