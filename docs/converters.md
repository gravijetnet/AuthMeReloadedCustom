# Account converters

Run `/authme converter <name>` with the `authme.admin.converter` permission.

## Auth+: `authplus`

Imports accounts from `plugins/Auth/players.yml`. Set `passwordHash` to `PBKDF2BASE64` and `settings.security.pbkdf2Rounds` to `120000` in AuthMe's `config.yml` first.

Player names are resolved through the server's `usercache.json`. Accounts without a cached name and accounts already in AuthMe are skipped.

## Database migration

- `sqlitetosql`: copy SQLite accounts into the configured SQL database.
- `mysqltosqlite`: copy MySQL accounts into SQLite.
