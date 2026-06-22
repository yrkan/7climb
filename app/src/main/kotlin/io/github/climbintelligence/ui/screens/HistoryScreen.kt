package io.github.climbintelligence.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.climbintelligence.ClimbIntelligenceExtension
import io.github.climbintelligence.R
import io.github.climbintelligence.data.database.AttemptEntity
import io.github.climbintelligence.data.database.ClimbEntity
import io.github.climbintelligence.ui.theme.Theme
import io.github.climbintelligence.util.PhysicsUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.tooling.preview.Preview
import io.github.climbintelligence.ui.theme.ClimbIntelligenceTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit
) {
    val repo = remember { ClimbIntelligenceExtension.instance?.climbRepository }

    var climbs by remember { mutableStateOf<List<ClimbEntity>>(emptyList()) }
    var recentAttempts by remember { mutableStateOf<List<AttemptEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            repo?.let {
                climbs = it.getAllClimbs()
                recentAttempts = it.getRecentAttempts(50)
            }
        }
    }

    HistoryScreenContent(
        climbs = climbs,
        recentAttempts = recentAttempts,
        onNavigateBack = onNavigateBack
    )
}

@Composable
internal fun HistoryScreenContent(
    climbs: List<ClimbEntity>,
    recentAttempts: List<AttemptEntity>,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "<-",
                color = Theme.colors.dim,
                fontSize = 16.sp,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onNavigateBack() }
                    .padding(end = 8.dp)
            )
            Text(
                text = stringResource(R.string.history_title),
                color = Theme.colors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Theme.colors.divider)
        )

        if (climbs.isEmpty() && recentAttempts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.history_empty),
                    color = Theme.colors.dim,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // Climbs section
                if (climbs.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.history_climbs).uppercase(),
                            color = Theme.colors.dim,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                    items(climbs) { climb ->
                        ClimbRow(climb)
                    }
                }

                // Recent attempts section
                if (recentAttempts.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.history_recent).uppercase(),
                            color = Theme.colors.dim,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(
                                start = 12.dp, end = 12.dp,
                                top = 16.dp, bottom = 8.dp
                            )
                        )
                    }
                    items(recentAttempts) { attempt ->
                        AttemptRow(attempt)
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ClimbRow(climb: ClimbEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Theme.colors.surface)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = climb.name,
                color = Theme.colors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (climb.category > 0) {
                Text(
                    text = "Cat ${climb.category}",
                    color = Theme.colors.attention,
                    fontSize = 11.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "%.1f km".format(climb.length / 1000.0),
                color = Theme.colors.dim,
                fontSize = 11.sp
            )
            Text(
                text = "%.0f m".format(climb.elevation),
                color = Theme.colors.dim,
                fontSize = 11.sp
            )
            Text(
                text = "%.1f%%".format(climb.avgGrade),
                color = Theme.colors.dim,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun AttemptRow(attempt: AttemptEntity) {
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Theme.colors.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = PhysicsUtils.formatTime(attempt.timeMs.milliseconds.inWholeSeconds),
                color = Theme.colors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = dateFormat.format(Date(attempt.date)),
                color = Theme.colors.dim,
                fontSize = 10.sp
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (attempt.avgPower > 0) {
                Text(
                    text = "${attempt.avgPower}W",
                    color = Theme.colors.textSecondary,
                    fontSize = 11.sp
                )
            }
            if (attempt.avgHR > 0) {
                Text(
                    text = "${attempt.avgHR}bpm",
                    color = Theme.colors.textSecondary,
                    fontSize = 11.sp
                )
            }
            if (attempt.isPR) {
                Text(
                    text = "PR",
                    color = Theme.colors.optimal,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private val CLIMBS
    get() = listOf(
        ClimbEntity(
            id = "1",
            name = "Alpe d'Huez",
            latitude = 45.11,
            longitude = 6.08,
            length = 13800.0,
            elevation = 1085.0,
            avgGrade = 7.9,
            maxGrade = 12.0,
            category = 1
        ),
        ClimbEntity(
            id = "2",
            name = "Mont Ventoux",
            latitude = 44.17,
            longitude = 5.27,
            length = 21400.0,
            elevation = 1589.0,
            avgGrade = 7.4,
            maxGrade = 11.0,
            category = 1
        )
    )
private val ATTEMPTS get() = listOf(
    AttemptEntity(
        id = 1L,
        climbId = "1",
        date = 1719000000000L,
        timeMs = 50.minutes.inWholeMilliseconds,
        avgPower = 280,
        normalizedPower = 290,
        avgHR = 165,
        maxHR = 180,
        isPR = true
    ),
    AttemptEntity(
        id = 2L,
        climbId = "2",
        date = 1719010000000L,
        timeMs = 45.minutes.inWholeMilliseconds,
        avgPower = 265,
        normalizedPower = 270,
        avgHR = 160,
        maxHR = 175,
        isPR = false
    )
)

@Preview(widthDp = 256, heightDp = 600)
@Composable
private fun HistoryScreenPreview() {
    ClimbIntelligenceTheme {
        HistoryScreenContent(climbs = CLIMBS, recentAttempts = ATTEMPTS, onNavigateBack = {})
    }
}
