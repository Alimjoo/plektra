package rocks.pkb.plektra

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun MainBackgroundSettingsScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(MainBackgroundPreferences.get(context)) }
    val options = listOf(
        MainBackgroundPreference.Daily to R.string.main_background_daily,
        MainBackgroundPreference.Strings to R.string.main_background_strings,
        MainBackgroundPreference.Keyboard to R.string.main_background_keyboard
    )

    Scaffold(
        topBar = {
            PlektraSettingsHeader(stringResource(R.string.main_background_title), onBack)
        }
    ) { padding ->
        Column(
            modifier = modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp)
        ) {
            Text(
                stringResource(R.string.main_background_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            options.forEach { (preference, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth().selectable(
                        selected = selected == preference,
                        role = Role.RadioButton,
                        onClick = {
                            selected = preference
                            MainBackgroundPreferences.set(context, preference)
                        }
                    ).padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected == preference, onClick = null)
                    Column(modifier = Modifier.padding(start = 14.dp)) {
                        Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
                        if (preference == MainBackgroundPreference.Daily) {
                            Text(
                                stringResource(R.string.main_background_daily_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
