# AuthMeReloadedCustom

Custom version of [AuthMeReloaded](https://github.com/AuthMe/AuthMeReloaded) for Minecraft authentication. Blocks movement, chat, commands and inventory access until a player logs in.

Includes login and registration dialogs, proxy authentication, two-factor authentication and account migration.

## Build

Requires Maven 3.8.8 or newer. JDK 21 or newer builds all modules; JDK 17 builds the core, tools and legacy Spigot module.

```sh
mvn clean package
```

Choose the JAR for your server platform. Legacy Spigot uses Java 17; the other server and proxy modules use Java 21. PacketEvents is required for inventory protection.

See [build commands](docs/build.md) for individual modules and tests.

## Configuration

AuthMe creates `plugins/AuthMe/config.yml` on first start.

- [Settings](docs/config.md)
- [Commands](docs/commands.md)
- [Permissions](docs/permission_nodes.md)
- [Proxy setup](docs/proxies/configuration.md)
- [Account converters](docs/converters.md)
- [Translations](docs/translations.md)

Use `settings.registration.useDialogUi` for login dialogs after joining. Pre-join dialogs use `settings.registration.usePreJoinDialogUi` and require Paper or Folia with dialog support.

GPL v3. Based on AuthMeReloaded, with credits to its [developers and translators](https://github.com/AuthMe/AuthMeReloaded/wiki/Development-team). GeoIP uses MaxMind GeoLite data.
