package com.example.kahoot.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.kahoot.R

object NotificationHelper {
    private const val CHANNEL_ID = "quiz_notifications"
    private const val CHANNEL_NAME = "Quiz Notifications"

    fun showQuizStartNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Créer le canal de notification (requis pour Android 8.0 et versions ultérieures)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Construire la notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Le Quiz commence!")
            .setContentText("Le quiz va commencer. Préparez-vous!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        // Afficher la notification
        notificationManager.notify(1, notification)
    }
}
