package fr.neige.stations.background

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
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.neige.stations.MainActivity
import fr.neige.stations.R
import fr.neige.stations.data.SnowApi
import fr.neige.stations.data.Stations
import fr.neige.stations.ui.UnitSystem
import fr.neige.stations.ui.fmtFall
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Tâche de fond (toutes les 6 h, réseau requis) :
 * met à jour le widget des favoris et envoie une alerte « neige fraîche » si le seuil est atteint.
 * Seuls les favoris sont interrogés, pour rester très loin des limites d'Open-Meteo.
 */
class SnowWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        Stations.init(ctx)
        val prefs = ctx.getSharedPreferences("neige", Context.MODE_PRIVATE)
        val favIds = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
        val favorites = Stations.all.filter { it.id in favIds }.take(MAX_FAVORITES)

        val data = try {
            SnowApi(ctx.cacheDir).fetchFavorites(favorites)
        } catch (e: Exception) {
            return if (runAttemptCount < 2) Result.retry() else Result.success()
        }

        WidgetData.save(ctx, favorites.map { s ->
            val d = data[s.id]
            WidgetRow(s.name, d?.topCm, d?.next48hCm)
        })
        runCatching { FavoritesWidget().updateAll(ctx) }

        if (prefs.getBoolean(KEY_ALERTS, false)) {
            val threshold = prefs.getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD_CM)
            val units = prefs.getString("units", null)
                ?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() } ?: UnitSystem.default()
            val today = LocalDate.now().toString()
            val already = prefs.getStringSet(KEY_NOTIFIED, emptySet()) ?: emptySet()
            val notified = already.filter { it.endsWith(today) }.toMutableSet() // on oublie les jours passés
            favorites.forEach { s ->
                val fall = data[s.id]?.next48hCm ?: return@forEach
                val key = "${s.id}|$today"
                if (fall >= threshold && key !in notified) {
                    notify(ctx, s.id.hashCode(), s.name, fmtFall(fall, units))
                    notified += key
                }
            }
            prefs.edit().putStringSet(KEY_NOTIFIED, notified).apply()
        }
        return Result.success()
    }

    private fun notify(ctx: Context, id: Int, name: String, amount: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_snow)
            .setContentTitle(ctx.getString(R.string.notif_title, name))
            .setContentText(ctx.getString(R.string.notif_text, amount))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    companion object {
        const val KEY_ALERTS = "alerts_enabled"
        const val KEY_THRESHOLD = "alerts_threshold_cm"
        const val DEFAULT_THRESHOLD_CM = 20
        private const val KEY_NOTIFIED = "alerts_notified"
        private const val CHANNEL_ID = "fresh_snow"
        private const val MAX_FAVORITES = 30
        private const val PERIODIC = "snow-check"
        private const val NOW = "snow-check-now"

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun createChannel(ctx: Context) {
            val ch = NotificationChannel(CHANNEL_ID, ctx.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT)
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }

        /** Planifie la vérification toutes les 6 heures (une seule tâche, conservée si déjà planifiée). */
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<SnowWorker>(6, TimeUnit.HOURS).setConstraints(network).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        /** Mise à jour immédiate (favoris modifiés, réglages changés). */
        fun runNow(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<SnowWorker>().setConstraints(network).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
        }
    }
}
