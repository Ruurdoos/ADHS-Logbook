# Report format, version 4

PDF and CSV are unencrypted exports, not restorable backups. The user selects inclusive local calendar dates; day boundaries respect the export timezone and daylight-saving transitions. An overlapping non-use period retains its original start/end; the PDF repeats the period on each intersecting day. Missing records never imply non-use, adherence or medication causation.

## PDF

“Summary with details” adds recorded dose counts, days with each record type, days without any records, medication snapshot groups and explicit observation response counts. Medication names, formulation, strength and logging units stay separate; totals never mix units. Snapshot groups describe recorded history rather than an inferred prescription. Daily details follow in chronological order. Relative curves remain independently scaled and separate from observations. The accessible in-app summary uses the same shared report model.

“Include free-text notes” controls dose, observation, measurement and non-use notes. Appointment questions are separately entered and included only in the summary PDF. They are not stored in the clinical record or CSV. Long text wraps and paginates. Existing dose mood is neither migrated into observations nor counted twice.

## CSV

UTF-8; quoted RFC-style fields, embedded quotes doubled, CR/LF retained inside quotes. Potential spreadsheet formula prefixes are neutralized by the existing shared CSV encoder. Every row has `schema_version=4`. Columns, in order:

| Field | Meaning |
| --- | --- |
| schema_version | `4` |
| record_type | `dose`, `observation`, `non_use`, or `measurement` |
| record_id | Stable ID within its record type |
| medication_id | Medication identity; blank for independent observations and measurements |
| medication | Historical name for a dose; current identity name for non-use |
| formulation | Dose snapshot formulation |
| amount, unit | Logged dose amount; unit also carries the measurement unit (mmHg, bpm, kg or lb) |
| timestamp_iso8601 | Recorded event instant using its stored offset; period start for non-use |
| end_timestamp_iso8601 | Non-use end instant; blank otherwise |
| timezone | Stored IANA timezone |
| category | `focus`, `mood`, `appetite`, `sleep`, `symptom`, `benefit`, `fading` |
| response | `rated`, `none`, `unsure`, `recorded`; blank outside observations |
| scale_version | `1` for observations |
| value | Explicit 0–4 rating only; blank is not zero or “none” |
| sleep_date | Optional `YYYY-MM-DD`, the night starting on this date |
| linked_dose_id | Optional contextual link; no causal assertion |
| dose_mood | Existing optional dose mood 0–4; separate from observations |
| notes | Free text, or blank when notes are excluded |
| strength, model_id | Dose snapshot metadata |
| stock_units | Explicit package units for a dose; blank when no explicit value was entered |

| measurement_kind | `pressure`, `pulse`, or `weight` |
| measurement_value | Original pulse or weight value; blank for pressure |
| systolic, diastolic | Explicit blood-pressure components, both in mmHg |
| created_at_iso8601 | Measurement entry creation instant in UTC, distinct from its event timestamp |

Measurement values are raw records, without interpretation or inferred units. Measurement days contribute to recorded-data coverage even when no doses or observations exist. The optional weekly overview uses the same shared aggregation with native calendar day boundaries, including daylight-saving changes. Legacy dose moods remain separate from observation distributions.

CSV rows are grouped by record type; consumers can sort by event timestamp. Supply configuration and the full count/restock ledger belong in encrypted backups, not the doctor report. Empty periods export a header-only CSV and an explicitly empty PDF summary. Software tests and visual checks do not replace clinician review of report usefulness.

The final CSV column is `non_use_kind` (`legacy`, `scheduled`, `day`, or `period`), blank for other record types. Observation `scale_version` distinguishes original sleep ratings from quality ratings. PDF details name the non-use type and explain each rating's scale. Legacy inclusive endpoints remain unchanged; new day/period endpoints are exclusive.

Reports now have separate Create and Share steps. A changed date range, format, notes setting, summary setting, appointment question, or underlying record invalidates the prepared report. A completed background request cannot publish a stale result. Backup previews count observations, measurements and non-use records in addition to medications and doses.
