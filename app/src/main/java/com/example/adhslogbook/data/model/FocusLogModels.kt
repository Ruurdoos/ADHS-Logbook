package com.example.adhslogbook.data.model

enum class MetricType { Focus, Mood, Energy }

data class CheckInMetric(
    val type: MetricType,
    val value: Float,
    val descriptor: String,
)

data class TagOption(val label: String, val selected: Boolean = false)

enum class MedicationStatus { Taken, Due }

data class MedicationSummary(
    val name: String,
    val dosage: String,
    val status: MedicationStatus,
)

data class TodayContent(
    val medication: MedicationSummary,
    val lastTaken: String,
    val metrics: List<CheckInMetric>,
    val tags: List<TagOption>,
    val notes: String,
)

enum class InsightCardStyle { Primary }

data class InsightCard(
    val title: String,
    val message: String,
    val style: InsightCardStyle,
)

data class TrendBar(
    val label: String,
    val value: Float,
    val highlighted: Boolean = false,
)

data class InsightsContent(
    val cards: List<InsightCard>,
    val bars: List<TrendBar>,
)
