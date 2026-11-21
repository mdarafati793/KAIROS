package com.kairos.app.data

import android.app.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kairos.app.MainActivity
import com.kairos.app.R
import kotlinx.coroutines.*

object Reminders {
    const val CH = "kairos_reminders"

    private fun pi(c: Context, id: Long, title: String, flags: Int) = PendingIntent.getBroadcast(
        c, id.toInt(),
        Intent(c, ReminderReceiver::class.java).putExtra("t", title).putExtra("id", id.toInt()),
        flags or PendingIntent.FLAG_IMMUTABLE
    )

    fun schedule(c: Context, id: Long, title: String, at: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        val p = pi(c, id, title, PendingIntent.FLAG_UPDATE_CURRENT)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
    }

    fun cancel(c: Context, id: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        PendingIntent.getBroadcast(
            c, id.toInt(), Intent(c, ReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )?.let { am.cancel(it) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(Reminders.CH, "Reminders", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(c, Reminders.CH)
            .setSmallIcon(R.drawable.ic_notif).setContentTitle("KAIROS")
            .setContentText(i.getStringExtra("t") ?: "Reminder")
            .setAutoCancel(true).setContentIntent(open).build()
        nm.notify(i.getIntExtra("id", 0), n)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED) return
        val pr = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDb.get(c).dao().upcoming(System.currentTimeMillis()).forEach { Reminders.schedule(c, it.id, it.title, it.time) }
            } finally { pr.finish() }
        }
    }
}
