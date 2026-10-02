# Portable backup envelope 1, document schema 3

A backup is an authenticated encrypted document, not a PDF or CSV report. Both native apps implement the same envelope using their platform cryptography libraries. No plaintext staging file is used for backup or restore.

| Bytes | Value |
| --- | --- |
| 0–7 | ASCII `ADHSBK01` |
| 8–23 | Random 16-byte salt |
| 24–35 | Random 12-byte AES-GCM nonce |
| 36 onward | AES-256-GCM ciphertext followed by its 16-byte authentication tag |

The first 36 bytes are authenticated additional data. PBKDF2-HMAC-SHA256 derives a 32-byte key from the UTF-8 passphrase, salt, and 600,000 iterations. Passphrases require at least 10 UTF-16 code units. Passphrases are not normalized, stored, or recoverable. Android uses JCA; iOS uses CommonCrypto and CryptoKit. Randomness comes from the native cryptographic generator. Readers enforce a 32 MiB envelope limit. Changing key derivation or envelope layout requires a new format identifier.

The plaintext is the `BackupDocument` UTF-8 JSON schema from the shared module. Readers accept document schemas 1, 2 and 3. Writers now emit schema 3. Schema 1 includes medication definitions, immutable entry snapshots, reminder definitions, and the allowed local preferences. `doseMg` is retained as the historical field name for compatibility; **`unit` determines the amount's unit**. Never interpret all values as milligrams or infer a conversion. Strength is a separate, optional description.

Schema 2 adds independent `observations` (category, explicit response, optional value, scale version, event/creation timestamps, zone/offset, sleep date, optional dose link and notes), `nonUse` points/periods, reminder `pause`, per-medication `supplies`, and `stock` ledger movements. Dose snapshots may include an explicit `supplyUnits` value. No unit conversion is inferred. Derived consumption is reconciled from current logs and the latest physical count during writes and restore. Imported schema-1 files default to empty new collections and an inactive pause.

Schema 3 adds typed `measurements`: stable ID, `kind` (`pressure`, `pulse`, `weight`), original `value` and `unit`, optional `diastolic`, event and creation timestamps, stored timezone/offset, and notes. Pressure uses `value` for systolic and requires diastolic, both in mmHg; pulse uses bpm; weight uses kg or lb. Older schemas default to an empty collection. Display conversion never rewrites the entered value or unit.

The app-lock setting, authenticated session, widget and quick-access configuration/action tokens, OS notification permissions, alert deduplication state and alert enable switches are device-local and never exported. `observations_enabled`, `measurements_enabled` and `weekly_enabled` are portable display preferences. Restored non-use records may contain an old occurrence identifier; it cannot reactivate a notification.

Imports validate versions, counts, lengths, identifiers, relationships, numeric values, medication/model eligibility, time zones, offsets, and reminder uniqueness before replacing current data. The native apps perform atomic persistence and retain one private pre-restore document for recovery. Restore clears quick-access setup and old occurrence/action tokens and leaves dose and supply reminders disabled. Notification permission grants never travel in a backup.

Both apps exclude their private database/recovery document from automatic OS backup. A user-selected document provider may itself use cloud storage; that destination is under the user's control. Lost passphrases cannot be reset. PDF/CSV files are unencrypted reports and are not restorable backups.

Synthetic interoperability fixtures are checked into the Android and iOS test directories. Their test-only passphrase is `test passphrase ä 😀`; these files contain no personal health information. Tests verify Android-to-iOS and iOS-to-Android decryption, wrong passwords, tampering, and round trips.

Schema-2 interoperability fixtures use the public test-only passphrase `should passphrase`. They contain synthetic independent observations, non-use, a pause and a supply ledger.

Schema-3 interoperability fixtures use the public test-only passphrase `could passphrase`. They contain synthetic weight measurements with distinct event and creation timestamps and original lb units.
