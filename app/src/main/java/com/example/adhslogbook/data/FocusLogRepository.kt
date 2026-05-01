package com.example.adhslogbook.data

import com.example.adhslogbook.data.model.CheckInMetric
import com.example.adhslogbook.data.model.CurvePoint
import com.example.adhslogbook.data.model.InsightCard
import com.example.adhslogbook.data.model.InsightCardStyle
import com.example.adhslogbook.data.model.InsightsContent
import com.example.adhslogbook.data.model.InsightsPeriod
import com.example.adhslogbook.data.model.MedicationStatus
import com.example.adhslogbook.data.model.MedicationSummary
import com.example.adhslogbook.data.model.MetricType
import com.example.adhslogbook.data.model.SideEffectItem
import com.example.adhslogbook.data.model.TagOption
import com.example.adhslogbook.data.model.TodayContent
import com.example.adhslogbook.data.model.TimelineDay
import com.example.adhslogbook.data.model.TimelineEvent
import com.example.adhslogbook.data.model.TimelineEventType
import com.example.adhslogbook.data.model.TrendBar

object FocusLogRepository {
    fun loadToday(): TodayContent = TodayContent(
        dateLabel = "Oct 24, Thursday",
        medication = MedicationSummary(
            name = "Elvanse",
            dosage = "30mg • Extended Release",
            status = MedicationStatus.Taken,
        ),
        lastTaken = "08:30 AM",
        estimatedDuration = "10-12 Hours",
        nowMarker = 0.6f,
        effectCurve = listOf(
            CurvePoint(0f, 1f),
            CurvePoint(0.12f, 0.96f),
            CurvePoint(0.24f, 0.6f),
            CurvePoint(0.36f, 0.28f),
            CurvePoint(0.5f, 0.18f),
            CurvePoint(0.68f, 0.22f),
            CurvePoint(0.84f, 0.6f),
            CurvePoint(1f, 1f),
        ),
        metrics = listOf(
            CheckInMetric(MetricType.Focus, 0.75f, "Good"),
            CheckInMetric(MetricType.Mood, 0.5f, "Neutral"),
            CheckInMetric(MetricType.Energy, 0.8f, "High"),
        ),
        tags = listOf(
            TagOption("Peak"),
            TagOption("Good focus", selected = true),
            TagOption("Calm"),
            TagOption("Restless"),
            TagOption("Rebound"),
            TagOption("No effect"),
            TagOption("Can't concentrate"),
        ),
        notes = "",
    )

    fun loadTimelineDays(): List<TimelineDay> = listOf(
        TimelineDay(
            dateLabel = "Yesterday, Oct 23",
            subtitle = "Elvanse 30mg",
            actualCurve = defaultActualCurve(),
            expectedCurve = defaultExpectedCurve(),
            events = listOf(
                TimelineEvent(
                    id = "y-dose",
                    time = "08:10 AM",
                    title = "Dose Taken",
                    type = TimelineEventType.Dose,
                ),
                TimelineEvent(
                    id = "y-focus",
                    time = "10:00 AM",
                    title = "Focus Block",
                    description = "Best concentration happened after the first protein-heavy breakfast.",
                    type = TimelineEventType.Focus,
                ),
            ),
            sideEffects = listOf(
                SideEffectItem("Dry mouth"),
                SideEffectItem("Skipped lunch", highlighted = true),
            ),
        ),
        TimelineDay(
            dateLabel = "Today, Oct 24",
            subtitle = "Elvanse 30mg",
            actualCurve = defaultActualCurve(),
            expectedCurve = defaultExpectedCurve(),
            events = listOf(
                TimelineEvent(
                    id = "dose",
                    time = "08:00 AM",
                    title = "Dose Taken",
                    type = TimelineEventType.Dose,
                ),
                TimelineEvent(
                    id = "peak",
                    time = "09:30 AM",
                    title = "Peak Effect Starts",
                    description = "Felt highly focused during morning meeting.",
                    type = TimelineEventType.Focus,
                ),
            ),
            sideEffects = listOf(
                SideEffectItem("Appetite loss", highlighted = true),
                SideEffectItem("Mild headache"),
            ),
        ),
    )

