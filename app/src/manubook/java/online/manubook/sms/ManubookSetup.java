package online.manubook.sms;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.List;

import tech.bogomolov.incomingsmsgateway.ForwardingConfig;
import tech.bogomolov.incomingsmsgateway.HeartbeatSettings;
import tech.bogomolov.incomingsmsgateway.SmsReceiverService;

/**
 * Writes the one upstream forwarding rule Manubook SMS uses and starts the engine
 * (android review F7). Every SMS goes as a one-item batch through upstream's body
 * template (contract §2), so the server gets each SMS's receive time.
 */
public final class ManubookSetup {

    static final String RULE_KEY = "manubook";
    static final String BATCH_TEMPLATE =
            "{\"messages\":[{\"text\":\"%text%\",\"receivedAt\":\"%receivedStamp=yyyy-MM-dd'T'HH:mm:ss.SSSXXX%\"}]}";
    // WorkManager's backoff starts at 10 s, doubles and stops growing at 5 h, so 30
    // tries cover about 4 days of server or network outage.
    static final int RETRIES = 30;

    private ManubookSetup() {
    }

    public static void apply(Context context, SetupCode code, List<String> accounts) {
        for (ForwardingConfig old : ForwardingConfig.getAll(context)) {
            old.remove();
        }
        ForwardingConfig rule = rule(context, code, accounts);
        rule.save();

        // Upstream's heartbeat stays off: its timer stops while the phone sleeps.
        new HeartbeatSettings(false, "", HeartbeatSettings.DEFAULT_INTERVAL_MINUTES).save(context);
        new ManubookSettings(context).saveSetup(code, accounts);

        startService(context);
        Beat.start(context);
    }

    static ForwardingConfig rule(Context context, SetupCode code, List<String> accounts) {
        ForwardingConfig rule = new ForwardingConfig(context);
        rule.setKey(RULE_KEY);
        rule.setSender("*");
        rule.setIsSenderRegex(false);
        rule.setSmsFilter(AccountFilter.regex(accounts));
        rule.setUrl(code.batchUrl());
        rule.setSimSlot(0);
        rule.setTemplate(BATCH_TEMPLATE);
        rule.setHeaders("{\"Authorization\":\"" + code.authorization + "\"}");
        rule.setRetriesNumber(RETRIES);
        rule.setIgnoreSsl(false);
        rule.setChunkedMode(false);
        rule.setIsSmsEnabled(true);
        rule.setSignHmacSha256(false);
        rule.setStoreFailed(true);
        rule.setLocalMode(false);
        return rule;
    }

    /** Starts upstream's foreground service, as upstream's MainActivity does. */
    static void startService(Context context) {
        try {
            context.startForegroundService(new Intent(context, SmsReceiverService.class));
        } catch (IllegalStateException e) {
            // Android 12+ refuses a start from the background outside its exemptions;
            // SMS still arrive through the manifest receiver.
            Log.w("ManubookSetup", "service start refused: " + e.getClass().getSimpleName());
        }
    }
}
