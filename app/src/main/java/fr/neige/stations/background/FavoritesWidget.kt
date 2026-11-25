package fr.neige.stations.background

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import fr.neige.stations.LocaleHelper
import fr.neige.stations.MainActivity
import fr.neige.stations.R
import fr.neige.stations.ui.UnitSystem
import fr.neige.stations.ui.fmtDepth
import fr.neige.stations.ui.fmtFall

/** Widget d'écran d'accueil : neige au sommet de vos favoris + chutes prévues sur 48 h. */
class FavoritesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (rows, _) = WidgetData.load(context)
        val units = context.getSharedPreferences("neige", Context.MODE_PRIVATE).getString("units", null)
            ?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() } ?: UnitSystem.default()
        val localized = LocaleHelper.wrap(context)
        val title = localized.getString(R.string.widget_title)
        val empty = localized.getString(R.string.widget_empty)
        provideContent {
            GlanceTheme { Content(title, empty, rows, units) }
        }
    }

    @Composable
    private fun Content(title: String, empty: String, rows: List<WidgetRow>, units: UnitSystem) {
        val ink = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp)
        Column(
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(16.dp)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text(title, style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp))
            Spacer(GlanceModifier.height(6.dp))
            if (rows.isEmpty()) {
                Text(empty, style = ink)
            } else {
                rows.take(6).forEach { r ->
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(r.name, style = ink, maxLines = 1, modifier = GlanceModifier.defaultWeight())
                        Text(
                            fmtDepth(r.topCm, units) + (r.next48hCm?.takeIf { it >= 1 }?.let { "  " + fmtFall(it, units) } ?: ""),
                            style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                        )
                    }
                }
            }
        }
    }
}

class FavoritesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FavoritesWidget()
}
