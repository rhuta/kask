package com.rhuta.kask.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rhuta.kask.R
import com.rhuta.kask.domain.engine.AIEngine
import com.rhuta.kask.domain.engine.StreamResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class InferenceService : Service() {

    @Inject
    lateinit var aiEngine: AIEngine

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val text = intent?.getStringExtra("text") ?: ""

        when (action) {
            "ACTION_REWRITE" -> startInference { aiEngine.rewrite(text) }
            "ACTION_SUMMARIZE" -> startInference { aiEngine.summarize(text) }
            "ACTION_STOP" -> {
                aiEngine.stop()
                stopForeground(true)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startInference(block: () -> kotlinx.coroutines.flow.Flow<StreamResult>) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI Inference Running")
            .setContentText("Processing your request...")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        serviceScope.launch {
            block().collect { result ->
                when (result) {
                    is StreamResult.Complete -> {
                        stopForeground(true)
                        stopSelf()
                    }
                    is StreamResult.Error -> {
                        // Handle error (e.g., notify user)
                        stopForeground(true)
                        stopSelf()
                    }
                    else -> {}
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        aiEngine.release()
    }

    private fun createNotificationChannel() {
        val name = "AI Service Channel"
        val descriptionText = "Channel for AI background inference"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "ai_service_channel"
        const val NOTIFICATION_ID = 1
    }
}
