package io.github.climbintelligence.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.climbintelligence.R
import io.github.climbintelligence.ui.theme.Theme

@Composable
internal fun SubScreenScaffold(
    title: String,
    onNavigateBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateBack() }
                .padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "\u2190",
                color = Theme.colors.dim,
                fontSize = 16.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = title,
                color = Theme.colors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        SettingsDivider()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            content = content
        )
    }
}

@Composable
internal fun MenuRow(
    label: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Theme.colors.text,
                fontSize = 13.sp
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = Theme.colors.dim,
                    fontSize = 10.sp
                )
            }
        }
        Text(
            text = ">",
            color = Theme.colors.dim,
            fontSize = 16.sp
        )
    }
}

@Composable
internal fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Theme.colors.divider)
    )
}

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        color = Theme.colors.dim,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
    )
}

@Composable
internal fun HintText(text: String) {
    Text(
        text = text,
        color = Theme.colors.dim,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@Composable
internal fun PresetLabel(text: String) {
    Text(
        text = text,
        color = Theme.colors.text,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
internal fun NumericRow(
    label: String,
    value: Int,
    unit: String,
    onValueChange: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Theme.colors.text,
            fontSize = 13.sp
        )
        Text(
            text = if (value > 0) "$value $unit" else "---",
            color = if (value > 0) Theme.colors.optimal else Theme.colors.dim,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }

    if (showDialog) {
        NumericInputDialog(
            title = label,
            initialValue = if (value > 0) value.toString() else "",
            unit = unit,
            onDismiss = { showDialog = false },
            onConfirm = { text ->
                text.trim().toIntOrNull()?.let { onValueChange(it) }
                showDialog = false
            }
        )
    }
}

@Composable
internal fun DecimalRow(
    label: String,
    value: Double,
    unit: String,
    onValueChange: (Double) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Theme.colors.text,
            fontSize = 13.sp
        )
        val display = if (value > 0) {
            if (value < 1) "%.3f".format(value) else "%.1f".format(value)
        } else "---"
        Text(
            text = if (value > 0) "$display $unit".trim() else "---",
            color = if (value > 0) Theme.colors.optimal else Theme.colors.dim,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }

    if (showDialog) {
        NumericInputDialog(
            title = label,
            initialValue = if (value > 0) {
                if (value < 1) "%.3f".format(value) else "%.1f".format(value)
            } else "",
            unit = unit,
            onDismiss = { showDialog = false },
            onConfirm = { text ->
                text.trim().toDoubleOrNull()?.let { onValueChange(it) }
                showDialog = false
            }
        )
    }
}

@Composable
internal fun NumericInputDialog(
    title: String,
    initialValue: String,
    unit: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    val focusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            onConfirm(text)
                        }
                    ),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Theme.colors.background,
                        unfocusedContainerColor = Theme.colors.background,
                        focusedTextColor = Theme.colors.text,
                        unfocusedTextColor = Theme.colors.text,
                        cursorColor = Theme.colors.optimal
                    ),
                    modifier = Modifier.weight(1f)
                )
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = unit,
                        color = Theme.colors.dim,
                        fontSize = 14.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(stringResource(R.string.ok), color = Theme.colors.optimal, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = Theme.colors.dim)
            }
        },
        containerColor = Theme.colors.surface,
        titleContentColor = Theme.colors.text,
        textContentColor = Theme.colors.text
    )
}

@Composable
internal fun ToggleRow(
    label: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Theme.colors.text,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Theme.colors.background,
                checkedTrackColor = Theme.colors.optimal,
                uncheckedThumbColor = Theme.colors.dim,
                uncheckedTrackColor = Theme.colors.surface
            )
        )
    }
}

@Composable
internal fun ExpandableRow(
    label: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Theme.colors.textSecondary,
            fontSize = 12.sp
        )
        Text(
            text = if (expanded) "\u25B2" else "\u25BC",
            color = Theme.colors.dim,
            fontSize = 14.sp
        )
    }
}

@Composable
internal fun NavigationRow(
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Theme.colors.text,
            fontSize = 13.sp
        )
        Text(
            text = ">",
            color = Theme.colors.dim,
            fontSize = 16.sp
        )
    }
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Theme.colors.text,
            fontSize = 13.sp
        )
        Text(
            text = value,
            color = Theme.colors.dim,
            fontSize = 12.sp
        )
    }
}

@Composable
internal fun CpRow(
    cp: Int,
    effectiveCp: Int,
    onValueChange: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.settings_cp),
            color = Theme.colors.text,
            fontSize = 13.sp
        )
        Text(
            text = if (cp > 0) "$cp W" else "Auto ($effectiveCp W)",
            color = Theme.colors.optimal,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }

    if (showDialog) {
        NumericInputDialog(
            title = stringResource(R.string.settings_cp),
            initialValue = if (cp > 0) cp.toString() else "",
            unit = "W",
            onDismiss = { showDialog = false },
            onConfirm = { text ->
                val value = text.trim().toIntOrNull() ?: 0
                onValueChange(value)
                showDialog = false
            }
        )
    }
}
