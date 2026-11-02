package online.manubook.sms;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Hourly: re-arms the beat alarm when it was lost (android review F1). */
public class BeatWatchdogWorker extends Worker {

    public BeatWatchdogWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (new ManubookSettings(getApplicationContext()).isSetUp()) {
            Beat.checkAlarm(getApplicationContext());
        }
        return Result.success();
    }
}
