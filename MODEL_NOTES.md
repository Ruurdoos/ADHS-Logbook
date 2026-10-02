# Estimated curves

This app plots **relative modeled exposure**, not measured blood concentration or medication effectiveness. The curve is a heuristic visualization and is not clinically validated. It must not guide dosing decisions.

The implementation uses a smooth rise to a typical peak followed by exponential decline at a typical terminal half-life. Dose contributions are added only within the same medication identity and supported historical model. Model IDs are `ritalin-ir-v1` and `vyvanse-capsule-v1`. Vertical scaling is relative within each chart and cannot compare medications or days. The initial rise, peak shape, and dose scaling are engineering simplifications, not a fitted pharmacokinetic model. Concerta-type ER is disabled because the simplified rise does not reproduce its multiphase release. Custom medications and atomoxetine also remain logging-only.

Reference values checked September 28, 2026:

| Preset | Peak used | Terminal half-life used | Source and limitations |
| --- | --- | --- | --- |
| Methylphenidate IR | 2 h | 3.5 h | [Ritalin label](https://dailymed.nlm.nih.gov/dailymed/drugInfo.cfm?setid=c0bf0835-6a2f-4067-a158-8b86c4b0668a) for peak; [Ritalin LA label, IR comparison](https://dailymed.nlm.nih.gov/dailymed/fda/fdaDrugXsl.cfm?setid=effd952d-ac94-47bb-b107-589a4934dcca&type=display) for adult IR half-life. |
| Concerta-type ER | Disabled | Disabled | [Concerta label](https://dailymed.nlm.nih.gov/dailymed/drugInfo.cfm?setid=1a88218c-5b18-4220-8f56-526de1a276cd). Retained as a source reference only; the earlier approximate curve has been removed. |
| Elvanse / Vyvanse | 3.8 h | 10.7 h | [Vyvanse label](https://dailymed.nlm.nih.gov/dailymed/drugInfo.cfm?setid=704e4378-ca83-445c-8b45-3cfa51c1ecad). Typical adult capsule dextroamphetamine peak; half-life midpoint from adult range 10–11.3 h. |
| Atomoxetine / custom medication | Unavailable | Unavailable | Logging supported; no short-term curve or phase is shown. |

Food, formulation, individual metabolism, age, organ function, and other medicines can change exposure. No patient-specific adjustment is implemented. Prior logged doses are included rather than resetting the curve at midnight. Gaps in logs do not establish that medication was not taken.

Before any clinical release, obtain independent clinical review, validate the model and wording for each exact formulation and population, and assess applicable regulatory requirements. Model version: `relative-heuristic-v1`.


## Must implementation changes — September 30, 2026

Medication setup chooses an explicit supported preset or a custom logging-only definition. Historical entries carry their own model ID, formulation, strength and unit. New names or amounts do not reinterpret previous entries. Changing a medication to an unsupported formulation suppresses its current Home curve; report curves use the historical snapshot. No unit conversion is inferred.

Home places last-log information before the estimate. Phase labels describe the estimate (for example, “Estimate decreasing”), not experienced benefit. Both native PDF writers call shared curve calculations and apply the same eligibility/separation rules. Reports retain independent scaling and explicitly identify empty days as missing records rather than confirmed non-use.

The engine remains `relative-heuristic-v1`. Source label references are provenance for engineering assumptions, not independent evidence that this model predicts an individual's response. Clinical validation is still unresolved; no “best time/dose,” therapeutic target, or crash alert is implemented.
