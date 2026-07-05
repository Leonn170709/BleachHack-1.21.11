---
description: Autonomously keep porting BleachHack from 1.19.4 to 1.21.11 until it's fully done - real fidelity first, compilable fallback only when truly impossible.
---

# Goal: finish the 1.19.4 -> 1.21.11 port

You are continuing a large, already-in-progress port of this Fabric mod from Minecraft 1.19.4
(branch `1.19.4`) to 1.21.11 (branch `1.21.11`). Work on the `1.21.11` branch.

## Mission

Get the mod to compile cleanly (`./gradlew build` with zero errors) **while making the 1.21.11
build feel exactly like the 1.19.4 build** - same modules, same settings, same visual behavior,
no missing features, no regressions a player would notice. Priority order, in this exact rank:

1. **Feel identical to 1.19.4.** Every module, every setting, every visual/behavioral detail should
   survive the port. Don't take a shortcut just because it's easier if a faithful fix is achievable.
2. **Only if something is genuinely, verifiably impossible** (the API/hook it depended on was
   removed with no equivalent anywhere in the new engine) - fall back to the closest possible
   substitute that still compiles, and document exactly what changed and why in a code comment.
   Never silently drop a feature; never guess and hope.
3. Don't stop to ask permission for routine fixes. Keep going, file by file, error by error, until
   the whole thing builds and you've swept back over the fallbacks looking for a better fix.

Do not stop and hand back control just because the task is large. Keep working across as many
tool calls as it takes. Only interrupt the user if you hit a decision that is genuinely theirs to
make (e.g. "drop this whole module" vs "reimplement it a harder way") - not for routine porting
judgment calls, which you should make yourself and document.

## Ground truth methodology (do not deviate from this - it's what's worked so far)

**Never guess a Yarn mapping, method signature, or class shape.** This version jump changed almost
every rendering/item/entity subsystem's architecture, not just names. Before writing a replacement
for any broken API:

1. Find the real answer in Loom's own cache, which already has the actual 1.21.11 Yarn-mapped jar
   and (after a `./gradlew genSources`) decompiled sources:
   - Mapped class jar: `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/**/*.jar`
   - Decompiled sources jar: `<repo>/.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/**/*-sources.jar`
   - Unzip the specific class/file you need and read it (`javap -p` for a quick signature check,
     full decompiled source when you need to understand *how* something is used, not just its shape).
2. Cross-check by searching for real vanilla call sites of the same API (e.g. how does vanilla's
   own debug renderer, HUD, or another entity renderer call this?) rather than inventing a call
   pattern from memory.
3. Only after you've confirmed the real shape do you write the replacement code.
4. If, after genuinely digging, an API/hook has *no* accessible equivalent (verify this - check
   adjacent classes, widened access via a new accesswidener entry, whether vanilla itself still
   does the equivalent thing somewhere reachable) - that's when rung 2 of the priority order kicks
   in. Write the closest working substitute and leave a comment stating what 1.19.4 did, what's
   different now, and why no better option exists.

## How to pick up where things stand

1. Check the task list (`TaskList`) for what's marked done vs pending - it reflects real progress
   from prior work in this repo.
2. Don't trust it blindly though - run `./gradlew compileJava` yourself first to see the actual
   current error count and file list. That log is ground truth; the task list is a summary.
3. Work through remaining errors in this rough priority order (highest engine-risk / most
   fan-out first, since fixing shared utilities early collapses dozens of downstream errors):
   shader/render pipeline -> render & world mixins -> packet interception -> GUI (`DrawableHelper`
   -> `DrawContext` migration) -> item/DataComponent migration -> everything else.
4. When you fix a shared utility (e.g. a `Renderer`/`Vertexer`-style class), re-run compileJava
   before moving on - it usually resolves a batch of files at once.
5. Update the task list as you go so a future `/goal` run (or a human checking in) can see real
   state without re-deriving it.

## Definition of done

- `./gradlew build` succeeds from a clean checkout, zero errors, zero unresolved references.
- Every module listed in `bleachhack.modules.json` / command in `bleachhack.commands.json` is still
  registered and loads.
- Every mixin in `bleachhack.mixins.json` targets a real class/method in 1.21.11 (re-verify anything
  fixed early in the port, since later findings sometimes invalidate earlier assumptions).
- Every documented fallback (search the codebase for comments explaining a 1.19.4 -> 1.21.11 gap)
  has been revisited at least once to check whether a newer understanding of the engine makes a
  more faithful fix possible after all - don't let "impossible" from three files ago go unchallenged.
- No dead code left behind from deleted/obsolete mixins, classes, or assets.

Start now. Don't ask whether to proceed - just go.
