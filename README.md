# Heaven (Fabric, Minecraft Java 1.21.11)

Client-side automation and navigation assistant. Mod id `heaven`, version `1.0.0`.
Requires Fabric Loader, Fabric API and Java 21.

## Build with no software installed (GitHub Actions)

1. Create a free GitHub account and a new empty repository.
2. Upload this project's contents to it (repository page -> **Add file -> Upload files**, drag in everything,
   including the `.github` folder). If the hidden `.github` folder is skipped by the uploader, use
   **Add file -> Create new file**, type `.github/workflows/build.yml` as the name and paste the file contents.
3. Open the **Actions** tab. The **Build Heaven** workflow runs on every push (or press **Run workflow**).
4. When it turns green, open the run and download the **heaven-1.0.0** artifact. Unzip it to get `heaven-1.0.0.jar`.
5. Put the jar in your `.minecraft/mods` folder together with Fabric API (Fabric Loader for 1.21.11).

The workflow installs Java 21 and Gradle 9.2.1 itself and looks up the newest Yarn mappings, Fabric Loader and
Fabric API for 1.21.11 before compiling (the values in `gradle.properties` are only fallbacks).
No Gradle wrapper is needed.

If the build fails, open the failed step in the Actions log; the error lines show the exact file and line.

## Commands

| Command | What it does |
|---|---|
| `/mine <block>` | Finds blocks of that type that have an unobstructed line of sight from your eyes, walks to them, mines them, collects drops, and repeats. Gives up after ~30 s with nothing visible. Example: `/mine diamond_ore` |
| `/takeme <x> <y> <z>` | Walks to the coordinates (jumps 1-block steps, drops up to 3, avoids lava/fire/cactus). `/takeme <x> <z>` ignores height. |
| `/find <target>` | Biomes: sampled from chunks already loaded, otherwise asks the server with vanilla `/locate biome`. Structures (village, stronghold, mansion, monument, ancient_city, fortress, ...): uses vanilla `/locate structure` and reads the reply. |
| `/stop` | Stops the current task and releases all keys. |
| `/heaven status` | Shows task, state, target, destination, food, combat, boat and elytra state. |
| `/heaven food\|defense\|boat\|elytra on\|off` | Toggle each assistant. |

## Assistants (active only while a Heaven task is running)

- **Auto food**: below 6 drumsticks it picks safe food (skips rotten flesh, raw chicken, spider eyes, pufferfish, golden apples...), eats until 19+ hunger.
- **Boat**: if open water lies ahead on a `/takeme` trip and you carry a boat, it places it, boards, sails toward the goal, leaves near land and breaks the boat to take it back.
- **Elytra**: only if an Elytra is worn, you carry 2+ fireworks and the goal is 180+ blocks away. Takes off, uses a rocket only when speed drops, and lands about 40 blocks out.
- **Mob defense**: attacks hostile mobs (Monster) within 6 blocks with a hotbar sword/axe if you have one. It never targets players, and leaves Endermen, zombified piglins and Wardens alone.

## Fair play

No X-ray or through-wall detection (ore must be reachable by a clear line from your eyes), no packet tricks,
no anti-cheat bypasses, no player-targeting combat. Movement, mining and eating are done through normal key
presses and standard interaction calls. Many servers ban automation mods, so check the rules before using it online.

## Known limitations

Pathfinding is a bounded local A* over loaded chunks (no diagonal moves, no gap jumping, no door/parkour logic).
Elytra flight is basic and can still crash into terrain. Coordinates must be absolute numbers (no `~`).

## Layout

`src/main/java/com/heaven/`: `HeavenClient`, `CommandManager`, `TaskManager`, `NavigationManager`, `MiningManager`,
`CombatManager`, `FoodManager`, `BoatManager`, `ElytraManager`, `FindManager`, `HudManager` plus small helpers.
