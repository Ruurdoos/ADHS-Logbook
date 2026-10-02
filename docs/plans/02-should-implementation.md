# Plan 2 — Should recommendations

Status: implemented in Android and the native iOS host. All six Should recommendations are included. Validation and device-only limits are recorded in [Should validation](../../artifacts/SHOULD_VALIDATION.md).

## Outcome and dependencies

Add optional convenience, appointment preparation and privacy without expanding bottom navigation. Features remain off or hidden until selected. Preserve local storage, neutral language and German/English accessibility from [Plan 1](01-must-implementation.md).

Use Plan 1's migrated medication identities, shared write path, durable action IDs, reminder occurrences and portable backup format. Extend backups for every new entity. Both platform hosts now exist from Plan 1. Native widgets and authentication remain platform-specific; record validation, inventory and report summaries are shared.

## S1 — Privacy lock and external-action policy

Effort: medium, native authentication adapters.

1. Add optional Settings → Data & Privacy → App lock. Use device biometrics with device-credential fallback, avoiding a separate forgotten-app-PIN recovery system.
2. Share lock policy and session state; Android uses AndroidX Biometric and iOS uses LocalAuthentication. Store platform secrets in platform-protected storage, not ordinary preferences.
3. Define relocking after backgrounding. Protect reports, restore, history and deep links as well as the main screen. Obscure sensitive app-switcher previews.
4. When locked, external surfaces expose generic content and open authentication before a write. Existing notification actions must not bypass this policy. Read current policy at execution, not only when rendering the action.
5. Handle biometric changes, unavailable hardware and authentication cancellation. Explain that app locking does not revoke already exported files or by itself encrypt every stored byte.

Acceptance: notification/deep-link/widget entry cannot bypass a locked app; foreground/background transitions protect content; cancellation preserves drafts without saving; device-credential fallback works.

## S2 — Home-screen logging widget

Effort: medium, native widget UI. Depends on Plan 1 M1/M2 and S1 policy.

1. Offer widget setup from Settings after initial medication setup. User chooses a medication; do not infer one from whichever screen happened to be open.
2. Show a clear last-logged timestamp and one logging action. Offer a privacy mode with generic content. Avoid a live chart and stale “minutes ago” text unless the platform can keep it accurate.
3. Revalidate medication, configured amount and lock state when tapped. A stale configuration opens review instead of logging the wrong amount.
4. Reuse durable logging and refresh widget/app state after commit, Undo, edits, archival and restore. Provide a route to correct an accidental log.
5. Android: Jetpack Glance with shared domain commands. iOS: WidgetKit/App Intent extension with an App Group storage strategy and concurrent-write protection. Use a compatible open-app fallback where interactive widgets are unavailable.

Acceptance: duplicate delivery creates one entry; stale/deleted medication is handled; database and all views agree after logging; widget resizing, process death, large text and privacy mode work.

## S3 — Optional timestamped observations

Effort: medium, mostly shared domain work.

1. Add a secondary Home action and a History entry point. Observations can be recorded without a dose; never insert a questionnaire into the immediate-log path.
2. Offer selectable categories: focus/everyday functioning, mood, appetite, sleep, symptoms/suspected side effects and user-noticed benefit/fading times. Start with only the user's chosen category visible.
3. Use short labelled scales or chips, with optional notes. Distinguish explicit “none,” “unsure” and missing responses. Sleep may describe the previous night rather than the entry instant.
4. Store occurrence time, creation time, zone/offset and stable observation type/scale version. Permit retrospective edits. Dose links are optional and do not establish causation.
5. Keep existing dose mood/notes intact. Do not silently convert them into observations or count them twice in summaries.
6. Extend SQLite migrations, shared models, History, export and backup. Use native presentation only where the existing platform UI requires it.

Acceptance: saving one observation needs no dose or unrelated answers; timestamps survive time-zone changes; edits, deletes and restore retain meaning; empty values never become symptom-free observations.

## S4 — Concise doctor-report summary

