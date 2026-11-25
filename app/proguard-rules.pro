# Règles R8 de l'application. Compose, coroutines et org.json (fourni par Android)
# n'ont pas besoin de règles particulières.

# Retire les journaux de débogage de la version distribuée.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# osmdroid (carte OpenStreetMap)
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
