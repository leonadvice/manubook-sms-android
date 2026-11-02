package online.manubook.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.work.ExistingWorkPolicy;
import androidx.work.WorkManager;

/**
 * Fires every 30 minutes from the allow-while-idle alarm. The system keeps the app
 * awake for about 10 seconds; a post that does not finish in time is retried
 * through WorkManager.
 */
public class BeatAlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Context app = context.getApplicationContext();
        if (!new ManubookSettings(app).isSetUp()) {
            return;
        }
        Beat.arm(app);
        WorkManager.getInstance(app).cancelUniqueWork(Beat.RETRY_WORK);
        PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                int code = Beat.send(app);
                Beat.afterTry(app, 0, code, ExistingWorkPolicy.REPLACE);
            } finally {
                pending.finish();
            }
        }, "ManubookBeat").start();
    }
}
