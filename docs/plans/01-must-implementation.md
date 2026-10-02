# Plan 1 — Must recommendations

Status: implementation authorized by the user and delivered in the working tree on 30 September 2026. M1–M6 are implemented for Android and the new iOS host. Automated and simulator validation is recorded in `artifacts/MUST_VALIDATION.md`; physical-device, assistive-technology, and independent clinical/regulatory release checks remain open.

## Outcome and boundaries

Make logging dependable, accessible and recoverable while preserving the four existing tabs, local storage and calm design. Do not add medical recommendations, accounts, network services or analytics. Additional tracking remains optional.

The other plans are [Should](02-should-implementation.md) and [Could](03-could-implementation.md). They are successive priority groups, not competing designs. Selected features can ship independently once their stated dependencies are ready.

## Original starting point (before implementation)

- `shared/src/commonMain/kotlin/com/adhs/logbook/shared/Models.kt` contains preset-bound models, validation, heuristic curves and CSV escaping.
- `app/src/main/java/com/adhs/logbook/Store.kt` uses Android SQLite, schema version 1, with an empty `onUpgrade`. Introduce real migrations before changing data structures.
- `LogbookViewModel.kt` handles UI writes; its busy flag does not provide durable deduplication across notification/widget entry points.
- `MainActivity.kt` already supports quick logging, Undo, editing, collapsed mood input and 7/30-day export shortcuts. Reuse these.
- `Reminders.kt` provides generic inexact reminders and reboot/time-change rescheduling. Reminders are not linked to a medication.
- `Reports.kt` renders Android PDF/CSV. Cloud backup is explicitly disabled in the manifest.
- The KMP module has JVM and optional iOS framework targets. There is no iOS application, storage adapter or UI in this checkout. Existing Android validation is documented in `artifacts/VALIDATION.md`; this planning task did not rerun it.

## Platform delivery dependency

Implement shared domain rules and Android integration first. Keep native storage, notification scheduling, file selection and locale formatting behind small interfaces; use `expect`/`actual` only where useful. Do not replace SQLite or migrate the UI framework solely for these features.

For iOS delivery, first locate any existing iOS host outside this checkout. If none exists, the selected cross-platform scope needs an iOS host, local persistence with migrations, baseline screens, framework integration and native adapters. Track this as a separate prerequisite, not as completed KMP support. An Android-only milestone must be labelled accordingly; full cross-platform acceptance requires real iOS integration and device validation.

## M1 — Custom medication and durable data foundations

Effort: medium. Shared logic plus native persistence.

1. Separate medication identity from `Preset`: name, formulation, optional strength/unit, usual logging dose/unit and optional supported model reference.
2. Preserve built-in presets as setup shortcuts. Add a local “Custom medication” path without assigning a curve. Keep strength, dose and package units distinct; never infer a dosage or unit conversion.
3. Store historical medication snapshots on dose entries. Later medication edits must not rewrite prior names, strengths, formulations or amounts.
4. Add stable IDs and a durable action identifier for externally triggered writes. Use additive, transactional migrations from the existing database; preserve IDs and all existing records.
5. Introduce a shared logging use case and repository contract so UI and future system actions follow the same rules. Refresh foreground state after background writes.

Acceptance: upgrade populated v1 data without loss; custom entries save/edit/export/restore correctly; unsupported medications remain loggable with no curve; historical records survive medication editing and archival.

## M2 — Last logged dose and robust immediate logging

Effort: low for display, medium for write integrity. Depends on M1 for durable action IDs.

1. Place the selected medication's last logged amount, absolute timestamp and elapsed time above the chart. Clearly identify the medication; show the date for older entries.
2. Use “Last logged” and “No dose logged today”; never infer ingestion or a missed dose from the log.
3. Preserve the current quick action, prefilled editor and Undo. Make the configured medication and amount clear before a one-tap save.
4. Commit one entry per action ID even when an event is delivered twice or two handlers race. Separate deliberate actions may create separate entries; no time-window rule should silently discard valid doses.
5. Undo only the referenced entry. Keep History editing available after the snackbar expires, and handle save failures without showing success.
6. Use an injectable clock for timestamp-dependent logic. Update elapsed display on resume and at a modest interval; respect local 12/24-hour display preferences.

