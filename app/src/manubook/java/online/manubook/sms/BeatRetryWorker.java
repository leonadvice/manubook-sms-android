package online.manubook.sms;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingWorkPolicy;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** One beat try through WorkManager: the first beat after a start, and the retries. */
public class BeatRetryWorker extends Worker {

    static final String ATTEMPT = "attempt";

    public BeatRetryWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        int attempt = getInputData().getInt(ATTEMPT, 0);
        int code = Beat.send(getApplicationContext());
        // Queued behind this worker, so it runs once this one has finished.
        Beat.afterTry(getApplicationContext(), attempt, code, ExistingWorkPolicy.APPEND_OR_REPLACE);
        return Result.success();
    }
}
