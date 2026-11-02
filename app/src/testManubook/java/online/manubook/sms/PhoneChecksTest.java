package online.manubook.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.core.content.UnusedAppRestrictionsConstants;

import org.junit.Test;

/** The checklist's settings: the unused-app mapping, the battery row and the change signature. */
public class PhoneChecksTest {

    private static PhoneChecks allOk() {
        PhoneChecks c = new PhoneChecks();
        c.sms = true;
        c.notify = true;
        c.battery = true;
        c.bgRestricted = false;
        c.unusedExempt = true;
        return c;
    }

    @Test
    public void testUnusedAppStatus() {
        assertTrue(PhoneChecks.exempt(UnusedAppRestrictionsConstants.DISABLED));
        assertTrue(PhoneChecks.exempt(UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE));
        assertFalse(PhoneChecks.exempt(UnusedAppRestrictionsConstants.API_31));
        assertFalse(PhoneChecks.exempt(UnusedAppRestrictionsConstants.API_30_BACKPORT));
        assertNull(PhoneChecks.exempt(UnusedAppRestrictionsConstants.ERROR));
    }

    @Test
    public void testBatteryRow() {
        PhoneChecks c = allOk();
        assertTrue(c.allOk());
        c.bgRestricted = true;
        assertFalse(c.batteryOk());
        assertFalse(c.allOk());
        c = allOk();
        c.battery = false;
        assertFalse(c.allOk());
    }

    @Test
    public void testUnknownCountsAsRight() {
        PhoneChecks c = allOk();
        c.unusedExempt = null;
        assertTrue(c.allOk());
        c.sms = false;
        assertFalse(c.allOk());
    }

    @Test
    public void testSignature() {
        PhoneChecks c = allOk();
        assertEquals("11101", c.signature());
        c.unusedExempt = null;
        assertEquals("1110?", c.signature());
    }
}
