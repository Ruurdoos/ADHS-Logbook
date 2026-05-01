package com.example.adhslogbook.ui.screens.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhslogbook.data.model.CurvePoint
import com.example.adhslogbook.data.model.SideEffectItem
import com.example.adhslogbook.data.model.TimelineDay
import com.example.adhslogbook.data.model.TimelineEvent
import com.example.adhslogbook.data.model.TimelineEventType
import com.example.adhslogbook.navigation.FocusLogDestination
import com.example.adhslogbook.navigation.ProductDestinations
import com.example.adhslogbook.ui.components.FocusLogBottomBar
import com.example.adhslogbook.ui.components.FocusLogTopBar
import com.example.adhslogbook.ui.components.ScreenStateHost
import com.example.adhslogbook.ui.theme.FocusLogPalette
import com.example.adhslogbook.ui.theme.FocusLogTheme

@Composable
fun TimelineRoute(
    currentDestination: FocusLogDestination,
    onNavigate: (FocusLogDestination) -> Unit,
    onHomeClick: () -> Unit,
    viewModel: TimelineViewModel = viewModel(),
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::addEvent,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Add event")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        ScreenStateHost(
            state = uiState.contentState,
            onRetry = viewModel::retry,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            loadingMessage = "Loading timeline",
            emptyMessage = "No timeline events have been logged yet.",
        ) { content ->
            TimelineScreen(
                content = content,
                contentPadding = innerPadding,
                hasPrevious = uiState.hasPrevious,
                hasNext = uiState.hasNext,
                onPrevious = viewModel::showPreviousDay,
                onNext = viewModel::showNextDay,
            )
        }
    }
}

@Composable
private fun TimelineScreen(
    content: TimelineDay,
    contentPadding: PaddingValues,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val spacing = FocusLogTheme.spacing

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = spacing.page,
            top = spacing.lg,
            end = spacing.page,
            bottom = contentPadding.calculateBottomPadding() + spacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        item {
            TimelineDaySwitcher(
                dateLabel = content.dateLabel,
                subtitle = content.subtitle,
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                onPrevious = onPrevious,
                onNext = onNext,
            )
        }

        item {
            TimelineCurveCard(
                actualCurve = content.actualCurve,
                expectedCurve = content.expectedCurve,
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Text(text = "Activity Log", style = MaterialTheme.typography.titleLarge)
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    content.events.forEachIndexed { index, event ->
                        TimelineEventItem(
                            event = event,
                            showConnector = index != content.events.lastIndex,
                        )
                    }
                }
            }
        }

        item {
            SideEffectsCard(items = content.sideEffects)
        }
    }
}

@Composable
private fun TimelineDaySwitcher(
    dateLabel: String,
    subtitle: String,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious, enabled = hasPrevious) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous day")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dateLabel, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onNext, enabled = hasNext) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next day")
            }
        }
    }
}

@Composable
private fun TimelineCurveCard(
    actualCurve: List<CurvePoint>,
    expectedCurve: List<CurvePoint>,
) {
    val spacing = FocusLogTheme.spacing
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val actualColor = MaterialTheme.colorScheme.primary
    val expectedColor = FocusLogPalette.Tertiary.copy(alpha = 0.55f)
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = "Effectiveness Curve",
                style = MaterialTheme.typography.titleLarge,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    repeat(4) { row ->
                        val y = size.height * row / 3f
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 2f,
                        )
                    }

                    drawPath(
                        path = curveToPath(expectedCurve, size.width, size.height),
                        color = expectedColor,
                        style = Stroke(
                            width = 6f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 10f)),
                            cap = StrokeCap.Round,
                        ),
                    )
                    drawPath(
                        path = curveToPath(actualCurve, size.width, size.height),
                        color = actualColor,
                        style = Stroke(width = 8f, cap = StrokeCap.Round),
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    listOf("8:00", "12:00", "16:00", "20:00").forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                LegendSwatch(
                    label = "My Experience",
                    color = MaterialTheme.colorScheme.primary,
                    dashed = false,
                )
                LegendSwatch(
                    label = "Expected",
                    color = FocusLogPalette.Tertiary.copy(alpha = 0.55f),
                    dashed = true,
                    modifier = Modifier.padding(start = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun LegendSwatch(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    dashed: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(
            modifier = Modifier
                .width(24.dp)
                .height(6.dp)
        ) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = size.height,
                cap = StrokeCap.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 6f)) else null,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimelineEventItem(
    event: TimelineEvent,
    showConnector: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = if (event.type == TimelineEventType.Dose) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = CircleShape,
                    )
            )
            if (showConnector) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .width(2.dp)
                        .height(72.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = event.time,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = timelineIcon(event.type),
                        contentDescription = null,
                        tint = when (event.type) {
                            TimelineEventType.Dose -> MaterialTheme.colorScheme.primary
                            TimelineEventType.Focus -> MaterialTheme.colorScheme.secondary
                            TimelineEventType.Meal -> FocusLogPalette.Tertiary
                            TimelineEventType.SideEffect -> FocusLogPalette.Error
                            TimelineEventType.Note -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(text = event.title, style = MaterialTheme.typography.titleMedium)
                }
                if (event.description.isNotBlank()) {
                    Text(
                        text = event.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SideEffectsCard(items: List<SideEffectItem>) {
    val spacing = FocusLogTheme.spacing
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = "Side Effects Noted",
                style = MaterialTheme.typography.titleLarge,
            )
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                items.forEach { item ->
                    Surface(
                        shape = CircleShape,
                        color = if (item.highlighted) {
                            FocusLogPalette.ErrorContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = if (item.highlighted) {
                                    FocusLogPalette.OnErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (item.highlighted) {
                                    FocusLogPalette.OnErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun timelineIcon(type: TimelineEventType) = when (type) {
    TimelineEventType.Dose -> Icons.Outlined.LocalPharmacy
    TimelineEventType.Focus -> Icons.Outlined.Bolt
    TimelineEventType.Meal -> Icons.Outlined.Restaurant
    TimelineEventType.SideEffect -> Icons.Outlined.WarningAmber
    TimelineEventType.Note -> Icons.AutoMirrored.Outlined.Notes
}

private fun curveToPath(
    points: List<CurvePoint>,
    width: Float,
    height: Float,
): Path {
    val path = Path()
    points.forEachIndexed { index, point ->
        val x = width * point.x
        val y = height * point.y
        if (index == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    return path
}
