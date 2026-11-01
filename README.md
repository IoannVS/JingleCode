# Jingle Code

Pixel-art winter decorations for JetBrains IDEs. Nothing floats in the air: every piece sits on a real edge
of the interface.

- **Garland** in the gaps between panels and above the status bar: twinkle, chase, color flow or warm white.
- **Icicles** under the main toolbar and under the active editor tab; their tips glint now and then.
- **Fireplace** with a stocking in the status bar.
- **Snow** over the editor and a **village** along its bottom edge, in four styles. The details change every day,
  windows and lamps light up in the evening, residents drop by now and then. Click a house to light its stove.
- **Snowdrift with a snowman** at the bottom of the Project panel.
- **Railway** at the bottom of Terminal, Run, Debug, Git and other bottom tool windows.
- **Santa's yard** at the bottom of Gradle, Maven, Database and other right tool windows.
- **Snow caps** on popups and context menus, a holly sprig on notifications.

The decorations react to your work. When a run, test or build succeeds, the garland turns gold and fireworks
go off over the toolbar; when it fails, the lights blink red and icicles fall down the window, then grow back.
A gift appears for every commit you haven't pushed yet, and a bullfinch brings Santa a letter for every notification.

## Season

Jingle Code switches itself on December 1 and puts the decorations away after the Old New Year,
January 14. Want them earlier? Set **Season** to **Always** in the settings.

## Settings

**Settings | Appearance | Jingle Code.** Pick a mode (Minimal, Normal, Maximum) or turn every decoration
on and off on its own. Snow and the scenes also have a button in the top right corner of the editor
and in the panel headers: garland bulb (nothing) → snowflake (snow only) → snow with the scene → the scene alone.

The animation pauses when the IDE window is inactive, in Power Save mode and in Presentation mode.

## Compatibility

Any JetBrains IDE 2025.1 or newer. The garland, icicles, snowman and Santa's yard need the new Islands UI.
Git is optional: without it the plugin works, the gifts just don't appear.

## Building

```shell
./gradlew runIde                              # sandbox IDE with the plugin
./gradlew runIde -PjingleNow=2026-12-31T23:59 # pretend it's another day (or a date alone)
./gradlew test buildPlugin                    # the plugin zip goes to build/distributions
./gradlew verifyPlugin                        # compatibility check, slow; run before a release
```

## Releasing

1. On `develop`, set `version` in `gradle.properties` and run `./gradlew patchChangelog` to move
   the `[Unreleased]` notes under it.
2. Open a pull request into `master`: CI builds, tests and runs `verifyPlugin`.
3. Merge it. The Release workflow publishes the plugin to JetBrains Marketplace with the `PUBLISH_TOKEN`
   secret, tags the commit `v<version>` and makes a GitHub release with the zip.
   A merge without a version bump publishes nothing.

The very first version goes to Marketplace by hand: `./gradlew buildPlugin`, then upload `build/distributions/*.zip`.

## License

[Apache License 2.0](LICENSE)
