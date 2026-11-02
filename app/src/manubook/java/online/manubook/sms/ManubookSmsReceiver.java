package online.manubook.sms;

import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;

import tech.bogomolov.incomingsmsgateway.ForwardingConfig;
import tech.bogomolov.incomingsmsgateway.SmsBroadcastReceiver;

/**
 * Upstream's SMS receiver, plus the catch time of the latest SMS that matched the
 * rule, for the status's {@code lastSmsAt}. The flavor manifest declares this class
 * in place of upstream's, so upstream's receiver code stays untouched.
 */
public class ManubookSmsReceiver extends SmsBroadcastReceiver {

    private Context appContext;

    @Override
    public void onReceive(Context context, Intent intent) {
        // Only the system can send this action (BROADCAST_SMS); refuse anything else.
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }
        appContext = context.getApplicationContext();
        super.onReceive(context, intent);
    }

    // Called by upstream's onReceive, once per rule the SMS matched.
    @Override
    protected void callWebHook(ForwardingConfig config, String sender, String slotName,
                               String content, long timeStamp) {
        new ManubookSettings(appContext).recordSms(System.currentTimeMillis());
        super.callWebHook(config, sender, slotName, content, timeStamp);
    }
}
