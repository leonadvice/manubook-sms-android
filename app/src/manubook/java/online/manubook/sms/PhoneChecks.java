package online.manubook.sms;

import android.Manifest;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.content.PackageManagerCompat;
import androidx.core.content.UnusedAppRestrictionsConstants;

import java.util.concurrent.TimeUnit;

/**
 * The phone settings forwarding depends on (android review F12), read the same way
 * for the beat, the setup checklist and the settings notice. Each is null when the
 * phone could not tell.
 */
public final class PhoneChecks {

    private static final String TAG = "ManubookChecks";

    public Boolean sms;
    public Boolean notify;
    /** On Android's battery exemption list: *Pin: Không hạn chế* (S21+ probe 8.1). */
    public Boolean battery;
    /** Battery set to *Hạn chế*. */
    public Boolean bgRestricted;
    /** *Xóa quyền nếu không dùng ứng dụng* is off (or the phone has no such reset). */
    public Boolean unusedExempt;

    /** Reads every check; call off the main thread, it waits up to 1 second. */
    public static PhoneChecks read(Context context) {
        PhoneChecks c = new PhoneChecks();
        c.sms = context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
        c.notify = context.getSystemService(NotificationManager.class).areNotificationsEnabled();
        c.battery = context.getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(context.getPackageName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            c.bgRestricted = context.getSystemService(ActivityManager.class).isBackgroundRestricted();
        } else {
            c.bgRestricted = false;
        }
        c.unusedExempt = unusedExempt(context);
        return c;
    }

    private static Boolean unusedExempt(Context context) {
        try {
            int status = PackageManagerCompat.getUnusedAppRestrictionsStatus(context).get(1, TimeUnit.SECONDS);
            return exempt(status);
        } catch (Exception e) {
            Log.w(TAG, "unused-app status failed: " + e.getClass().getSimpleName());
            return null;
        }
    }

    /** Maps androidx's unused-app status to the status's {@code unusedExempt}. */
    static Boolean exempt(int status) {
        switch (status) {
            case UnusedAppRestrictionsConstants.DISABLED:
            case UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE:
                return true;
            case UnusedAppRestrictionsConstants.API_30_BACKPORT:
            case UnusedAppRestrictionsConstants.API_30:
            case UnusedAppRestrictionsConstants.API_31:
                return false;
            default:
                return null;
        }
    }

    /** The battery row is ticked only for *Không hạn chế*, never for *Hạn chế*. */
    public boolean batteryOk() {
        return Boolean.TRUE.equals(battery) && !Boolean.TRUE.equals(bgRestricted);
    }

    /** Whether every setting forwarding needs is right; an unknown one counts as right. */
    public boolean allOk() {
        return !Boolean.FALSE.equals(sms) && !Boolean.FALSE.equals(notify) && batteryOk()
                && !Boolean.FALSE.equals(unusedExempt);
    }

    /** A short fixed form of the checks, to see whether they changed since the last beat. */
    public String signature() {
        return new String(new char[]{flag(sms), flag(notify), flag(battery), flag(bgRestricted), flag(unusedExempt)});
    }

    private static char flag(Boolean value) {
        return value == null ? '?' : value ? '1' : '0';
    }
}