    fun loadInsights(period: InsightsPeriod): InsightsContent {
        return when (period) {
            InsightsPeriod.Daily -> InsightsContent(
                title = "Daily Insights",
                subtitle = "Today shows a high-focus window around late morning.",
                periodLabel = period.label,
                cards = listOf(
                    InsightCard(
                        title = "Key Observation",
                        message = "Focus peaked about 90 minutes after the first dose.",
                        style = InsightCardStyle.Primary,
                    ),
                    InsightCard(
                        title = "Pattern Detected",
                        message = "Late caffeine pushed the afternoon drop by roughly 30 minutes.",
                        style = InsightCardStyle.Secondary,
                    ),
                ),
                trendTitle = "Focus & Energy Trends",
                trendWindowLabel = "Today",
                bars = listOf(
                    TrendBar("8a", 0.35f),
                    TrendBar("10a", 0.82f, highlighted = true, marker = "Peak"),
                    TrendBar("12p", 0.74f),
                    TrendBar("2p", 0.52f),
                    TrendBar("4p", 0.28f),
                    TrendBar("6p", 0.18f),
                ),
            )

            InsightsPeriod.Weekly -> InsightsContent(
                title = "Weekly Insights",
                subtitle = "Your patterns for the past 7 days",
                periodLabel = period.label,
                cards = listOf(
                    InsightCard(
                        title = "Key Observation",
                        message = "Best focus usually starts 90m after first dose.",
                        style = InsightCardStyle.Primary,
                    ),
                    InsightCard(
                        title = "Pattern Detected",
                        message = "Taking medication with a high-protein breakfast reduces afternoon crash by 40%.",
                        style = InsightCardStyle.Secondary,
                    ),
                ),
                trendTitle = "Focus & Energy Trends",
                trendWindowLabel = "Last 7 Days",
                bars = listOf(
                    TrendBar("Mon", 0.40f),
                    TrendBar("Tue", 0.70f),
                    TrendBar("Wed", 0.85f, highlighted = true, marker = "Peak"),
                    TrendBar("Thu", 0.60f),
                    TrendBar("Fri", 0.45f),
                    TrendBar("Sat", 0.20f),
                    TrendBar("Sun", 0.15f),
                ),
            )

            InsightsPeriod.Monthly -> InsightsContent(
                title = "Monthly Insights",
                subtitle = "The strongest weeks align with sleep consistency and breakfast routine.",
                periodLabel = period.label,
                cards = listOf(
                    InsightCard(
                        title = "Key Observation",
                        message = "Stable wake-up times were the clearest predictor of good medication response.",
                        style = InsightCardStyle.Primary,
                    ),
                    InsightCard(
                        title = "Pattern Detected",
                        message = "Late lunches increased rebound symptoms in 3 out of 4 lower-focus weeks.",
                        style = InsightCardStyle.Secondary,
                    ),
                ),
                trendTitle = "Focus & Energy Trends",
                trendWindowLabel = "Last 4 Weeks",
                bars = listOf(
                    TrendBar("W1", 0.58f),
                    TrendBar("W2", 0.76f, highlighted = true, marker = "Best"),
                    TrendBar("W3", 0.62f),
                    TrendBar("W4", 0.49f),
                ),
            )
        }
    }

    private fun defaultActualCurve(): List<CurvePoint> = listOf(
        CurvePoint(0f, 1f),
        CurvePoint(0.12f, 0.94f),
        CurvePoint(0.24f, 0.50f),
        CurvePoint(0.38f, 0.14f),
        CurvePoint(0.52f, 0.18f),
        CurvePoint(0.68f, 0.36f),
        CurvePoint(0.84f, 0.82f),
        CurvePoint(1f, 1f),
    )

    private fun defaultExpectedCurve(): List<CurvePoint> = listOf(
        CurvePoint(0f, 1f),
        CurvePoint(0.16f, 0.88f),
        CurvePoint(0.3f, 0.46f),
        CurvePoint(0.44f, 0.18f),
        CurvePoint(0.58f, 0.22f),
        CurvePoint(0.72f, 0.44f),
        CurvePoint(0.88f, 0.86f),
        CurvePoint(1f, 1f),
    )
}
