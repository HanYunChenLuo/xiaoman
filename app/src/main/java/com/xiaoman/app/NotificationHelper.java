package com.xiaoman.app;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** 到期提醒：AlarmManager 定时 + 本地通知。 */
final class NotificationHelper {
    static final String CHANNEL = "expire_reminder";

    static void schedule(Context c, JSONObject a) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            int id = a.optString("id").hashCode();
            Intent it = new Intent(c, ReminderReceiver.class)
                    .putExtra("name", a.optString("name"))
                    .putExtra("date", a.optString("expireAt"));
            PendingIntent pi = PendingIntent.getBroadcast(c, id, it,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            String expire = a.optString("expireAt");
            if (expire.isEmpty() || am == null) { am.cancel(pi); return; }
            Calendar cal = Calendar.getInstance();
            String[] p = expire.split("-");
            cal.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]),
                    a.optInt("remindHour", 9), a.optInt("remindMinute", 0), 0);
            cal.set(Calendar.MILLISECOND, 0);
            long unitMs = 86400000L;
            if ("week".equals(a.optString("remindUnit", "day"))) unitMs = 7 * 86400000L;
            else if ("month".equals(a.optString("remindUnit", "day"))) unitMs = 30 * 86400000L;
            long trigger = cal.getTimeInMillis() - Math.max(0, a.optInt("remindDays", 0)) * unitMs;
            if (trigger < System.currentTimeMillis()) trigger = System.currentTimeMillis() + 5000;
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        } catch (Exception ignored) {}
    }

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "到期提醒", NotificationManager.IMPORTANCE_DEFAULT));
        }
    }
}

class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        String name = intent.getStringExtra("name");
        String date = intent.getStringExtra("date");
        NotificationHelper.ensureChannel(c);
        android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new android.app.Notification.Builder(c, NotificationHelper.CHANNEL)
                : new android.app.Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("小满 · 到期提醒")
                .setContentText((name == null ? "资产" : name) + " 将于 " + date + " 到期，请留意")
                .setAutoCancel(true);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify((name == null ? "asset" : name).hashCode(), b.build());
    }
}