Effort: medium. Depends on S3 for observation summaries; baseline dose summary can ship first.

1. Extend the existing Export screen and date presets; add a “Summary with details” PDF option rather than a new navigation destination.
2. Prepare a shared report model: selected period/timezone, medication/formulation history, logged doses, user-recorded changes, available observations and explicit data coverage.
3. Put a readable summary first, followed by chronological details. Let users include a short “Questions for my appointment” note and choose whether free-text notes are exported.
4. Show counts and denominators; label results “recorded.” Separate explicit non-use from missing logs. Do not infer prescribed regimens, compliance or medication causation.
5. Reuse Android `Reports.kt`; supply equivalent native PDF rendering/sharing for iOS after its host exists. Avoid mixing separately scaled exposure curves with observation trends.
6. Update CSV with documented, versioned fields/record types while preserving export safety and timestamps. Add report localisation and screen-reader-friendly in-app preview/summary.

Acceptance: test empty/sparse/dense periods, multiple formulations, long German names/notes, multipage layout and daylight-saving boundaries; visually inspect rendered PDFs. Clinician feedback is an external validation task, not something automated tests establish.

## S5 — Supply and prescription reminders

Effort: medium, shared ledger plus existing native reminder adapters.

1. Add optional Supply settings per medication: unit label, current counted stock, low-stock threshold and an independently chosen prescription-request date.
2. Keep prescribed/logged milligrams separate from package units. Ask for an explicit units-used value or user-defined mapping when needed; never infer tablet splitting or dose changes.
3. Use an inventory ledger for initial counts, restocks, count corrections and logged consumption. Changes are transactional and linked to the source entry/action ID.
4. Reconcile dose edits/deletions and Undo exactly once. Never subtract stock for unresolved reminders or “not taken” records. Backdated entries outside the selected inventory counting period must not retroactively corrupt a newer physical count.
5. Label the result “Estimated remaining.” Flag an inconsistent count neutrally and offer recounting; never silently clamp a negative estimate.
6. Notify at the user-chosen threshold/date with deduplication. Do not infer days remaining for irregular schedules or hard-code German prescription lead times. Home shows a compact card only when relevant.

Acceptance: partial units, count corrections, repeated actions, backdating, dose edits, restocks and restore preserve ledger consistency; reminders stop/update after replenishment; no dose suggestion is generated.

## S6 — Pause reminders and explicitly record non-use

Effort: low–medium, shared state and native rescheduling. Depends on Plan 1 M3.

1. Keep two separate actions: “Pause reminders” and recording that medication was not taken. Pausing reminders makes no statement about treatment.
2. Offer manual pause/resume, optionally with a user-selected end date, in reminder settings. Do not recommend medication-free days.
3. Add explicit “Not taken” and optional user-recorded non-use periods through History/reminder detail. Preserve unrecorded days as unknown.
4. Store non-use separately from dose entries; it has no dose amount and creates no curve or stock deduction.
5. Cancel snoozes/follow-ups during the pause. Resuming schedules future occurrences only, with no overdue backlog. Permit corrections and avoid ambiguous overlapping statuses for the same occurrence.
6. Include explicit records in History, reports and backups using neutral styling and no adherence score.

Acceptance: pausing cannot fabricate non-use; resumption creates no notification burst; edits resolve occurrence state consistently; inventory and estimates include actual dose logs only.

## Execution order and completion checks

1. Implement S1 privacy policy before S2 external logging.
2. Implement S3 observations → S4 report summary.
3. Implement S6 occurrence states → S5 supply reconciliation, then run combined flows. Each feature can ship separately if its dependencies are satisfied.
4. Run shared tests, Android build/lint and selected-device instrumentation using Plan 1's commands. Focus tests on integrity, privacy bypasses, reminder state and restore round trips.
5. Check widget/app/PDF layouts in both languages, dark mode and large text. Verify disabled features add no Home clutter.
6. Run equivalent host/device checks for the selected iOS scope. Update backup documentation, README and validation records with actual results and platform limitations.
