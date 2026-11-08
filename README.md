# Player Hit Beacons (Fabric, Minecraft 1.21.11, client-side only)

Every player you hit gets their own colored beacon above their head, visible through blocks,
entities and crowds, for 20 seconds after your last hit on that player.

Press `=` to turn beacons on/off (rebind in Options > Controls > Key Binds).

Colors in order: red, blue, green, purple, yellow, cyan, orange, pink, white, lime, then
auto-generated distinct hues.

## Build
Push this folder to GitHub; Actions builds the jar (artifact `hit-beacons-jar`).
Locally: JDK 21, `gradle wrapper --gradle-version 9.2.1`, `./gradlew build`.

## Install
Put the jar in `.minecraft/mods` with Fabric API (Fabric Loader 0.18.1+).

## Tuning (constants in source)
- `BeaconTracker.REQUIRE_CONFIRMED_DAMAGE` (default false = mark on every swing at a player)
- `BeaconTracker.BEACON_DURATION_NANOS`
- `BeaconRenderer.BEAM_HEIGHT / HEAD_GAP`
- If beams don't show, use `WorldRenderEvents.END_MAIN` instead of `BEFORE_TRANSLUCENT`.
