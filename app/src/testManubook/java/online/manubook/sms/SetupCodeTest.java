package online.manubook.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** The combined setup code (contract §3). Synthetic keys and tokens only. */
public class SetupCodeTest {

    private static final String HOST = "api.manubook.online";
    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAa1_-";
    private static final String TOKEN = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";

    private static String code(String host) {
        return "https://" + host + "/api/sms\nBearer " + KEY + "\nhttps://" + host + "/api/sms/beat/" + TOKEN;
    }

    @Test
    public void testAcceptsTheThreeLines() {
        SetupCode code = SetupCode.parse(code(HOST), HOST, false);
        assertNotNull(code);
        assertEquals("https://api.manubook.online/api/sms", code.smsUrl);
        assertEquals("https://api.manubook.online/api/sms/batch", code.batchUrl());
        assertEquals("Bearer " + KEY, code.authorization);
        assertEquals("https://api.manubook.online/api/sms/beat/" + TOKEN, code.beatUrl);
        assertEquals("1_-", code.keyTail().substring(1));
    }

    @Test
    public void testReleaseRefusesAnotherHost() {
        assertNull(SetupCode.parse(code("admin.manubook.online"), HOST, false));
        assertNull(SetupCode.parse(code("staging.example.test"), HOST, false));
    }

    @Test
    public void testDebugAcceptsAnotherHttpsHost() {
        assertNotNull(SetupCode.parse(code("staging.example.test"), HOST, true));
    }

    @Test
    public void testRefusesMixedHosts() {
        String mixed = "https://" + HOST + "/api/sms\nBearer " + KEY + "\nhttps://other.example/api/sms/beat/" + TOKEN;
        assertNull(SetupCode.parse(mixed, HOST, true));
    }

    @Test
    public void testRefusesAnyOtherShape() {
        assertNull(SetupCode.parse(null, HOST, false));
        assertNull(SetupCode.parse(code(HOST) + "\n", HOST, false));
        assertNull(SetupCode.parse(code(HOST).replace("\n", "\r\n"), HOST, false));
        assertNull(SetupCode.parse(code(HOST).replace("https://", "http://"), HOST, false));
        assertNull(SetupCode.parse(code(HOST).replace(KEY, KEY.substring(1)), HOST, false));
        assertNull(SetupCode.parse(code(HOST).replace(TOKEN, TOKEN + "B"), HOST, false));
        assertNull(SetupCode.parse(code(HOST).replace("Bearer ", "bearer "), HOST, false));
        assertNull(SetupCode.parse("https://" + HOST + "/api/sms\nBearer " + KEY, HOST, false));
    }
}
