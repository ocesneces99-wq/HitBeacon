# Player Hit Beacons (Fabric, Minecraft 1.21.11, client-side only)

Every player you hit gets their own colored beacon above their head, visible through blocks,
entities and crowds, for exactly 20 seconds after your last hit on that player.

Colors in order: red, blue, green, purple, yellow, cyan, orange, pink, white, lime, then
auto-generated distinct hues. A new player takes the lowest color not used by an active beacon.

## Build the jar

Option A - GitHub Actions (no local setup): push this folder to a GitHub repo. The `build`
workflow produces the jar as the `hit-beacons-jar` artifact.

Option B - locally (JDK 21 + Gradle 9.2+):

    gradle wrapper --gradle-version 9.2.1   # once, creates ./gradlew
    ./gradlew build                          # jar: build/libs/hit-beacons-1.0.0.jar

## Install

Put `hit-beacons-1.0.0.jar` in `.minecraft/mods` together with Fabric API, using Fabric Loader
0.18.1+ for 1.21.11. Nothing is installed on the server.

## Tuning (constants in source)

- `BeaconTracker.REQUIRE_CONFIRMED_DAMAGE` - `true` (default): only count hits that actually hurt
  the target. `false`: mark on every swing that connects with a player.
- `BeaconTracker.BEACON_DURATION_NANOS` - 20 seconds.
- `BeaconRenderer.BEAM_HEIGHT / HEAD_GAP` - beam length and where it starts above the head.
- If beams do not show, change `WorldRenderEvents.BEFORE_TRANSLUCENT` to `END_MAIN` in
  `HitBeaconsClient`.
