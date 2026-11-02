package online.manubook.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import tech.bogomolov.incomingsmsgateway.ManubookFilterBridge;

/**
 * The one filter for all banks (android review F7, F11): built from 1, 2 and 5
 * accounts, it always compiles and matches only balance-change SMS for those
 * accounts. Synthetic account numbers and SMS only (public repository).
 */
public class AccountFilterTest {

    private static final String ACB_LIKE =
            "ACB: TK 100000001 +5,000,000 VND luc 09:12 15/10/2026. So du 12,345,678 VND. ND: CHUYEN TIEN";
    private static final String AGRIBANK_LIKE =
            "Agribank: 15/10/2026 09:12 TK 2000000000001: +1,000,000VND. SD: 3,000,000VND. ND: THANH TOAN";

    @Test
    public void testOneAccount() {
        String regex = AccountFilter.regex(Collections.singletonList("100000001"));
        assertEquals("(?s)TK (100000001)\\b.*(So du|SD:)", regex);
        assertTrue(ManubookFilterBridge.matches(regex, ACB_LIKE));
        assertFalse(ManubookFilterBridge.matches(regex, AGRIBANK_LIKE));
    }

    @Test
    public void testTwoAccountsAcrossBanks() {
        String regex = AccountFilter.regex(Arrays.asList("100000001", "2000000000001"));
        assertTrue(ManubookFilterBridge.matches(regex, ACB_LIKE));
        assertTrue(ManubookFilterBridge.matches(regex, AGRIBANK_LIKE));
    }

    @Test
    public void testFiveAccountsCompile() {
        List<String> accounts = Arrays.asList("100000001", "100000002", "100000003", "2000000000001", "2000000000002");
        String regex = AccountFilter.regex(accounts);
        Pattern.compile(regex);
        assertTrue(ManubookFilterBridge.matches(regex, AGRIBANK_LIKE));
    }

    @Test
    public void testNeedsTheBalanceChangeShape() {
        String regex = AccountFilter.regex(Collections.singletonList("100000001"));
        assertFalse(ManubookFilterBridge.matches(regex, "Ma OTP cua TK 100000001 la 123456"));
        assertFalse(ManubookFilterBridge.matches(regex, "TK 1000000012 +5,000 VND. So du 9 VND"));
        assertTrue(ManubookFilterBridge.matches(regex, "MANUBOOK TEST 09:12:40 TK 100000001 So du"));
    }

    @Test
    public void testNormalizeKeepsDigitsOnly() {
        assertEquals(Arrays.asList("100000001", "2000000000001"),
                AccountFilter.normalize(Arrays.asList(" 100000001 ", "", "2000000000001", "100000001")));
        assertNull(AccountFilter.normalize(Collections.singletonList("1000-0001")));
        assertNull(AccountFilter.normalize(Collections.singletonList("12345")));
        assertNull(AccountFilter.normalize(Collections.singletonList("123456789012345678901")));
    }
}
