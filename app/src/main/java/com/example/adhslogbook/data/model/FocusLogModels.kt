package com.example.adhslogbook.data.model

enum class MetricType {
    Focus,
    Mood,
    Energy,
}

data class CheckInMetric(
    val type: MetricType,
    val value: Float,
    val descriptor: String,
)

data class TagOption(
    val label: String,
    val selected: Boolean = false,
)

enum class MedicationStatus {
    Taken,
    Scheduled,
    Due,
}

data class MedicationSummary(
    val name: String,
    val dosage: String,
    val status: MedicationStatus,
)

data class CurvePoint(
    val x: Float,
    val y: Float,
)

data class TodayContent(
    val dateLabel: String,
    val medication: MedicationSummary,
    val lastTaken: String,
    val estimatedDuration: String,
    val nowMarker: Float,
    val effectCurve: List<CurvePoint>,
    val metrics: List<CheckInMetric>,
    val tags: List<TagOption>,
    val notes: String,
)

enum class TimelineEventType {
    Dose,
    Focus,
    Meal,
    SideEffect,
    Note,
}

data class TimelineEvent(
    val id: String,
    val time: String,
    val title: String,
    val description: String = "",
    val type: TimelineEventType,
)

data class SideEffectItem(
    val label: String,
    val highlighted: Boolean = false,
)

data class TimelineDay(
    val dateLabel: String,
    val subtitle: String,
    val actualCurve: List<CurvePoint>,
    val expectedCurve: List<CurvePoint>,
    val events: List<TimelineEvent>,
    val sideEffects: List<SideEffectItem>,
)

enum class InsightsPeriod(val label: String) {
    Daily("Daily"),
    Weekly("Weekly"),
    Monthly("Monthly"),
}

enum class InsightCardStyle {
    Primary,
    Secondary,
}

data class InsightCard(
    val title: String,
    val message: String,
    val style: InsightCardStyle,
)

data class TrendBar(
    val label: String,
    val value: Float,
    val highlighted: Boolean = false,
    val marker: String? = null,
)

data class InsightsContent(
    val title: String,
    val subtitle: String,
    val periodLabel: String,
    val cards: List<InsightCard>,
    val trendTitle: String,
    val trendWindowLabel: String,
    val bars: List<TrendBar>,
)