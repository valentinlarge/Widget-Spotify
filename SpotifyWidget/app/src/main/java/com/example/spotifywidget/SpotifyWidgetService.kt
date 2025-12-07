package com.example.spotifywidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.Image
import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.Track
import kotlinx.coroutines.*

class SpotifyWidgetService : Service() {

    private val CLIENT_ID = "cadc50c2d16740c28e62efad222762b0"
    private val REDIRECT_URI = "com.example.spotifywidget://callback"
    private var spotifyAppRemote: SpotifyAppRemote? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    private var currentTrack: Track? = null
    private var currentIsPaused: Boolean = true
    private var lastPlaybackPosition: Long = 0
    private var lastUpdateTime: Long = 0
    
    private var currentTrackImageUri: String? = null
    private var currentBitmap: Bitmap? = null

    companion object {
        private const val CHANNEL_ID = "SpotifyWidgetChannel"
        private const val TAG = "SpotifyWidgetService"
        
        const val ACTION_PLAY_PAUSE = "com.example.spotifywidget.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.spotifywidget.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.example.spotifywidget.ACTION_PREVIOUS"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != null) {
            handleAction(intent.action!!)
        }
        connectToSpotify()
        return START_STICKY
    }

    private fun handleAction(action: String) {
        val playerApi = spotifyAppRemote?.playerApi ?: return
        when (action) {
            ACTION_PLAY_PAUSE -> {
                playerApi.playerState.setResultCallback { state ->
                    if (state.isPaused) playerApi.resume() else playerApi.pause()
                }
            }
            ACTION_NEXT -> playerApi.skipNext()
            ACTION_PREVIOUS -> playerApi.skipPrevious()
        }
    }

    private fun connectToSpotify() {
        if (spotifyAppRemote?.isConnected == true) return

        val connectionParams = ConnectionParams.Builder(CLIENT_ID)
            .setRedirectUri(REDIRECT_URI)
            .showAuthView(false)
            .build()

        SpotifyAppRemote.connect(this, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                subscribeToPlayerState()
            }

            override fun onFailure(throwable: Throwable) {
                Log.e(TAG, "Erreur connexion: ${throwable.message}")
            }
        })
    }

    private fun subscribeToPlayerState() {
        spotifyAppRemote?.playerApi?.subscribeToPlayerState()?.setEventCallback { playerState ->
            currentTrack = playerState.track
            currentIsPaused = playerState.isPaused
            lastPlaybackPosition = playerState.playbackPosition
            lastUpdateTime = System.currentTimeMillis()

            updateWidgetFull()

            if (!currentIsPaused) {
                startProgressUpdater()
            } else {
                stopProgressUpdater()
            }
        }
    }

    private fun startProgressUpdater() {
        if (progressJob?.isActive == true) return

        progressJob = serviceScope.launch {
            while (isActive) {
                val timePassed = System.currentTimeMillis() - lastUpdateTime
                lastPlaybackPosition += timePassed
                lastUpdateTime = System.currentTimeMillis()
                
                updateWidgetFull()
                delay(1000)
            }
        }
    }

    private fun stopProgressUpdater() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun safeResizeBitmap(original: Bitmap): Bitmap {
        val maxDimension = 300
        val width = original.width
        val height = original.height

        if (width <= maxDimension && height <= maxDimension) {
            return original
        }

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int

        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt()
        }

        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true)
    }

    private fun updateWidgetFull() {
        try {
            val track = currentTrack ?: return
            val views = RemoteViews(packageName, R.layout.widget_spotify)

            views.setTextViewText(R.id.widget_song_title, track.name)
            views.setTextViewText(R.id.widget_artist_name, track.artist.name)
            views.setImageViewResource(R.id.btn_play_pause, if (currentIsPaused) R.drawable.ic_play else R.drawable.ic_pause)

            if (track.duration > 0) {
                val safePos = if (lastPlaybackPosition > track.duration) track.duration else lastPlaybackPosition
                val progress = ((safePos.toFloat() / track.duration.toFloat()) * 100).toInt()
                views.setProgressBar(R.id.widget_progress_bar, 100, progress, false)
            }

            views.setOnClickPendingIntent(R.id.btn_prev, getPendingIntent(ACTION_PREVIOUS))
            views.setOnClickPendingIntent(R.id.btn_next, getPendingIntent(ACTION_NEXT))
            views.setOnClickPendingIntent(R.id.btn_play_pause, getPendingIntent(ACTION_PLAY_PAUSE))

            if (currentTrackImageUri != track.imageUri.raw) {
                currentTrackImageUri = track.imageUri.raw
                // Mode LARGE + RESIZE SAFE
                spotifyAppRemote?.imagesApi?.getImage(track.imageUri, Image.Dimension.LARGE)
                    ?.setResultCallback { bitmap ->
                        val safeBitmap = safeResizeBitmap(bitmap)
                        currentBitmap = safeBitmap
                        
                        views.setImageViewBitmap(R.id.widget_album_art, safeBitmap)
                        pushWidgetUpdate(views)
                    }
            } else if (currentBitmap != null) {
                views.setImageViewBitmap(R.id.widget_album_art, currentBitmap)
                pushWidgetUpdate(views)
            } else {
                pushWidgetUpdate(views)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur mise à jour widget: ${e.message}")
        }
    }

    private fun getPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, SpotifyWidgetService::class.java).apply { this.action = action }
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun pushWidgetUpdate(views: RemoteViews) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val widgetComponent = ComponentName(this, SpotifyWidgetProvider::class.java)
            appWidgetManager.updateAppWidget(widgetComponent, views)
        } catch (e: Exception) {
            Log.e(TAG, "Transaction trop grosse: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressUpdater()
        serviceScope.cancel()
        SpotifyAppRemote.disconnect(spotifyAppRemote)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Spotify Widget Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Spotify Widget")
            .setContentText("Contrôle actif")
            .setSmallIcon(R.drawable.ic_music_note)
            .build()
    }
}