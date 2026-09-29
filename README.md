# Heaven 1.1.0 — Fabric 1.21.11

Improved client automation inspired by the supplied teammate setup, which showed Fabric 1.21.11, Fabric Loader 0.19.5, Baritone-Meteor 1.21.11-SNAPSHOT, Nether Pathfinder, and an existing Heaven 1.0.0.

## Local # commands
- `#mine <block>`
- `#find <target>`
- `#takeme <x> <y> <z>`
- `#stop`
- `#status`
- `#fight <player>` is intentionally not an automated PvP bot; Heaven can defend against hostile mobs.

## Improvements
- # prefix is intercepted locally instead of being sent to chat.
- Mining task can pause when a hostile mob becomes an immediate threat and resume afterward.
- Navigation task pauses for threats and resumes afterward.
- Automatic food assistance.
- Visible/reachable block targeting only; no X-ray/through-wall mining.
- Input keys are released on stop.
- Cloud build workflow included.

## Cloud build
Upload the project to GitHub, open Actions, run `Build Heaven`, then download the `Heaven-Fabric-1.21.11` artifact. No Java/Gradle installation is required on your computer.
