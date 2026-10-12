# CLAUDE.md

Prometheus patch for Lunar Client: a set of Mixins that unlock every Lunar cosmetic, emote, spray and badge client-side, and save what the player equips to disk. Read [CONTRIBUTING.md](CONTRIBUTING.md) for patch design, commit style (Conventional Commits) and the project's AI policy.

## Layout

- `mixin/com/lunarclient/...` mirrors the package of the Lunar class each mixin targets. Mixin classes are named `Mixin<Target>`, with `$Stub` for a nested `Stub` class. Every mixin must be listed in `src/main/resources/prometheus.lunar.mixins.json` (`required: true`, `defaultRequire: 1`, so a missing target crashes on launch).
- `util/FileUtils` holds our own state and the logger. Files are protobuf binaries under `OS.getConfigFolder()/lunar/`. That's `%APPDATA%/.minecraft/prometheus/lunar` on Windows, the same per-OS folders as essential on macOS/Linux, and it can be overridden with `PROMETHEUS_FOLDER`. Never use a path relative to the working directory, since Lunar's working directory changes with how it's launched. On startup, files from the old relative `prometheus/saved/` are copied over if missing.
  - `badge.bin`: the equipped badge ID as a 4-byte int; `-1` means no badge. Older files are a single byte (where `0xFF` meant `-1`), and `readBadge` still accepts them.
  - `outfits.bin`: length-delimited `Outfit` list. Every outfit gets a random `Uuid`, because Lunar keys outfits by id. A legacy single `outfit.bin` is read when `outfits.bin` doesn't exist yet.
  - `outfit_tree.bin`: the `OutfitTree`, i.e. the default outfit plus conditional ones. `fixOutfitTree` drops references to deleted outfits.
  - `emotes.bin` / `sprays.bin`: length-delimited `EquippedEmote` / `EquippedSpray` lists.
- There are no loader entrypoints. Forge loads the mixin config through `META-INF/MANIFEST.MF` (`MixinTweaker`), and Fabric through `fabric.mod.json`. Mod id is `prometheus-lunar` in every descriptor (`fabric.mod.json`, `mcmod.info`, `mods.toml`); keep them in sync.

## How unlocking works (non-obvious)

- Lunar talks to its backend over a websocket RPC layer of generated protobuf services (`com.lunarclient.websocket.<feature>.v<N>.<Feature>Service.Stub`). We `@Overwrite` the stub methods, so **the request never reaches the server**. Instead we build the response ourselves and pass it to `rpcCallback.run(...)`, and Lunar handles it like a real server reply.
- `login` responses set the `hasAll<X>Flag` (plus `artistTools` for cosmetics) and fill in the equipped state from `FileUtils`. Lunar unlocks everything from that flag; we never list individual cosmetic IDs.
- Update/equip methods (`updateOutfit`, `equipBadge`, `updateEquipped*`) just persist the request to disk and never run the callback; Lunar passes empty callbacks for these. Since the server never sees them, our files are the only source of truth across restarts.
- Outfit management (`createOutfit`, `deleteOutfit`, `selectOutfit`) is handled locally too and replies `STATUS_OK`. Lunar only applies these changes on an OK reply, and the real server doesn't know our outfits.
- Any stub method we don't overwrite still goes to the real server. Before adding a feature, list a stub's methods (`javap -p`) to see which calls are still unpatched.
- Use methods (`useEmote`, `useSpray`) reply `STATUS_OK` so Lunar plays them locally.
- Only the cosmetic service has two versions in the jar (`cosmetic/v1` and `cosmetic/v2`). We patch `v2`. If Lunar bumps a service version, the new package needs its own mixin.
- Staff mods (only Xray today) are the one thing with no unobfuscated hook. Lunar marks one available only when the server sends Apollo's `EnableStaffModsMessage`, and the Xray mod clears that flag on every disconnect. `util/StaffMods` (started from the cosmetic `login`) works around this without naming obfuscated classes:
  - It scans Lunar's jar once for the staff-mod class: a small class containing the strings `Xray` and `getOrdinal`, with lombok's unobfuscated `getName()`/`getOrdinal()`, exactly one instance `()Z` getter and one `(Z)V` setter, and `public static final` fields of its own type.
  - It then sets every such instance available again every second, on a daemon thread.
  - If the structure no longer matches, it logs a warning and staff mods stay locked; it never crashes. Check it against a new build by running `StaffMods.unlock()` in a harness on the mock jar.

## Mixin rules

- Target only the **unobfuscated** packages: `com.lunarclient.websocket` (protobuf stubs/messages) and `com.google.protobuf`. `com.moonsworth.lunar` is ProGuard-obfuscated (`HHICICROOC...`-style names), and those names change between Lunar builds. When there is truly no unobfuscated hook, find the class by structure at runtime, as `StaffMods` does, instead of hardcoding obfuscated names.
- Always use `remap = false`. The targets aren't Minecraft classes, so there are no mappings for them.
- Before adding or changing a target, verify it exists in the current Lunar build. The jar is at `~/.lunarclient/offline/multiver/lunar.jar`; `lib/lunar.mock.jar` is a copy of it (check `lunarBuildData.txt` inside for the version). To inspect a class: `unzip` it, then `javap -p -c -cp <dir> <class> | grep <target>`.
- Logging uses `java.util.logging` (`FileUtils.logger`). Lunar silences JUL, so `FileUtils` installs its own `ConsoleHandler`. Keep using that logger, not a new one.

## Reading Lunar's code

The jar has class files only (plus some bundled library sources, e.g. geckolib). Decompile with IntelliJ's bundled Vineflower, which needs Java 25, i.e. IntelliJ's JBR; the system JDK is 21:
```
"C:/Program Files/JetBrains/IntelliJ IDEA 2025.3/jbr/bin/java.exe" -cp "C:/Program Files/JetBrains/IntelliJ IDEA 2025.3/plugins/java-decompiler/lib/java-decompiler.jar" org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler -dgs=1 <classes-dir>/ <out-dir>/
```
Extract only the packages you need into a scratch dir first; the jar holds ~18700 classes (the protobuf stubs alone are ~2400 under `com/lunarclient/websocket`).

## Building / checking

- Maven isn't on PATH on this machine (the Nix flake provides it elsewhere). To type-check without it, run `javac` against the mock jar plus the Mixin jar from `~/.m2`. Pass Windows paths (`cygpath -w`) in the classpath. Use `-proc:none`, since the Mixin annotation processor needs Guava, and `--release 8`. Use `-implicit:none -sourcepath ""` too, because the mock jar contains `.java` files that javac would otherwise try to compile:
  ```
  javac -proc:none --release 8 -implicit:none -sourcepath "" -Xlint:-options -cp "lib/lunar.mock.jar;<m2>/org/spongepowered/mixin/0.8.7/mixin-0.8.7.jar" -d <out> $(find src/main/java -name "*.java")
  ```
- No test suite. For `FileUtils` round-trips, a throwaway harness on the mock jar classpath can call the real protobuf message classes. Set `PROMETHEUS_FOLDER` to a scratch dir so files don't land in the real save folder.
- Code targets Java 8: no `var`, `List.of`, records, etc.
- In-game testing is done by the user; report what was and wasn't verified.
