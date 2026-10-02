# Plan 3 — Could recommendations

Status: implemented on Android and iOS on 1 October 2026, following explicit user authorization. C1–C4 are included in version 0.4.0. Automated and emulator/simulator evidence, plus remaining physical-device checks, are recorded in [Could validation](../../artifacts/COULD_VALIDATION.md).

## Outcome and dependencies

Provide optional faster entry points and simple descriptive records after the core is reliable. All four retained recommendations are optional and disabled by default. Preserve four tabs, local-only operation and neutral wording.

Use [Plan 1](01-must-implementation.md) for durable logging, custom medication identity, locale handling, reminders and backup. Use [Plan 2](02-should-implementation.md) for lock policy, widget adapters, observations and report data. If a dependency is not selected, either implement its minimum shared contract within the approved feature or leave the dependent feature pending; do not silently expand scope.

The native iOS host and shared persistence contracts were delivered in Plans 1 and 2.

## C1 — Android App Shortcuts and iOS App Intents

Effort: medium, native entry points around shared commands.

1. Add a generic “Log dose” shortcut that opens the existing prefilled editor. Offer a medication-specific shortcut only through explicit user setup.
2. Default to opening review; any immediate-save variant must identify the configured medication and amount and reuse the durable action-ID path. Merely opening a URL must not create a dose.
3. Keep health details out of public shortcut labels by default. Validate lock state, medication activity and configuration revision when executing; stale shortcuts open the editor or explain unavailability.
4. Android: use launcher/pinned shortcuts and validated app navigation. iOS: expose explicit tap-triggered Shortcuts actions through App Intents after host integration; no additional assistant workflow is in scope.
5. Reconcile shortcut state on medication changes, archival and restore. Carry only an opaque identifier, never trust a supplied dose from an external link.

Acceptance: shortcut from a cold process reaches the intended flow; invalid input cannot write arbitrary records; locked-app and stale-configuration paths are safe; deliberate repeated logs remain distinct from duplicate event delivery.

## C2 — Android Quick Settings tile and iOS Lock Screen widget

Effort: medium, native surface work. Reuses C1 routing and S2 widget architecture.

1. Add explicit setup under Settings → Quick access; no automatic installation prompts during onboarding.
2. Android tile opens the logging flow. Default to a generic label; never show a permanent “Taken” state for a medication that may have several doses per day.
3. iOS Lock Screen widget uses a compact generic logging affordance. Any last-log display is explicit opt-in and indicates its timestamp without implying live accuracy.
4. Enforce authentication policy for both views and writes. A dismissed unlock request must leave data untouched. Respect OS capability differences with an open-app fallback.
5. Reuse shared commands and the existing notification/widget invalidation path; do not create another source of truth or continuously refresh the exposure graph.

Acceptance: lock-screen privacy, cold starts, stale state, medication removal, restore and supported OS variants behave consistently; no health data appears before unlock by default.

## C3 — Optional manual blood pressure, pulse and weight

Effort: medium, shared measurement models with native input/accessibility.

1. Place Measurements inside optional observation settings and the existing observation form. Keep it absent from the default Home screen.
2. Define typed records: systolic/diastolic pressure with mmHg, pulse with bpm, and weight with an explicit supported unit. Do not use a generic unlabelled number field.
3. Capture measurement time separately from entry time, timezone and optional note. Support editing, deletion and backdating; distinguish a missing measurement from zero.
4. Validate numeric syntax, finite values and unit consistency. Do not add normal/abnormal bands, interpretation, treatment instructions or automatically prescribed measurement schedules.
5. Reuse S3's repository and history structure where appropriate, while retaining measurement-specific types. Add raw values and units to S4 reports and versioned backups.
6. Handle locale decimals and unit conversion explicitly; retain original entered value/unit when converting for display.

Acceptance: unit round trips are stable; pressure components cannot be swapped by an ambiguous form; invalid numeric input cannot corrupt data; exports preserve original values, units and timestamps; no clinical interpretation is displayed.

## C4 — Descriptive weekly overview

Effort: medium, shared aggregation and existing screen presentation. Depends on observation/report models and explicit non-use semantics where selected.

1. Add an optional compact section in History with a chosen calendar week; no new tab and no automatic summary notification.
2. Show logged-dose timelines, recorded observation distributions, notes and data coverage. Describe recorded information, not success or failure.
3. Separate unknown days from explicit non-use. No completion percentages, streaks, medication rankings or automatically attributed causes.
4. Keep ordinal observations as counts/distributions unless the scale definition supports another meaningful summary. Show denominators and distinguish legacy dose moods from standalone observations.
5. Keep medications/formulations and units separate. Use simple tabular/text alternatives to charts and a neutral empty state for sparse records.
6. Reuse the shared report aggregation layer and locale/time-zone rules so the weekly view and exported report agree. Keep analysis entirely on-device.

Acceptance: sparse and irregular logging cannot imply improvement, deterioration or non-adherence; midnight/daylight-saving boundaries group records correctly; multiple medications remain distinct; displayed counts reconcile with underlying records and the export.

## Execution order and completion checks

1. C1 validated entry points → C2 optional surfaces.
2. C3 typed measurements → C4 overview, reusing selected Should infrastructure.
3. Run shared tests, Android assembly/lint and targeted device instrumentation from Plan 1. Test external-input validation, privacy, unit conversion, aggregation boundaries and backup round trips.
4. Verify Quick Settings/Lock Screen behaviour on supported physical devices where possible; record platform restrictions and untested combinations.
5. Check German/English, large text, screen-reader labels and dark mode. Ensure every optional feature can be disabled without losing history.
6. Update README and validation evidence only after implementation. Treat iOS intent/widget delivery as incomplete until the host and native integration are built and tested.

## Delivered implementation

- C1: generic and explicitly configured review shortcuts, opaque identifiers, current lock/activity/revision checks, configuration reset on restore, generic public labels, localized iOS intent metadata. No immediate-save variant was added.
- C2: opt-in Android tile setup and WidgetKit accessory circular/rectangular families; generic default, optional absolute last-log time on iOS, hidden whenever app lock is enabled.
- C3: typed measurement storage, Android schema-4 migration, Swift persistence, optional forms/history, display conversion, schema-3 backups and CSV/PDF records.
- C4: History navigation to a chosen-week overview with shared coverage/counts, daily details and notes, separate ordinal distributions and legacy moods. No extra tab or automatic notification.

Device-only authentication and platform surface coverage remain release acceptance checks; implementation completion does not imply those checks have passed.
