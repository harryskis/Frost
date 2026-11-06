# Third-party notices

## Odin (odtheking/Odin)

Frost's dungeon room-name database (`src/main/resources/assets/frost/rooms.json`) and
the world-block "core hash" room-identification algorithm in
`com.frost.dungeon.WorldRoomScanner` are adapted from
[Odin](https://github.com/odtheking/Odin) by odtheking, used under the BSD 3-Clause
License below. Neither the data nor the algorithm were reverse-engineered independently
by this project - they're a direct port, and this notice + the license text is required
by the terms of that license.

Dungeon Map's "Odin" style (`com.frost.gui.map.MapPainter`, `com.frost.dungeon.MapStyle`)
replicates Odin's dungeon map - its colors, sizes, door and label rules - and bundles
Odin's map checkmark icons (`src/main/resources/assets/frost/textures/map/odin/`), under
the same license.

```
BSD 3-Clause License

Copyright (c) 2025, odtheking

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

## dtMap (ricedotwho/dtMap)

Dungeon Map's "LCM" style (`com.frost.gui.map.MapPainter`, `com.frost.dungeon.MapStyle`)
replicates [dtMap](https://github.com/ricedotwho/dtMap) by rice.who - the map from
LegitCatMod - and bundles its map checkmark icons
(`src/main/resources/assets/frost/textures/map/lcm/`), used under the BSD 3-Clause License
below. Show Princes' list of the rooms a prince can be in (`RoomDatabase.PRINCE_ROOMS`) is
taken from dtMap's room data under the same license; its crown icon is Frost's own.

```
BSD 3-Clause License

Copyright (c) 2026, rice.who

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

## NoammAddons (Noamm9/NoammAddons)

Dungeon Map's "NA" style replicates the dungeon map of
[NoammAddons](https://github.com/Noamm9/NoammAddons) by Noamm9, and bundles its map
checkmark icons (`src/main/resources/assets/frost/textures/map/na/`). NoammAddons is
dedicated to the public domain under CC0 1.0 Universal, which asks for no notice - it's
credited here anyway.
