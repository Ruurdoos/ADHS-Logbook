package com.example.adhslogbook.ui.screens.insights

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.adhslogbook.data.model.InsightsContent
import com.example.adhslogbook.R
import com.example.adhslogbook.navigation.FocusLogDestination
import com.example.adhslogbook.navigation.ProductDestinations
import com.example.adhslogbook.ui.components.FocusLogBottomBar
import com.example.adhslogbook.ui.components.FocusLogTopBar
import com.example.adhslogbook.ui.components.ScreenStateHost
import com.example.adhslogbook.ui.theme.FocusLogTheme
import com.example.adhslogbook.ui.viewmodels.LogbookViewModel
import kotlinx.coroutines.launch

@Composable
fun InsightsRoute(
    viewModel: LogbookViewModel,
    currentDestination: FocusLogDestination,
    onNavigate: (FocusLogDestination) -> Unit,
    onHomeClick: () -> Unit,
) {
    val state by viewModel.insightsContent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = { FocusLogTopBar(onHomeClick) },
        bottomBar = { FocusLogBottomBar(ProductDestinations, currentDestination, onNavigate) },
    ) { padding ->
        ScreenStateHost(
            state = state,
            modifier = Modifier.padding(padding).fillMaxSize(),
            loadingMessage = stringResource(R.string.loading_weekly_insights),
            emptyMessage = stringResource(R.string.no_weekly_checkins),
        ) { content ->
            InsightsContent(
                content = content,
                modifier = Modifier.fillMaxSize(),
                onExport = {
                    scope.launch {
                        val report = viewModel.createExportText()
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, report)
                                },
                                context.getString(R.string.export_for_doctor),
                            )
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun InsightsContent(content: InsightsContent, modifier: Modifier, onExport: () -> Unit) {
    val spacing = FocusLogTheme.spacing
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(spacing.page),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item {
            Text(stringResource(R.string.weekly_insights), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.weekly_basis),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Button(onClick = onExport) {
                Icon(Icons.Outlined.Share, contentDescription = null)
                Text(stringResource(R.string.export_report))
            }
        }
        if (content.cards.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.insight_minimum),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            content.cards.forEach { card ->
                item {
                    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Column(Modifier.fillMaxWidth().padding(18.dp)) {
                            Text(card.title, style = MaterialTheme.typography.titleMedium)
                            Text(card.message, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
        if (content.bars.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.no_weekly_checkins),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item { WeeklyBars(content) }
        }
    }
}

@Composable
private fun WeeklyBars(content: InsightsContent) {
    Surface(shape = RoundedCornerShape(22.dp), tonalElevation = 1.dp) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.average_daily_focus), style = MaterialTheme.typography.titleLarge)
            Row(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                content.bars.forEach { bar ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .width(28.dp)
                                .height((bar.value.coerceIn(0f, 1f) * 130f).dp)
                                .background(
                                    if (bar.highlighted) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.secondary,
                                    RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                                )
                        )
                        Text(bar.label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
