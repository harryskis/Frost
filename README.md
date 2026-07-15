# Frost

A Fabric client mod for Hypixel Skyblock that tracks per-player contribution during
Catacombs dungeon runs - exactly which rooms each player cleared (solo or as part of a
stack), secrets found, and a configurable weighted "contribution score" that accounts for
some rooms being harder than others.

Built for Minecraft 1.21.11 (Fabric), in Kotlin.

## Features

- **Room tracking** - identifies the real name of every room cleared (e.g. "Crypt", "Ice
  Fill"), and who cleared it, split into solo vs. stacked credit.
- **Secrets tracking** - per-player secrets-found diff for the run, via the Hypixel API.
- **Configurable room weights** (`/frost roomweights`) - assign a difficulty weight to
  each room individually, grouped by shape (1x1, 1x2, 1x3, 1x4, 2x2, L) plus a "Misc"
  category for puzzle/entrance/fairy/blood/trap rooms.
- **Weighted contribution score** - an end-of-run message summarizing each player's score
  (room weights + secrets), with automatic retries if Hypixel's API hasn't caught up yet
  with the run's secrets.
- **Configurable chat channel** (`/frost` -> "Chat Channel") - send the contribution
  score to party chat, all chat, or keep it local-only.
- **On-demand summary** - `/frost lastrun` re-prints the current/most recent run's
  breakdown at any time.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11.
2. Install [Fabric API](https://modrinth.com/mod/fabric-api) and
   [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin).
3. Drop Frost's jar into your `mods` folder.
4. In-game, run `/frost apikey <your key>` (or use the "API Key" screen from `/frost`) with
   a personal key from [developer.hypixel.net](https://developer.hypixel.net) - required
   for secrets tracking. Hypixel API keys expire periodically; if secrets stop tracking,
   generate a new one.

## Building from source

```
./gradlew build
```

The output jar is written to `build/libs/`.

Run the unit test suite (no Minecraft client required) with:

```
./gradlew test
```

## Commands

| Command | Description |
| --- | --- |
| `/frost` | Opens the main menu (API key, room weights, chat channel). |
| `/frost apikey <key>` | Sets your Hypixel API key directly from chat. |
| `/frost roomweights` | Opens the room-weight configuration screen. |
| `/frost lastrun` | Re-prints the current/most recent run's room breakdown. |

## Credits

Frost's dungeon room-name database and world-block room-identification algorithm are
adapted from [Odin](https://github.com/odtheking/Odin) by odtheking (BSD 3-Clause) - see
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for the full license text.

## License

[MIT](LICENSE) for Frost's own code. See `THIRD_PARTY_NOTICES.md` for the license terms
covering the ported Odin data/algorithm.

## Contact

Bug reports and feature requests: **harryskis** on Discord.
