package rocks.pkb.plektra

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppIconSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedVariant by remember { mutableStateOf(AppIconManager.selectedVariant(context)) }

    fun select(variant: AppIconVariant) {
        if (selectedVariant == variant) return
        AppIconManager.selectVariant(context, variant)
        selectedVariant = variant
        Toast.makeText(context, R.string.app_icon_update_hint, Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            PlektraSettingsHeader(stringResource(R.string.app_icon_title), onBack)
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .settingRow(SettingLinkIds.CUSTOMIZATION_APP_ICON),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Text(
                text = stringResource(R.string.app_icon_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            IconFamilySection(
                title = stringResource(R.string.app_icon_orange),
                closedPreview = R.drawable.closed_orange_launcher_preview,
                openPreview = R.drawable.open_orange_launcher_preview,
                closedSelected = selectedVariant == AppIconVariant.ClosedOrange,
                openSelected = selectedVariant == AppIconVariant.OpenOrange,
                onClosedClick = { select(AppIconVariant.ClosedOrange) },
                onOpenClick = { select(AppIconVariant.OpenOrange) }
            )

            IconFamilySection(
                title = stringResource(R.string.app_icon_blue),
                closedPreview = R.drawable.closed_blue_launcher_preview,
                openPreview = R.drawable.open_blue_launcher_preview,
                closedSelected = selectedVariant == AppIconVariant.ClosedBlue,
                openSelected = selectedVariant == AppIconVariant.OpenBlue,
                onClosedClick = { select(AppIconVariant.ClosedBlue) },
                onOpenClick = { select(AppIconVariant.OpenBlue) }
            )
        }
    }
}

@Composable
private fun IconFamilySection(
    title: String,
    closedPreview: Int,
    openPreview: Int,
    closedSelected: Boolean,
    openSelected: Boolean,
    onClosedClick: () -> Unit,
    onOpenClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIconChoiceCard(
                title = stringResource(R.string.app_icon_closed),
                iconRes = closedPreview,
                selected = closedSelected,
                onClick = onClosedClick,
                modifier = Modifier.weight(1f)
            )
            AppIconChoiceCard(
                title = stringResource(R.string.app_icon_open),
                iconRes = openPreview,
                selected = openSelected,
                onClick = onOpenClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AppIconChoiceCard(
    title: String,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val border = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    Surface(
        modifier = modifier.selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = onClick
        ),
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        border = border
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier
                        .size(82.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .align(Alignment.Center)
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .align(Alignment.TopEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.app_icon_selected),
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}
