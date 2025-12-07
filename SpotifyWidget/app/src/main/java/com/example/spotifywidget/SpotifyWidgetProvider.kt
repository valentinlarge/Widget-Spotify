package com.example.spotifywidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build

class SpotifyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Au démarrage ou à la mise à jour du widget, on s'assure que le service est lancé
        startSpotifyService(context)
    }

    override fun onEnabled(context: Context) {
        // Premier widget ajouté
        startSpotifyService(context)
    }

    override fun onDisabled(context: Context) {
        // Dernier widget supprimé, on pourrait arrêter le service ici si on voulait
        val intent = Intent(context, SpotifyWidgetService::class.java)
        context.stopService(intent)
    }

    private fun startSpotifyService(context: Context) {
        val intent = Intent(context, SpotifyWidgetService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
