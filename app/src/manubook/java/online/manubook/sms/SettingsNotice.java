package online.manubook.sms;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.service.notification.StatusBarNotification;

import tech.bogomolov.incomingsmsgateway.R;

/**
 * The phone's own notice when a setting stops forwarding (user decision D28): shown
 * while a checklist row is not ticked, removed once every row is. Manubook's admin
 * alert stays the main signal; with notifications off this notice cannot show.
 * It carries the app name only, never an amount or a name.
 */
public final class SettingsNotice {

    private static final String CHANNEL_ID = "manubook_settings";
    private static final int NOTICE_ID = 2;

    private SettingsNotice() {
    }

    public static void update(Context context, PhoneChecks checks) {
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        if (checks.allOk() || !new ManubookSettings(context).isSetUp()) {
            notifications.cancel(NOTICE_ID);
            return;
        }
        for (StatusBarNotification shown : notifications.getActiveNotifications()) {
            if (shown.getId() == NOTICE_ID) {
                // Already showing: Android drops updates posted too often, so post once.
                return;
            }
        }
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.manubook_notice_channel), NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(context, SetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent tap = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notice = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_f)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.manubook_notice))
                .setContentIntent(tap)
                .setOnlyAlertOnce(true)
                .build();
        notifications.notify(NOTICE_ID, notice);
    }
}