Acceptance: repeated event delivery produces one entry; two deliberate logs are retained; process recreation and background writes keep the visible last-log state accurate; Undo cannot remove a different record.

## M3 — Actionable, gentle reminders

Effort: medium, platform-specific. Depends on M1/M2.

1. Allow an explicit medication/default-dose association. Migrate existing generic reminders as generic; do not guess which medication they concern.
2. Add log-now, snooze and open actions. Generic reminders open the editor instead of silently logging a medication. A delayed action records the actual action time, with retrospective editing available.
3. Represent each occurrence durably: reminder ID, scheduled instant, state, snooze/follow-up state and linked log ID. Repeated actions are idempotent.
4. Offer one optional follow-up and an explicit cutoff. Cancel pending follow-ups when that occurrence is resolved. Do not arbitrarily associate unrelated logs; ambiguous matches require an explicit link or remain unresolved.
5. Preserve private notification previews and inexact delivery semantics. Expired actions open the app without writing; archived medications invalidate their actions. Dismissing a notification is not a dose record.
6. Reconcile pending alarms after edits, reboot, time-zone changes, permission changes and restore. Local schedules and historical instants must remain distinct.
7. Android: extend the scheduler/receiver and notification actions. iOS: implement local UserNotifications scheduling and action handlers after the host prerequisite. Keep policy in shared code and delivery native.

Acceptance: validate permission denial, repeated taps, late actions, snooze/cutoff, cancellation after logging, daylight-saving transitions and reboot on supported devices. UI accurately states that OS settings can delay reminders.

## M4 — Manual backup and restore

Effort: medium, with platform file adapters. Depends on M1 schema.

1. Add Settings → Data → Create backup / Restore backup, with last successful backup date. PDF/CSV stay reports, not restore formats.
2. Define a versioned portable document containing medications, historical snapshots, logs, user preferences and reminder definitions. Do not restore platform permission grants or pending OS action tokens.
3. Use `kotlinx.serialization` JSON for the logical payload. Add a vetted, versioned authenticated-encryption envelope with passphrase-derived keys; document lost-passphrase behaviour. Do not use a device-only key for portable backups or implement cryptographic primitives.
4. Export through native document selection. Explain that the user controls the destination, including any cloud-backed provider. Retain application cloud-backup exclusions and exclude temporary plaintext files.
5. Fully validate size, format version, relationships and values before any mutation. Preview date range/counts and state clearly that restore replaces the current log. Confirmation belongs to the future app flow.
6. Restore atomically with rollback on error and a recoverable local pre-restore snapshot. Recreate reminders from definitions only, discard stale actions and refresh all surfaces. Keep reminders off until the user confirms their restored settings.
7. Extend the format and round-trip tests whenever later plans add stored entities. Use Android document APIs and the iOS document picker behind file adapters.

Acceptance: same-device and replacement-device round trips preserve values and Unicode; wrong passphrases, truncated/future-version files and interrupted imports leave current data intact; no unintentional cleartext health-data caches remain.

## M5 — Clarify and restrict the exposure model

Effort: medium engineering; clinical review is a separate dependency.

1. Retain the existing logging-only Atomoxetine behaviour, per-medication views, relative units and limitations in `MODEL_NOTES.md`.
2. Decouple eligibility from the broad medication label. Enable a model only for an explicitly supported formulation; ambiguous ER or custom entries show “Estimate unavailable.” Preserve historical formulation/model provenance.
3. Present last-log information first. Label phase text as model behaviour, for example “Estimate decreasing,” avoiding language that asserts experienced benefit.
4. Expose source, model version, independent scaling and limitations through the existing model-info affordance. Align onboarding, Home and PDF language.
5. Separate substances and unsupported formulations throughout calculation and export. Never add therapeutic targets or imply safe timing from the curve.
6. Verify calculation behaviour without describing mathematical tests as clinical validation. Record independent clinical/regulatory review as an unresolved release dependency; if support cannot be established, retain logging and suppress the affected estimate.

