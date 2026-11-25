package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.example.PhotonApplication
import com.example.player.PhotonPlaybackBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Multi-size Home Screen Widget provider for Photon Audio.
 * Handles Compact (2x1), Standard (4x1/4x2), and Studio (4x3+) responsive layouts
 * plus Play/Pause, Skip Next/Prev, Favorite Heart, and Shuffle actions.
 */
class PhotonAudioWidgetProvider : AppWidgetProvider() {

    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        PhotonWidgetUpdater.requestUpdate(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        PhotonWidgetUpdater.requestUpdate(context, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val app = context.applicationContext as? PhotonApplication ?: return
        val controller = app.playerController

        when (intent.action) {
            PhotonWidgetUpdater.ACTION_PLAY_PAUSE -> {
                val current = PhotonPlaybackBridge.state.value
                if (current.queue.isEmpty()) {
                    widgetScope.launch {
                        val tracks = runCatching { app.libraryRepository.allTracks.first() }.getOrDefault(emptyList())
                        if (tracks.isNotEmpty()) {
                            controller.playQueue(tracks, 0)
                        }
                        PhotonWidgetUpdater.requestUpdate(context)
                    }
                } else {
                    controller.togglePlayPause()
                    PhotonWidgetUpdater.requestUpdate(context)
                }
            }
            PhotonWidgetUpdater.ACTION_NEXT -> {
                controller.skipNext()
                PhotonWidgetUpdater.requestUpdate(context)
            }
            PhotonWidgetUpdater.ACTION_PREV -> {
                controller.skipPrevious()
                PhotonWidgetUpdater.requestUpdate(context)
            }
            PhotonWidgetUpdater.ACTION_TOGGLE_SHUFFLE -> {
                controller.toggleShuffle()
                PhotonWidgetUpdater.requestUpdate(context)
            }
            PhotonWidgetUpdater.ACTION_TOGGLE_FAVORITE -> {
                val track = PhotonPlaybackBridge.state.value.currentTrack ?: return
                val nextFav = !track.isFavorite
                widgetScope.launch {
                    app.libraryRepository.setTrackFavorite(track.id, nextFav)
                    controller.updateTrackInQueue(track.copy(isFavorite = nextFav))
                    PhotonWidgetUpdater.requestUpdate(context)
                }
            }
        }
    }
}
