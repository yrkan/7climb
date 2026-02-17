package io.github.climbintelligence.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.climbintelligence.BuildConfig
import io.github.climbintelligence.R
import io.github.climbintelligence.ui.theme.Theme

@Composable
fun AboutScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current

    SubScreenScaffold(
        title = stringResource(R.string.about_title),
        onNavigateBack = onNavigateBack
    ) {
        InfoRow(
            label = stringResource(R.string.settings_version),
            value = BuildConfig.VERSION_NAME
        )

        SettingsDivider()

        AboutLinkRow(
            label = stringResource(R.string.about_github),
            onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yrkan/7climb"))
                )
            }
        )

        SettingsDivider()

        AboutLinkRow(
            label = stringResource(R.string.about_privacy),
            onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://7climb.com/privacy"))
                )
            }
        )

        SettingsDivider()

        AboutLinkRow(
            label = stringResource(R.string.about_contact),
            onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${context.getString(R.string.about_contact_email)}"))
                )
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AboutLinkRow(
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
            text = "\u2197",
            color = Theme.colors.dim,
            fontSize = 14.sp
        )
    }
}
