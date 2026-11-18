package com.xiaoman.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONObject
import java.util.Calendar

/** 到期提醒：AlarmManager 定时 + 本地通知。 */
object NotificationHelper {
    const val CHANNEL = "expire_reminder"

    @JvmStatic
    fun schedule(c: Context, a: JSONObject) {
        try {
            val am = c.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val id = a.optString("id").hashCode()
            val it = Intent(c, ReminderReceiver::class.java)
                .putExtra("name", a.optString("name"))
                .putExtra("date", a.optString("expireAt"))
            val pi = PendingIntent.getBroadcast(
                c, id, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val expire = a.optString("expireAt")
            if (expire.isEmpty() || am == null) {
                am?.cancel(pi)
                return
            }
            val cal = Calendar.getInstance()
            val p = expire.split("-")
            cal.set(
                p[0].toInt(), p[1].toInt() - 1, p[2].toInt(),
                a.optInt("remindHour", 9), a.optInt("remindMinute", 0), 0
            )
            cal.set(Calendar.MILLISECOND, 0)
            var unitMs = 86400000L
            if ("week" == a.optString("remindUnit", "day")) unitMs = 7 * 86400000L
            else if ("month" == a.optString("remindUnit", "day")) unitMs = 30 * 86400000L
            var trigger = cal.timeInMillis - Math.max(0, a.optInt("remindDays", 0)) * unitMs
            if (trigger < System.currentTimeMillis()) trigger = System.currentTimeMillis() + 5000
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } catch (_: Exception) {
        }
    }

    @JvmStatic
    fun ensureChannel(c: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "到期提醒", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val name = intent.getStringExtra("name")
        val date = intent.getStringExtra("date")
        NotificationHelper.ensureChannel(c)
        val b = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(c, NotificationHelper.CHANNEL)
        } else {
            Notification.Builder(c)
        }
        b.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("小满 · 到期提醒")
            .setContentText((name ?: "资产") + " 将于 " + date + " 到期，请留意")
            .setAutoCancel(true)
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify((name ?: "asset").hashCode(), b.build())
    }
}
