package com.example.adhslogbook.ui.screens.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.SentimentNeutral
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhslogbook.data.model.CheckInMetric
import com.example.adhslogbook.data.model.CurvePoint
import com.example.adhslogbook.data.model.MedicationStatus
import com.example.adhslogbook.data.model.MetricType
import com.example.adhslogbook.data.model.TodayContent
import com.example.adhslogbook.navigation.FocusLogDestination
import com.example.adhslogbook.navigation.ProductDestinations
import com.example.adhslogbook.ui.components.FocusLogBottomBar
import com.example.adhslogbook.ui.components.FocusLogTopBar
import com.example.adhslogbook.ui.components.ScreenStateHost
import com.example.adhslogbook.ui.theme.ADHSLogbookTheme
import com.example.adhslogbook.ui.theme.FocusLogPalette
import com.example.adhslogbook.ui.theme.FocusLogTheme

@Composable
fun TodayRoute(
    currentDestination: FocusLogDestination,
    onNavigate: (FocusLogDestination) -> Unit,
    onHomeClick: () -> Unit,
    viewModel: TodayViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeSnackbar()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            FocusLogTopBar(
                onHomeClick = onHomeClick,
                onSettingsClick = {},
            )
        },
        bottomBar = {
            FocusLogBottomBar(
                items = ProductDestinations,
                currentDestination = currentDestination,
                onNavigate = onNavigate,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        ScreenStateHost(
            state = uiState.contentState,
            onRetry = viewModel::retry,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            loadingMessage = "Loading today's check-in",
            emptyMessage = "No check-in template has been prepared yet.",
        ) { content ->
            TodayScreen(
                content = content,
                contentPadding = innerPadding,
                onMetricChange = viewModel::updateMetric,
                onToggleTag = viewModel::toggleTag,
                onAddTag = viewModel::addQuickTag,
                onNotesChange = viewModel::updateNotes,
                onLogNextDose = viewModel::logNextDose,
            )
        }
    }
}

@Composable
private fun TodayScreen(
    content: TodayContent,
    contentPadding: PaddingValues,
    onMetricChange: (MetricType, Float) -> Unit,
    onToggleTag: (String) -> Unit,
    onAddTag: () -> Unit,
    onNotesChange: (String) -> Unit,
    onLogNextDose: () -> Unit,
) {
    val spacing = FocusLogTheme.spacing

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = spacing.page,
            top = spacing.lg,
            end = spacing.page,
            bottom = contentPadding.calculateBottomPadding() + spacing.lg,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "TODAY",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = content.dateLabel,
                        style = MaterialTheme.typography.headlineLarge,
                    )
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Box(
                        modifier = Modifier
                            .size(spacing.touchTarget)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.DateRange, contentDescription = "Calendar")
                    }
                }
            }
        }

        item {
            MedicationCard(
                content = content,
                onLogNextDose = onLogNextDose,
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Text(
                    text = "Quick Check-in",
                    style = MaterialTheme.typography.titleLarge,
                )
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    content.metrics.forEach { metric ->
                        MetricSliderCard(metric = metric) { onMetricChange(metric.type, it) }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Text(text = "Tags", style = MaterialTheme.typography.titleLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    content.tags.forEach { tag ->
                        FilterChip(
                            selected = tag.selected,
                            onClick = { onToggleTag(tag.label) },
                            label = { Text(tag.label) },
                            leadingIcon = if (tag.selected) {
                                { Icon(Icons.Outlined.Check, contentDescription = null) }
                            } else {
                                null
                            },
                        )
                    }
                    AssistChip(
                        onClick = onAddTag,
                        label = { Text("Add") },
                        leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = content.notes,
                onValueChange = onNotesChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp),
                label = { Text("Notes") },
                placeholder = { Text("How are you feeling right now?") },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                shape = RoundedCornerShape(24.dp),
            )
        }
    }
}

@Composable
private fun MedicationCard(
    content: TodayContent,
    onLogNextDose: () -> Unit,
) {
    val spacing = FocusLogTheme.spacing
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.LocalPharmacy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Column {
                        Text(content.medication.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            content.medication.dosage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                MedicationStatusChip(status = content.medication.status)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                MetricInfoBox(
                    modifier = Modifier.weight(1f),
                    label = "Last Taken",
                    value = content.lastTaken,
                )
                MetricInfoBox(
                    modifier = Modifier.weight(1f),
                    label = "Est. Duration",
                    value = content.estimatedDuration,
                )
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Effectiveness Curve",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CompactEffectivenessChart(
                        points = content.effectCurve,
                        nowMarker = content.nowMarker,
                    )
                }
            }

            Button(
                onClick = onLogNextDose,
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text(
                    text = "Log Next Dose",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun MedicationStatusChip(status: MedicationStatus) {
    val (label, background, content) = when (status) {
        MedicationStatus.Taken -> Triple(
            "Taken",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        MedicationStatus.Scheduled -> Triple(
            "Scheduled",
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.primary,
        )
        MedicationStatus.Due -> Triple(
            "Due",
            FocusLogPalette.ErrorContainer,
            FocusLogPalette.OnErrorContainer,
        )
    }

    Surface(
        shape = CircleShape,
        color = background,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(content, CircleShape),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = content,
            )
        }
    }
}

@Composable
private fun MetricInfoBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun MetricSliderCard(
    metric: CheckInMetric,
    onValueChange: (Float) -> Unit,
) {
    val icon = when (metric.type) {
        MetricType.Focus -> Icons.Outlined.TrackChanges
        MetricType.Mood -> Icons.Outlined.SentimentNeutral
        MetricType.Energy -> Icons.Outlined.Bolt
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Text(
                        text = metric.type.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    text = metric.descriptor,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Slider(
                value = metric.value,
                onValueChange = onValueChange,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

@Composable
private fun CompactEffectivenessChart(
    points: List<CurvePoint>,
    nowMarker: Float,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val markerColor = FocusLogPalette.Error.copy(alpha = 0.5f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (points.isEmpty()) return@Canvas

            val path = Path()
            points.forEachIndexed { index, point ->
                val x = size.width * point.x
                val y = size.height * point.y
                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 6f, cap = StrokeCap.Round),
            )

            val markerX = size.width * nowMarker
            drawLine(
                color = markerColor,
                start = Offset(markerX, 0f),
                end = Offset(markerX, size.height),
                strokeWidth = 4f,
            )
        }

        Box(
            modifier = Modifier
                .align(BiasAlignment(horizontalBias = nowMarker * 2 - 1, verticalBias = -1f))
                .padding(top = 4.dp)
                .background(
                    color = FocusLogPalette.Error,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = "NOW",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("8 AM", "12 PM", "4 PM", "8 PM").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CompactEffectivenessChartPreview() {
    ADHSLogbookTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            CompactEffectivenessChart(
                points = listOf(
                    CurvePoint(0f, 0.8f),
                    CurvePoint(0.2f, 0.6f),
                    CurvePoint(0.4f, 0.4f),
                    CurvePoint(0.6f, 0.5f),
                    CurvePoint(0.8f, 0.7f),
                    CurvePoint(1f, 0.9f),
                ),
                nowMarker = 0.7f
            )
        }
    }
}