Acceptance: unsupported entries never fabricate curves; no measured-concentration/efficacy claims; existing carryover and separation tests continue to pass; charts and PDFs share the same eligibility rules.

## M6 — Accessibility, localisation and regional formats

Effort: medium; shared semantics/resources where practical, native platform behaviour.

1. Extract hard-coded user-facing text from UI, reminders, domain status strings and reports. Return status keys from shared logic, not English UI sentences. Supply German and English resources, including plurals and errors.
2. Respect system language and 12/24-hour preference. Support locale-aware decimal input; preserve canonical numeric values and unambiguous timestamps in data exchange.
3. Add system-following dark mode, contrast-checked theme tokens and scalable layouts. Replace hard-coded white surfaces where necessary.
4. Review TalkBack/VoiceOver names, focus order, chart alternatives, touch targets, keyboard access and large text. Use short labels with ambiguous icons.
5. Respect reduced motion. Any confirmation haptic is optional and never the only success signal.
6. Keep onboarding brief; request permissions at the relevant feature. Preserve neutral empty states and optional fields.

Acceptance: complete logging, history editing and export at large text size in both languages; no clipped primary controls; dark-mode contrast and screen-reader flow are checked; PDF labels, decimal formatting and timestamps remain correct.

## Execution order and completion checks

1. M1 migrations/domain contracts → M2 logging → M3 reminders.
2. M4 portable backup → M5 model presentation/eligibility → M6 full accessibility/localisation pass. Extract new strings as each feature is built.
3. Extend `ModelTest.kt`, `LoggingFlowTest.kt` and `StorageAndReportTest.kt` for meaningful behavioural cases; add migration/reminder/restore tests where needed.
4. Run `./gradlew :shared:jvmTest :app:assembleDebug :app:lintDebug`, then selected-device instrumentation. Validate changed app/PDF layouts. Test supported Android version boundaries rather than relying on one emulator.
5. For iOS scope, build/link the shared framework with `-PenableIos=true`, run host tests and verify native adapters on supported iOS devices. Framework compilation alone is not feature validation.
6. Update README, design/model notes and validation evidence. Mark each feature/platform separately complete, incomplete or externally blocked. The implementation and test evidence are recorded below.


## Delivery record

| Scope | Android | iOS |
| --- | --- | --- |
| M1 | SQLite v1→v2 migration, custom definitions, historical snapshots, durable action table | New SwiftUI host, atomic private persistence, shared validation and logging |
| M2 | Last log, regional timestamps, foreground refresh, idempotent writes, Undo | Last log, native regional timestamps, foreground refresh, idempotent writes, Undo |
| M3 | Durable occurrences, medication association, snooze/follow-up/cutoff, boot/time reconciliation | UserNotifications actions, durable occurrences, finite coverage displayed in Settings |
| M4 | Native document picker, authenticated backup, preview, atomic restore and recovery | Native document importer/exporter, interoperable authenticated backup, atomic restore and recovery |
| M5 | Supported model IDs and formulation restrictions in Home/PDF; no Concerta curve | Same shared eligibility/calculation rules in Home/PDF |
| M6 | German/English, dark theme, scaling, visible mood labels, chart semantics | German/English, native dark surfaces/scaling, visible mood labels, chart semantics |

Platform code is implemented; this is not a production-release certification. No physical iOS device/signing identity is available to validate installation and background delivery. Full TalkBack/VoiceOver use, OS-version boundaries, OEM power restrictions, reboot/overnight delivery, and independent clinical/regulatory review remain release acceptance work. Android 17 UI instrumentation is blocked by the installed AndroidX test framework; storage tests run separately.

No Should/Could feature or explicitly excluded feature is included in this delivery.
