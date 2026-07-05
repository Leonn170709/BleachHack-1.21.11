# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

BleachHack is a Fabric mod (Minecraft utility/"hacked client") for Minecraft 1.19.4, written in Java 17. This is the `1.19.4` branch (each Minecraft version lives on its own branch — do not assume other branches share code layout).

## Build / dev commands

```
./gradlew build              # build the mod jar
./gradlew genSources idea     # generate sources + IntelliJ project (or `eclipse` for Eclipse)
```

There is no test suite — this is a client-side game mod driven by manual/in-game verification, not unit tests.

Versions (Minecraft, Yarn mappings, Fabric Loader) are pinned in `gradle.properties`. Fabric API is optional/commented out there; some modules must degrade gracefully when it's absent (see recent "Fix Fabric api compatibility" fixes).

## Architecture

### Two-phase init (`BleachHack.java`)

- **Phase 1** (`onInitialize`, standard Fabric entrypoint): only touches things safe before the game is fully up — file I/O init, reading saved options/friends, kicking off the async update check.
- **Phase 2** (`postInit()`): called manually from `MixinMinecraftClient` once the game client is available. This is where modules, commands, ClickGui windows, and the command suggestor actually get loaded. **If you add code that touches `MinecraftClient` state at startup, it almost certainly belongs in `postInit`, not `onInitialize`.**

### Modules are registered via JSON manifest, not classpath scanning

New modules/commands are **not** auto-discovered. You must add the class name to:
- `src/main/resources/bleachhack.modules.json` (package `org.bleachhack.module.mods`) for modules
- `src/main/resources/bleachhack.commands.json` (package `org.bleachhack.command.commands`) for commands

`ModuleManager`/`CommandManager` reflectively `Class.forName` + no-arg-construct every entry listed there at Phase 2. A module/command that exists as a `.java` file but is missing from its JSON list will never load.

### Module anatomy

A module extends `Module` (`module/Module.java`), calling `super(name, key, ModuleCategory, [defaultEnabled,] desc, settings...)`. Settings (`SettingSlider`, `SettingMode`, `SettingToggle`, `SettingColor`, `SettingKeybind`, etc., in `setting/module/`) are passed positionally and read back by index via `getSetting(n).asX()` — order matters and is not name-keyed.

Modules subscribe to the event bus automatically on enable/disable (`onEnable`/`onDisable` in `Module.java` call `BleachHack.eventBus.subscribe/unsubscribe(this)`); listener methods are annotated `@BleachSubscribe` and take a single `Event*` argument. Look at an existing module in `module/mods/` (e.g. `Fullbright.java`) as the template for a new one rather than starting from scratch.

### Custom event bus, not Fabric events

`eventbus/BleachEventBus.java` is BleachHack's own pub/sub system (not Fabric's event API). Event types live in `event/events/` (`EventTick`, `EventPacket`, `EventRenderBlock`, etc.) and are fired from mixins into game code — search for an event's class name to find which mixin dispatches it before assuming an event fires where you'd expect.

### Mixins

All mixins live flat in `mixin/` (package `org.bleachhack.mixin`) and must be listed in `src/main/resources/bleachhack.mixins.json` (`client` array). Accessor mixins are prefixed `Accessor*`; injecting mixins are prefixed `Mixin*`. Widened field/method access that doesn't need a mixin goes in `src/main/resources/bleachhack.accesswidener` instead — check there first before writing an Accessor mixin for a simple private-field read.

### GUI

`gui/clickgui/` is the module ClickGui (the toggle-and-configure panel); `gui/window/` is the more general draggable-window system (used for things like `WindowManagerScreen`) with its own `widget/` subpackage. These are separate UI stacks — don't conflate them.

### Persistence

Config (options, modules, friends, ClickGui/window layout) is read/written through `util/io/BleachFileHelper.java` and `BleachFileMang.java`, generally as JSON via `BleachJsonHelper`. Saves are debounced through scheduled flags (e.g. `BleachFileHelper.SCHEDULE_SAVE_MODULES.set(true)`) rather than written synchronously on every change — follow that pattern instead of writing files inline in hot paths.

## License note

Per `README.md`: distributing a modified version of BleachHack (or a mod with ported BleachHack features) requires disclosing source, stating changes, and using a compatible license per `LICENSE` (GPLv3).
