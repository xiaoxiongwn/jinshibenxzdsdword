package com.example.diary.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.diary.MainActivity
import com.example.diary.data.DiaryDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON") {
            // 开机后重新注册所有提醒：必须用 goAsync()，否则 onReceive 一返回
            // 系统就可能杀掉进程，协程里的 DB 查询根本来不及完成
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = DiaryDatabase.getDatabase(context)
                    val entries = db.diaryDao().getEntriesWithReminders()
                    val now = System.currentTimeMillis()

                    entries.forEach { entry ->
                        var t = entry.reminderTime
                        // 过期了且是重复提醒：顺延到下一个未来时间点
                        if (t <= now &&
                            entry.reminderType != "none" &&
                            entry.reminderType != "once") {
                            t = calculateNextReminder(
                                entry.reminderTime,
                                entry.reminderType,
                                entry.reminderInterval
                            )
                            if (t > now) {
                                db.diaryDao().update(entry.copy(reminderTime = t))
                            }
                        }
                        if (t > now) {
                            ReminderScheduler.schedule(context, entry.copy(reminderTime = t))
                        }
                    }
                    // 顺便把前台保活服务拉起来
                    try {
                        DiaryForegroundService.start(context)
                    } catch (_: Throwable) {
                        // 某些厂商在 BOOT 早期不允许启前台服务，忽略即可
                    }
                } catch (_: Throwable) {
                    // 不能让异常吃掉 pending.finish()
                } finally {
                    pending.finish()
                }
            }
            return
        }

        val entryId = intent.getLongExtra("entry_id", -1)
        val title = intent.getStringExtra("title") ?: "日记提醒"
        val content = intent.getStringExtra("content") ?: ""
        val reminderType = intent.getStringExtra("reminder_type") ?: "none"
        val reminderInterval = intent.getIntExtra("reminder_interval", 0)

        showNotification(context, entryId, title, content)

        if (reminderType != "none" && reminderType != "once") {
            scheduleNext(context, entryId, reminderType, reminderInterval)
        }
    }

    private fun showNotification(context: Context, entryId: Long, title: String, content: String) {
        val channelId = "diary_reminders"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "日记提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "日记条目提醒"
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("entry_id", entryId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, entryId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content.take(50))
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .build()

        notificationManager.notify(entryId.toInt(), notification)
    }

    private fun scheduleNext(context: Context, entryId: Long, reminderType: String, reminderInterval: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = DiaryDatabase.getDatabase(context)
            val entry = db.diaryDao().getById(entryId) ?: return@launch

            val nextTime = calculateNextReminder(entry.reminderTime, reminderType, reminderInterval)
            if (nextTime > 0) {
                db.diaryDao().update(entry.copy(reminderTime = nextTime))
                ReminderScheduler.schedule(context, entry.copy(reminderTime = nextTime))
            }
        }
    }

    companion object {
        fun calculateNextReminder(currentTime: Long, type: String, interval: Int): Long {
            val cal = Calendar.getInstance().apply { timeInMillis = currentTime }
            val hourStep = if (interval > 0) interval else 1
            val dayStep = if (interval > 0) interval else 1
            when (type) {
                "hourly" -> cal.add(Calendar.HOUR_OF_DAY, hourStep)
                "daily" -> cal.add(Calendar.DAY_OF_YEAR, 1)
                "weekly" -> cal.add(Calendar.DAY_OF_YEAR, 7)
                "monthly" -> cal.add(Calendar.MONTH, 1)
                "yearly" -> cal.add(Calendar.YEAR, 1)
                "custom_days" -> cal.add(Calendar.DAY_OF_YEAR, dayStep)
                else -> return 0
            }
            while (cal.timeInMillis <= System.currentTimeMillis()) {
                when (type) {
                    "hourly" -> cal.add(Calendar.HOUR_OF_DAY, hourStep)
                    "daily" -> cal.add(Calendar.DAY_OF_YEAR, 1)
                    "weekly" -> cal.add(Calendar.DAY_OF_YEAR, 7)
                    "monthly" -> cal.add(Calendar.MONTH, 1)
                    "yearly" -> cal.add(Calendar.YEAR, 1)
                    "custom_days" -> cal.add(Calendar.DAY_OF_YEAR, dayStep)
                    else -> return 0
                }
            }
            return cal.timeInMillis
        }
    }

    private fun calculateNextReminder(currentTime: Long, type: String, interval: Int): Long =
        Companion.calculateNextReminder(currentTime, type, interval)
}
