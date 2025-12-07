# Règles Proguard pour Spotify Widget

# Ignorer les warnings sur les classes manquantes du SDK Spotify
# Le SDK utilise Jackson optionnellement mais nous utilisons Gson
-dontwarn com.fasterxml.jackson.**
-dontwarn com.spotify.base.annotations.**
-dontwarn javax.annotation.**

# Garder les classes du SDK Spotify pour éviter qu'elles ne soient supprimées
-keep class com.spotify.** { *; }

# Garder notre code (optionnel si on veut tout obfusquer, mais plus sûr pour un début)
-keep class com.example.spotifywidget.** { *; }
