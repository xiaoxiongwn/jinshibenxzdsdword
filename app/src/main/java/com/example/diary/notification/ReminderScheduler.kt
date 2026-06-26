package com.example.diary.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.example.diary.data.DiaryEntry

object ReminderScheduler {
    fun schedule(context: Context, entry: DiaryEntry) {
        if (entry.reminderType == "none" || entry.reminderTime <= 0) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("entry_id", entry.id)
            putExtra("title", entry.title)
            putExtra("content", entry.content)
            putExtra("reminder_type", entry.reminderType)
            putExtra("reminder_interval", entry.reminderInterval)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, entry.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 使用 setAlarmClock：系统级闹钟，被杀概率最低，状态栏显示闹钟图标
        val showIntent = Intent(context, com.example.diary.MainActivity::class.java).let {
            PendingIntent.getActivity(
                context, entry.id.toInt(), it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val alarmClockInfo = AlarmManager.AlarmClockInfo(entry.reminderTime, showIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            }
        } else {
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        }
    }

    fun cancel(context: Context, entryId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, entryId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun canScheduleExact(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else true
    }

    fun openAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            context.startActivity(intent)
        }
    }
}
