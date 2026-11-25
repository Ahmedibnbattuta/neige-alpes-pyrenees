package fr.neige.stations.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import fr.neige.stations.R
import kotlin.math.roundToInt

/** Réglage des alertes « neige fraîche » (favoris, seuil sur 48 h). */
@Composable
fun AlertsDialog(
    enabled: Boolean,
    thresholdCm: Int,
    hasFavorites: Boolean,
    onEnabled: (Boolean) -> Unit,
    onThreshold: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val u = LocalUnits.current
    var denied by remember { mutableStateOf(false) }
    var slider by remember { mutableFloatStateOf(thresholdCm.toFloat()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        onEnabled(granted)
    }

    fun enable(on: Boolean) {
        if (on && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else onEnabled(on)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.alerts_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.alerts_enable), modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enable(it) })
                }
                Text(stringResource(R.string.alerts_threshold, fmtDepth(slider.roundToInt(), u)))
                Slider(
                    value = slider,
                    onValueChange = { slider = (it / 5f).roundToInt() * 5f },
                    onValueChangeFinished = { onThreshold(slider.roundToInt()) },
                    valueRange = 5f..60f,
                    steps = 10,
                    enabled = enabled,
                )
                if (!hasFavorites) Text(stringResource(R.string.alerts_no_favorites), color = MaterialTheme.colorScheme.tertiary)
                if (denied) Text(stringResource(R.string.alerts_permission_denied), color = MaterialTheme.colorScheme.error)
                Text(
                    stringResource(R.string.alerts_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
