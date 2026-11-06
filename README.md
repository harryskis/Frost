# Frost

A Fabric client mod for Hypixel Skyblock that tracks per-player contribution during
Catacombs dungeon runs - exactly which rooms each player cleared (solo or as part of a
stack), secrets found, and a configurable weighted "contribution score" that accounts for
some rooms being harder than others.

Built for Minecraft 26.3 (Fabric), in Kotlin. Requires Java 25.

## Features

Everything is set up from the `/frost` menu: draggable columns by category, where left-clicking
a feature turns it on or off and right-clicking opens its settings beneath it. Right-click a
column's header to collapse it, or use the search bar at the bottom.

- **Room tracking** - identifies the real name of every room cleared (e.g. "Crypt", "Ice
  Fill"), and who cleared it, split into solo vs. stacked credit.
- **Secrets tracking** - per-player secrets-found diff for the run, via the Hypixel API.
- **Configurable room weights** (`/frost roomweights`) - assign a difficulty weight to
  each room individually, grouped by shape (1x1, 1x2, 1x3, 1x4, 2x2, L) plus a "Misc"
  category for puzzle/entrance/fairy/blood/trap rooms.
- **Weighted contribution score** - an end-of-run message summarizing each player's score
  (room weights + secrets), with automatic retries if Hypixel's API hasn't caught up yet
  with the run's secrets.
- **Configurable chat channel** (`/frost` -> Dungeons -> Contributions) - send the contribution
  score to party chat, all chat, or keep it local-only.
- **On-demand summary** - `/frost lastrun` re-prints the current/most recent run's
  breakdown at any time.
- **Aim Assist** (`/frost` -> Dungeons -> Aim Assist) - a light, adjustable pull toward
  [Odin](https://github.com/odtheking/Odin)'s etherwarp waypoints, only while sneaking with
  an Aspect of the Void. Requires Odin (0.4.0+ on 26.3) with its Dungeon Waypoints module enabled.
- **Dungeon Map** (`/frost` -> Dungeons -> Dungeon Map) - a movable dungeon map on your HUD showing
  the rooms that have been opened, with party members shown as skin heads that turn with them.
  Names turn green when a room is cleared and secret counts when they're all found. Left-click
  toggles it; right-click opens its menu: **Edit Map** to drag it around (hold it and scroll to
  resize), **Map Style** to draw it as Frost's own map or like NoammAddons' (**NA**), Odin's
  or LegitCatMod's (**LCM**), each with that mod's colors, borders, doors, checkmarks and
  heads, in Legit and Funny alike, **NA Room Text** for what the NA style writes on rooms
  (checkmarks, secrets, names, or names and secrets, as NoammAddons' own setting), **Show
  Princes** for a crown on each room a prince can be in (Big Red Flag, Chambers, Doors, Flags,
  Grass Ruin, Leaves, Market, Pirate, Quartz Knight, Red Blue, Skull, Sloth, Supertall,
  Waterfall and Withermancer), in the room's **Top Left** corner or **Center**ed under its
  name, with **Hide on Kill** to clear them all once a prince dies - from Hypixel's "A Prince
  falls" line or a party's "Prince Killed!" message, **Blood Rush Path** for a line showing the way to the blood door until it's opened,
  **Total Secrets** for a line under the map with the team's secrets out of the dungeon's total,
  worked out from the tab list, **Limit Room Names** to shrink long names to fit their rooms, **Special Room Names** to hide the
  names of blood, fairy, entrance, miniboss or trap rooms, **Fill Completed Rooms** to fill rooms
  with a solid color once both are done, **Filled Room Types** to include blood, miniboss, trap
  and puzzle rooms in that, **Fill Color** and **Background Color** color pickers, and **Player
  Head Size** and **Room Text Size** sliders. Fill Completed Rooms, Limit Room Names and the
  color pickers are part of the Frost style; the other styles keep their own mod's look.
- **Funny Map** (`/frost` -> Dungeons -> Funny Map) - shows every room on Dungeon Map before it's
  discovered, read from the world itself, dimmed until someone opens it. Only shows what your
  client has loaded, so far rooms stay blank until you get closer. Right-click for **Toggle
  Funny** to turn it on and off with a key, **Star Mobs** to mark the starred mobs in your current
  room, and **Blood Rush Alert** for big text when the blood rush only goes through 2 rooms, not
  counting entrance, fairy and blood, with **Alert 3 Room** to also warn of 3, and **Alert on
  Possible** to warn when the part that hasn't loaded could make it that short.
- **Glass Doors** (`/frost` -> Dungeons -> Glass Doors) - turns the door out of the entrance
  room into glass, on your client only, so you can see through it while it still blocks you
  and opens normally. Right-click it to do the same to wither doors.
- **Star Mob ESP** (`/frost` -> Dungeons -> Star Mob ESP) - highlights every starred mob through
  walls, in a color you choose, as an outline or a filled box.
- **M5 Auto Ult** (`/frost` -> Dungeons -> M5 Auto Ult) - as Healer or Tank, presses your drop key
  once about 10 seconds after entering the M5 boss, to use your ultimate.
- **Leap Fix** (`/frost` -> Dungeons -> Leap Fix) - after a spirit leap, turns you to face the same
  way as the player you leaped to.
- **Fairy Door ESP** (`/frost` -> Dungeons -> Fairy Door ESP) - highlights the wither door out of
  the fairy room through walls, so you can see which way to go before you enter.
- **Key Pickup** (`/frost` -> Dungeons -> Key Pickup) - when anyone picks up a wither or blood
  key, **Pickup Notification** shows "Wither Key Picked Up" or "Blood Key Picked Up" on screen,
  and **Pickup Sound** plays a sound; either can be turned off. Right-click it for **Move
  Notification** (drag it, hold and scroll to resize), **Size**, **Duration**, and a color for
  each key; and for the sound, a searchable list of every Minecraft sound (click one to hear
  it), **Volume**, **Pitch** and **Play Sound** to test it.
- **M5 Shitters** (`/frost` -> Dungeons -> M5 Shitters) - shows the names of players on the
  M5 Shitters list in red, in chat, in Party Finder and in the tab list. Click a party in Party
  Finder, or a red name in chat (like the "joined the dungeon group" message), to see why
  they're on the list; hovering a name in chat shows it too. It uses the M5 Shitters database
  out of the box; right-click it to change **Database** to another link: a Google Sheet,
  Pastebin, GitHub file, or any JSON, CSV or text with a username or UUID and a reason per
  player. Players listed by UUID have their current names looked up from Mojang (a couple a
  second, saved in `config/frost-uuid-names.json` and rechecked every few days), so their names
  go red as they come in. It reloads every 15 minutes, or now with
  **Reload List** or `/frost shitters reload`. `/frost shitters add <name> <reason>` and
  `remove <name>` keep your own additions on top of it, and `/frost shitters reason <name>`
  and `list` show what's on it.
- **Croesus** (`/frost` -> Dungeons -> Croesus) - tracks your unopened Croesus chests, with a
  movable HUD showing `Unopened Chests: xx/60` and a big red warning at 55 or more. The real
  count is read in the Dungeon Hub, and each run you finish adds one until you're back there.
- **Chat Regex** (`/frost` -> QoL -> Chat Regex) - reacts to chat messages you choose. Add a
  rule, type the message to watch for (like `A Wither Key was picked up` - it matches anywhere in
  a line, ignoring case and colors), and pick what happens: text on screen, with its own
  **Text**, **Color**, **Size**, **Duration** and place (**Move Text**), and/or a **Sound**
  with **Volume** and **Pitch**. Blank text shows the chat line itself. Turn on **Regex** to
  match with a regular expression, and put its groups in the text as `$1`, `$2`...; `&` color
  codes work in the text too. **Test** shows and plays a rule without waiting for the message.
- **Spotify playback control** (`/frost spotify`) - skip, go back, and play/pause Spotify
  from in-game, bindable to any key via the Keybinds screen. Requires Spotify Premium (a
  Spotify platform requirement for playback control, not a mod limitation).

## Recommended Values

Here are the recommended values for room weights. Feel free to change these at your 
desire, just note that doing so may not reflect accuracy. If not mentioned below, assume 
1 for default value. These are also exactly what a fresh install starts with, and what
`/frost roomweights reset` restores them to.

**1x2 Rooms**
- Bridges: 2
- Crypt: 4
- Gold: 2
- Mage: 2
- Pedestal: 2
- Skull: 2

**1x3 Rooms**
- Slime: 3

**1x4 Rooms**
- Pipes: 2
- Pit: 4
- Waterfall: 2

**2x2 Rooms**
- Atlas: 2
- Mines: 2

**L Rooms**
- Altar: 3
- Chambers: 2
- Lava Ravine: 2
- Layers: 3
- Pirate: 2
- Withermancer: 2

**Misc. Rooms**
- Blood: 0
- Entrance: 0
- Fairy: 0
- Higher Blaze: 4
- Ice Fill: 9
- Ice Path: 4
- Lower Blaze: 4
- New Trap: 2
- Quiz: 5
- Teleport Maze: 2
- Tic Tac Toe: 5
- Water Board: 6

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.3, and make sure you are running Java 25 or newer (Minecraft 26.x requires it).
2. Install [Fabric API](https://modrinth.com/mod/fabric-api) and
   [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin).
3. Drop Frost's jar into your `mods` folder.
4. In-game, run `/frost apikey <your key>` (or use `/frost` -> Developer -> Hypixel API) with
   a personal key from [developer.hypixel.net](https://developer.hypixel.net) - required
   for secrets tracking. Hypixel API keys expire periodically; if secrets stop tracking,
   generate a new one.

## Spotify Controls

Spotify's playback API is only available to Premium accounts, and every app that talks to
it - including this one - needs its own free Spotify Developer app registered by you. Frost
never sees your Spotify password; it only ever gets a per-app access token via Spotify's
standard PKCE login flow.

1. Go to [developer.spotify.com/dashboard](https://developer.spotify.com/dashboard), log
   in, and click **Create app**.
2. Give it any name/description. For **Redirect URI**, enter exactly:
   `http://127.0.0.1:39562/callback`
3. Save, open the app, and copy its **Client ID**.
4. In-game: `/frost spotify clientid <your client id>`
5. In-game: `/frost spotify login` - this opens your browser to Spotify's consent page.
   Approve it, then return to Minecraft; the tab it opens can be closed once it says so.
6. `/frost spotify status` confirms you're connected and shows what's currently playing.

Once connected, `/frost spotify next`, `previous`, and `playpause` control playback on
whatever device Spotify is currently active on. Bind them to keys via `/frost` ->
QoL -> Keybinds (or type them directly in chat) - there's no separate keybind system just for
Spotify.

| Command | Description |
| --- | --- |
| `/frost spotify clientid <id>` | Saves your Spotify app's Client ID. |
| `/frost spotify login` | Starts the browser-based login/consent flow. |
| `/frost spotify status` | Shows connection state and the current track. |
| `/frost spotify next` / `previous` | Skips to the next/previous track. |
| `/frost spotify playpause` | Toggles play/pause. |

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
| `/frost` | Opens the main menu, where every feature is toggled and configured. |
| `/frost apikey <key>` | Sets your Hypixel API key directly from chat. |
| `/frost roomweights` | Opens the room-weight configuration screen. |
| `/frost roomweights reset` | Resets room weights to the recommended values above. |
| `/frost lastrun` | Re-prints the current/most recent run's room breakdown. |
| `/frost spotify ...` | Spotify playback control - see [Spotify Controls](#spotify-controls). |

## Credits

Frost's dungeon room-name database and world-block room-identification algorithm are
adapted from [Odin](https://github.com/odtheking/Odin) by odtheking (BSD 3-Clause) - see
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for the full license text.

## License

[MIT](LICENSE) for Frost's own code. See `THIRD_PARTY_NOTICES.md` for the license terms
covering the ported Odin data/algorithm.

## Contact

Bug reports and feature requests: **harryskis** on Discord.
