package online.manubook.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Restarts the beat after a reboot or an app update, and on a clock or time zone
 * change. Alarms do not survive a reboot. After an update the foreground service
 * is started again here too; after a reboot upstream's BootCompletedReceiver does.
 */
public class BeatRearmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Context app = context.getApplicationContext();
        if (!new ManubookSettings(app).isSetUp() || intent.getAction() == null) {
            return;
        }
        switch (intent.getAction()) {
            case Intent.ACTION_MY_PACKAGE_REPLACED:
                ManubookSetup.startService(app);
                Beat.start(app);
                break;
            case Intent.ACTION_BOOT_COMPLETED:
                Beat.start(app);
                break;
            default:
                Beat.armIfMissing(app);
        }
    }
}
