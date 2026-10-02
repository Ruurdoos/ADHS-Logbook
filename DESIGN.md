# ADHS Logbook — design handoff

## Deliverable

Android-first mobile UI design for a Kotlin Multiplatform app with a native SwiftUI iOS counterpart. The repository now also contains a working Android and iOS implementations; see README.md for build instructions and the platform boundary. The screen board uses illustrative data, not a validated pharmacokinetic model.

Local screen board: [screens.html](screens.html). Open it in a browser to review nine views, including the six core areas, entry editing, and a report layout. Navigation, optional mood expansion, and quick-log Undo are demonstration interactions. This board does not persist data or generate reports.

Remote design workspace: https://stitch.withgoogle.com/projects/13742950276748215024 . The design system was created, but screen generation timed out and returned no screens when checked. The local board is the delivered visual reference; no Figma file was created.

## Visual system

| Token | Value |
| --- | --- |
| Background | `#F7F8F4` |
| Surface | `#FFFFFF` |
| Primary text | `#24352D` |
| Secondary text | `#5D6B63` |
| Primary action / graph | `#416B59` |
| Selected surface | `#E8EFE6` |
| Font | Inter; platform fallback sans-serif |
| Title | 28sp, semibold |
| Body | 16sp, regular, 24sp line height |
| Label | 14sp, medium |
| Screen padding | 24dp |
| Spacing scale | 4, 8, 12, 16, 24, 32dp |
| Card radius | 12dp |
| Minimum touch target | 48 × 48dp |
| Primary action height | At least 56dp; expands with text |

Use outline icons with visible labels. Avoid decorative graphics, gradients, gamification, or status communicated through color alone. Preserve readable text contrast despite the muted palette. Use scrolling layouts at large font sizes; do not fix content to a device height.

## Screen flow

Welcome → Medication setup → Home. Main navigation: Home, History, Export, Settings. Entry and edit forms are sheets or pushed destinations; they do not add navigation tabs.

### Welcome and setup

Welcome title: “A little clarity, every day.” Body: “Log your medication and see an estimate of how levels change. Keep notes when you want to.” Footer: “Private. Stored on this device.” Action: “Get started”.

Setup offers Methylphenidate IR, Methylphenidate ER / Concerta-type, Elvanse / Vyvanse, and Atomoxetine / Strattera. A numeric dose field carries a persistent mg suffix and accepts decimal input according to locale. A stepper supplements direct entry. No dose is recommended by the design. Show “You can add another medication later.” Finish with “Start”.

### Home

Show Today, a date, a large “Estimated level” chart, “Last 24 hours”, and a qualitative state such as “Estimate decreasing”. Mark the current moment with a line, dot, and text label. Include dose markers and concise accessible descriptions. Display “Estimate, not a medical measurement.” Demo screens explicitly identify illustrative data.

Combine contributions from logged doses of the same medication. When there are multiple medications, provide a compact medication selector and separate graph views. Do not add concentrations across different active ingredients. Relative chart values must not imply measured blood units, a therapeutic target, symptom control, or readiness to take another dose.

The primary “Log dose” opens the prefilled entry form. A secondary “Log now · [usual dose] [unit]” action saves immediately and shows “Dose logged” with Undo. This resolves the brief's two entry paths: review/edit and immediate logging. Prevent repeated submission while a save is pending. Undo reverses only that specific entry.

Empty state: “Your first dose starts the picture.” Action: “Log dose”. Never show fabricated patient history on a fresh installation.

### Log dose and edit entry

Prefill the selected medication, configured dose, and the current local time. Time and date remain editable for retrospective entries. Capture the actual timestamp at save for the “Now” mode. Require a positive finite dose and a medication; show errors beside the relevant field. Do not silently clamp a dose or suggest a replacement dose.

“How are you feeling? (optional)” starts collapsed. Expanded state has five labeled faces: Very low, Low, Okay, Good, Very good. Nothing is selected by default. Notes are optional. “Save dose” works without changing either optional field. Keep it above the keyboard and bottom safe area.

History opens the same form in edit mode, with “Save changes” and “Delete entry”. Confirm deletion with clear language or offer a reliable Undo. Medication edits in Settings must not rewrite old logged doses or names.

### History

Group entries by local calendar day, most recent first. Each row shows medication, mg, time, and a mood icon when recorded. A calendar action permits jumping to a date; scrolling remains available. Notes appear in the detail form rather than expanding every row. Empty date: “No doses logged for this day.”

