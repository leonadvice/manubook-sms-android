package online.manubook.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** The SMS-off periods of the status (contract §1 smsOff). */
public class SmsOffPeriodsTest {

    @Test
    public void testNoPeriodBeforeTheFirstGrant() {
        SmsOffPeriods p = SmsOffPeriods.parse("", 0);
        p.observe(false, 1000);
        assertTrue(p.list().isEmpty());
    }

    @Test
    public void testOpenThenClose() {
        SmsOffPeriods p = SmsOffPeriods.parse("", 0);
        p.observe(true, 1000);
        p.observe(true, 2000);
        p.observe(false, 3000);
        p.observe(false, 4000);
        List<SmsOffPeriods.Period> open = p.list();
        assertEquals(1, open.size());
        assertEquals(2000, open.get(0).from);
        assertEquals(0, open.get(0).to);
        p.observe(true, 5000);
        assertEquals(5000, p.list().get(0).to);
        assertEquals(5000, p.getLastSeenGrantedAt());
    }

    @Test
    public void testRoundTripAndAcknowledge() {
        SmsOffPeriods p = SmsOffPeriods.parse("100:200,300:0", 250);
        assertEquals("100:200,300:0", p.format());
        // A beat carried both; only the closed one is forgotten.
        p.acknowledge(p.list());
        assertEquals("300:0", p.format());
        p.observe(true, 400);
        assertEquals("300:400", p.format());
        p.acknowledge(SmsOffPeriods.parse("300:0", 0).list());
        assertEquals("300:400", p.format());
    }

    @Test
    public void testKeepsTheNewestFive() {
        SmsOffPeriods p = SmsOffPeriods.parse("", 1);
        for (int i = 1; i <= 7; i++) {
            p.observe(false, i * 10);
            p.observe(true, i * 10 + 5);
        }
        List<SmsOffPeriods.Period> list = p.list();
        assertEquals(SmsOffPeriods.MAX, list.size());
        assertEquals(25, list.get(0).from);
        assertEquals(75, list.get(4).to);
    }

    @Test
    public void testDamagedEntryIsDropped() {
        assertEquals("1:2", SmsOffPeriods.parse("x:y,1:2,3", 0).format());
    }
}
