# YuiMorph

English | [中文](README.md)

A disguise plugin for Minecraft: players can disguise as vanilla mobs or other players. The server plugin runs on Folia with PacketEvents; an optional Fabric client mod adds extra UI.

<!-- Placeholder: replace with a GIF -->
![YuiMorph demo](docs/images/demo.gif)

## Quick start

1. Download `YuiMorph-<version>.jar` from the [latest release](https://github.com/Cherry7593/YuiMorph/releases/latest) into `plugins/`
2. Install [PacketEvents](https://modrinth.com/plugin/packetevents) 2.14.0 or newer
3. Start the server and run `/ym` to open the menu, or `/ym morph pig`
4. (Optional) Install `yuimorph-fabric-<version>.jar` on the client and press `N` for the live-preview selector

By default a disguise must be unlocked by killing that mob (or player) first. OPs have `yuimorph.morph.any` and can use any disguise.

## Compatibility

| Component | Version | Status |
|---|---|---|
| Folia | 26.3 | Tested |
| Paper | 26.3 | Should work (Paper plugin format, API 26.3), **not fully tested** |
| PacketEvents | 2.14.0+ | Required |
| Java | 25 | Required |
| Fabric client mod (optional) | Minecraft 26.3, Fabric Loader 0.19.5+ | Requires Fabric API and Fabric Language Kotlin |

Players without the mod can still disguise and see other players' disguises.

## Features

- **Disguises**: vanilla mobs, or players with their Mojang skin (`player:<name>`). Rendered server-side with packets.
- **Properties** (`/ym property`, per mob type): baby, color, sheared, size (slime / magma cube / phantom), variants (cat, wolf, frog, pig, cow, chicken, axolotl, parrot, rabbit, fox, horse, ...), charged creeper, snow golem pumpkin, panda genes, villager type and profession, custom name, player skin.
- **Actions** (`/ym action`, per mob type): sit, lie down, sleep, dance, roar, crawl, dig, eat, sniff, croak, etc.
- **Unlocking**: kill to unlock (entities tagged `yuimorph_nogrant` give nothing); trade disguises with other players (`/ym request`).
- **Gameplay**: hitbox scales with the disguise (vanilla scale attribute, with a space check before growing); health scaling `none` / `capped` / `unlimited`; mob aggro (experimental) — mobs only target what they would target in vanilla; ambient, hurt and death sounds; disguise restored on rejoin; forced disguise for all players; ban disguises or whole categories; disable disguising per world.
- **UI**: chest GUI without the mod (`/ym`). With the mod: live-model selector (WYSIWYG properties, presets, recents), action wheel, HUD, see your own disguise in F5, first-person disguise arms / front legs.

## Commands

Root command `/yuimorph`, alias `/ym`. Tab completion and `/ym help` only show commands you have permission for.

| Command | Description | Permission |
|---|---|---|
| `/ym` | Open disguise menu | `yuimorph.morph` |
| `/ym morph <disguise>` | Disguise, e.g. `pig`, `minecraft:fox`, `player:Notch` | `yuimorph.morph` |
| `/ym unmorph` | Remove disguise | `yuimorph.unmorph` |
| `/ym property [key] [value]` | View / set / reset properties | `yuimorph.property` |
| `/ym action [action]` | List / play actions (`stop` ends a persistent one) | `yuimorph.action` |
| `/ym request send\|accept\|deny <player>` | Disguise trade | `yuimorph.request.send` / `.accept` / `.deny` |
| `/ym help` | Help | none |
| `/ym admin status\|reload` | Status / reload config | `yuimorph.admin` |
| `/ym admin grant\|revoke <players> <disguise>` | Grant / revoke unlock | `yuimorph.admin` |
| `/ym admin force <players> <disguise>` / `unmorph <players>` | Force disguise / remove | `yuimorph.admin` |
| `/ym admin query <players>` / `queryall` / `lookup <players>` | Current disguise / all disguised / unlocked list | `yuimorph.admin` |
| `/ym admin ban\|unban <disguise>` / `bans` | Ban / unban / list | `yuimorph.admin` |
| `/ym admin import feathermorph [confirm]` | Import FeatherMorph data | `yuimorph.admin` |

Client mod default keys (rebindable): `N` selector, hold `G` action wheel, `Down` unmorph.

## Permissions

| Permission | Description | Default |
|---|---|---|
| `yuimorph.morph` | `/ym`, `/ym morph` | everyone |
| `yuimorph.unmorph` | Remove disguise | everyone |
| `yuimorph.acquire` | Unlock by killing | everyone |
| `yuimorph.morph.any` | Use any disguise without unlocking | op |
| `yuimorph.morph.as.<namespace>.<key>` | Per-disguise; allowed unless explicitly set to false | unset |
| `yuimorph.property` | `/ym property` | everyone |
| `yuimorph.property.name` | Custom name | everyone |
| `yuimorph.property.skin` | Change player-disguise skin | op |
| `yuimorph.action` | Actions | everyone |
| `yuimorph.request.send` / `.accept` / `.deny` | Trading | everyone |
| `yuimorph.forced.bypass` | Ignore forced disguise | op |
| `yuimorph.admin` | Admin commands | op |

## Configuration

Key options in `plugins/YuiMorph/config.yml`: `unlock-on-kill`, `banned-disguises` (supports `minecraft:@all` / `player:@all`), `disabled-worlds`, `bounding-box.*`, `health-scale.mode`, `mob-aggro.*`, `restore-on-join`, `unmorph-on-death`, `forced-disguise`, `client-mod.enabled`. Client settings live in `config/yuimorph/settings.json`.

## Migrating from FeatherMorph

Keep the `plugins/FeatherMorph/` data folder (the FeatherMorph jar can be removed), start YuiMorph, then run in console:

```text
ym admin import feathermorph           # dry run
ym admin import feathermorph confirm   # import
```

Imports unlocked disguises and the disguise each player had when logging out; common properties are converted, player skins are not. Data is merged, not overwritten, so it is safe to run again and works for online players. FeatherMorph data is only read, and `plugins/YuiMorph/playerdata` is backed up first.

## FAQ

- **Is the client mod required?** No. It only adds self-view rendering, the selector, keybinds and the HUD.
- **Does it work on Paper?** It should, but it has only been tested on Folia 26.3. Please open an issue if something breaks.
- **How do I turn off mob aggro?** Set `mob-aggro.enabled: false` and run `/ym admin reload`.
- **How do I block one disguise?** `/ym admin ban minecraft:warden`, or set `yuimorph.morph.as.minecraft.warden` to false.
- **Languages?** Server messages are currently Simplified Chinese only; the client mod has English and Chinese.

## Building

JDK 25 required. `./gradlew build` builds all modules and runs tests. After a Minecraft update, regenerate entity tables with `python3 scripts/gen-entity-layouts.py <server jar>` and run `./gradlew :plugin:test`.

## License

[MIT](LICENSE) © Yui. Feature design inspired by [FeatherMorph](https://github.com/NiFeather/FeatherMorph).