### Export

Choose a start date, end date, and PDF report or CSV. Optional shortcuts: Last 7 days and Last 30 days. Primary action: “Export report”. Disable export with a clear inline explanation for an invalid range or no entries. On success, open the native share sheet. Cancellation preserves the selected range.

The PDF contains the selected date range, timezone, generation date, clearly labeled estimated graphs for each medication, and a chronological table of medication, dose, timestamp, mood, and notes. Repeat table headers across pages, wrap notes, and avoid splitting dose rows. Explicitly show days without entries rather than implying confirmed missed medication. Include no clinical recommendations.

CSV columns: entry_id, medication, formulation, dose_mg, timestamp_iso8601, timezone, mood, notes. Use UTF-8, quoted/escaped fields, and a documented spreadsheet-safe treatment for text that could be interpreted as a formula. Preserve full timestamps and UTC offsets. Include only the requested range.

### Settings

Medication management supports editing a usual dose, adding another preset, and removing a medication from future use while retaining historical entries. The medication picker is built in; no network lookup is required.

Reminders provide an on/off switch and one or more times. They ask the user to log; they are not dosing recommendations. Request notification permission when enabling reminders. If denied, retain the times and show a short route to system settings. Hide medication names from notification content by default.

Privacy copy: “Your log stays on this device. No account needed.” Include an Export shortcut. For this claim to hold, application configuration must explicitly exclude health data from platform cloud backups. Exporting is a deliberate exception: the user chooses where a report goes through the system share sheet.

## Kotlin Multiplatform handoff

Share domain models, local repository contracts, validation, export data preparation, and validated model calculations in common code. Use Compose Multiplatform for shared UI and theme tokens where appropriate. No framework versions or build configuration are prescribed by this design.

Suggested concepts: MedicationPreset (stable ID, formulation, model version), UserMedication (preset reference, usual dose), DoseEntry (immutable recorded medication snapshot, dose, instant, zone/offset, optional mood and notes), Reminder, and ExportRange.

Platform adapters handle local database location/protection, notifications, date/time controls, PDF rendering, temporary export files, and sharing. Android uses a content URI with temporary read permission for the share sheet. iOS uses its native activity sheet. Remove temporary report files when no longer needed without breaking active sharing.

Android uses system Back and edge-to-edge insets; iOS respects safe areas, native back gestures, and keyboard behavior. Both support screen readers and text scaling. Provide a textual chart summary and accessible dose list so the graph is not the only source of information.

## Model boundary

Preset names in the design do not constitute validated pharmacokinetic profiles. Before implementation, source and review formulation-specific model parameters and their limitations. Store sources and model versions. Do not infer interchangeability from a shared ingredient or use a single release model for all extended-release products. The interface must support “Estimate unavailable” instead of inventing a curve when a suitable model is absent.

## Acceptance checks

- All six core areas are reachable without an account or network access.
- A user can log the usual dose now with one action and Undo it.
- A user can open the prefilled form and save without optional input.
- Empty, populated, edited, deleted, and export-cancelled states remain clear.
- Large text, screen readers, keyboard display, and narrow screens preserve access to primary actions.
- Date grouping and exported timestamps remain correct across timezone and daylight-saving changes.
- A second medication never produces an invalid mixed concentration graph.
- Medication removal preserves historical records and reports.
- Reminders remain clearly distinguished from instructions to take medication.
- PDF tables remain readable with long medication names and notes.

These are implementation acceptance criteria; they have not been tested against a running native app.


## Implemented Must scope — September 30, 2026

The native apps extend this initial visual reference with custom medication definitions, immutable history snapshots, last-log information above the chart, actionable reminders, encrypted backup/restore, German/English localization and system dark mode. Custom dose units are explicit; strength never implies a conversion. Concerta-type ER, atomoxetine and custom definitions have no curve.

Dark tokens: background `#121713`, surface `#1B231D`, primary text `#E3EAE1`, secondary text `#BFCBBC`, primary `#A8D5B7`, selected surface `#294B37`. Native iOS uses adaptive system surfaces and an adaptive sage accent. The apps use platform fonts and native scaling rather than a font selector. Mood labels remain visible when expanded; custom medication names and notes are not translated.

The implementation plans and validation record take precedence over illustrative copy in the original screen board. Screen-reader and physical-device acceptance checks are tracked in `artifacts/MUST_VALIDATION.md`.
