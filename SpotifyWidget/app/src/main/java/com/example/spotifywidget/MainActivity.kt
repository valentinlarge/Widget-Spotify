package com.example.spotifywidget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote

class MainActivity : Activity() {

    private val CLIENT_ID = "cadc50c2d16740c28e62efad222762b0"
    private val REDIRECT_URI = "com.example.spotifywidget://callback"
    private val REQUEST_CODE = 1337
    private var spotifyAppRemote: SpotifyAppRemote? = null

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(50, 50, 50, 50)
        }

        statusText = TextView(this).apply {
            text = "Appuie sur Connecter pour autoriser Spotify"
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 50)
        }
        
        val connectButton = Button(this).apply {
            text = "AUTORISER L'ACCÈS SPOTIFY"
            setOnClickListener { startAuthFlow() }
        }

        layout.addView(statusText)
        layout.addView(connectButton)
        setContentView(layout)
    }

    private fun startAuthFlow() {
        statusText.text = "Ouverture de Spotify..."
        
        // On demande l'autorisation explicitement
        val builder = AuthorizationRequest.Builder(
            CLIENT_ID,
            AuthorizationResponse.Type.TOKEN,
            REDIRECT_URI
        )

        builder.setScopes(arrayOf("app-remote-control"))
        val request = builder.build()

        AuthorizationClient.openLoginActivity(this, REQUEST_CODE, request)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)

        // Résultat de l'authentification
        if (requestCode == REQUEST_CODE) {
            val response = AuthorizationClient.getResponse(resultCode, intent)

            when (response.type) {
                AuthorizationResponse.Type.TOKEN -> {
                    statusText.text = "Autorisation RÉUSSIE !\nConnexion au service distant..."
                    connectAppRemote() // Maintenant qu'on est autorisé, on connecte le Remote
                }
                AuthorizationResponse.Type.ERROR -> {
                    statusText.text = "ERREUR D'AUTH : ${response.error}"
                    Log.e("MainActivity", "Auth error: ${response.error}")
                }
                else -> {
                    statusText.text = "Annulé par l'utilisateur"
                }
            }
        }
    }

    private fun connectAppRemote() {
        val connectionParams = ConnectionParams.Builder(CLIENT_ID)
            .setRedirectUri(REDIRECT_URI)
            .showAuthView(false) // Plus besoin de montrer la vue, on vient de le faire
            .build()

        SpotifyAppRemote.connect(this, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                runOnUiThread {
                    statusText.text = "SUCCÈS TOTAL !\n\nLancement du Widget..."
                    
                    // On démarre le service du Widget maintenant que l'auth est faite
                    val intent = Intent(this@MainActivity, SpotifyWidgetService::class.java)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                }
                Log.d("MainActivity", "Connected to App Remote & Service started!")
            }

            override fun onFailure(throwable: Throwable) {
                runOnUiThread {
                    statusText.text = "Erreur de connexion Remote : ${throwable.message}"
                }
                Log.e("MainActivity", throwable.message, throwable)
            }
        })
    }

    override fun onStop() {
        super.onStop()
        // On garde la connexion active si possible
    }
}
