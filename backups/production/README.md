# Production database snapshots

Only encrypted production snapshots are stored here. Never commit plaintext
`.sql`, `.sql.gz`, database credentials, or recovery keys.

## Snapshot: 2026-08-26 15:09:35 Asia/Shanghai

- Database: `SuperCampus`
- Tables at export time: 74
- Approximate live database size: 6.98 MiB
- Encrypted file: `production-db-20260826-150935.sql.gz.aes`
- Encrypted file size: 291112 bytes
- Encrypted SHA-256: `f917fe76a140684534fc836dfc66f676e6c75fc55ffa8136ba7eeaa272335321`
- Source gzip SHA-256: `e443045577850fc395e3e592ca4360a0350c81ab485e5ffa84bab530ba72698c`
- Encryption: AES-256-CBC with HMAC-SHA-256 (encrypt-then-MAC), format `CDBAK001`

The recovery key is intentionally outside Git:

```text
C:\Users\75177\.campus-backup\production-db-aes.key
```

Back up that key separately. Losing it makes the encrypted snapshot
unrecoverable.

## Restore the compressed SQL file

Run from the repository root:

```powershell
.\scripts\database\Restore-DatabaseBackup.ps1 `
  -InputPath .\backups\production\production-db-20260826-150935.sql.gz.aes `
  -OutputPath $env:TEMP\production-db-20260826-150935.sql.gz `
  -KeyPath $env:USERPROFILE\.campus-backup\production-db-aes.key
```

The restore script verifies the HMAC before decrypting. After restoring, verify
the gzip SHA-256 against the value above before importing it into MySQL.
