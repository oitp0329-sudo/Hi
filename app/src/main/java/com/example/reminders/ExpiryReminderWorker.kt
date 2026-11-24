package com.example.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.core.data.local.FileDudeDatabase
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

class ExpiryReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = FileDudeDatabase.getInstance(applicationContext)
        val docs = db.walletDocumentDao().getAllOnce()
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60L * 60L * 1000L

        val dueDocs = docs.filter { doc ->
            if (doc.isRenewed) return@filter false
            val daysLeft = ceil((doc.expiryDateMillis - now).toDouble() / dayMs.toDouble()).toInt()
            val offsets = doc.reminderOffsetsCsv.split(",").mapNotNull { it.trim().toIntOrNull() }
            daysLeft <= 0 || offsets.any { offset -> daysLeft in (offset - 1)..offset }
        }

        if (dueDocs.isNotEmpty()) {
            postSummaryNotification(dueDocs.size, dueDocs.first().title)
        }
        return Result.success()
    }

    private fun postSummaryNotification(count: Int, firstTitle: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_tab", "reminders")
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            101,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (count == 1) "Document expiring soon: $firstTitle" else "$count documents need renewal attention"
        val text = if (count == 1) {
            "Tap to review expiry details or mark $firstTitle as renewed."
        } else {
            "Including $firstTitle and ${count - 1} other document(s)."
        }

        val notification = NotificationCompat.Builder(applicationContext, ReminderScheduler.CHANNEL_EXPIRY)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(2048, notification)
    }
}

object ReminderScheduler {
    const val CHANNEL_EXPIRY = "filedude_expiry_reminders"
    const val CHANNEL_PROCESSING = "filedude_processing"
    private const val WORK_NAME = "filedude_daily_expiry_check"

    fun ensureNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val expiryChannel = NotificationChannel(
                CHANNEL_EXPIRY,
                context.getString(R.string.notification_channel_expiry_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_expiry_desc)
            }
            val processingChannel = NotificationChannel(
                CHANNEL_PROCESSING,
                context.getString(R.string.notification_channel_processing_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_processing_desc)
            }
            manager.createNotificationChannel(expiryChannel)
            manager.createNotificationChannel(processingChannel)
        }
    }

    fun scheduleDailyCheck(context: Context) {
        val request = PeriodicWorkRequestBuilder<ExpiryReminderWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun triggerImmediateCheck(context: Context) {
        val request = OneTimeWorkRequestBuilder<ExpiryReminderWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
