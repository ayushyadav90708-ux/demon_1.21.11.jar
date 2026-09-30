# Enhanced Baritone 1.21.11

This fork keeps Baritone's existing pathfinding/mining architecture and adds:

- `#mine <block>` — existing Baritone mine command.
- `#find <block>` — existing Baritone cache search.
- `#takeme <x> <y> <z>` — alias for `#goto`, using Baritone's native pathfinder.
- `#stop` — cancels Baritone tasks and combat assistance.
- `#fight <player>` — targets a named player using a temporary high-priority combat process.
- Automatic hostile-mob defense: a nearby hostile mob temporarily takes control, Baritone attacks it, then the previous mining/navigation process remains active and can resume.

The combat process is temporary so it does not call `onLostControl()` on the underlying mining process when it takes priority. This is the key behavior needed for mine -> defend -> resume.

## Cloud build

Use GitHub Actions workflow **Build Enhanced Baritone 1.21.11**. It installs JDK 21 on the GitHub runner and uploads the built `dist/` artifact.
