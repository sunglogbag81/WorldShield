# WorldShield

WorldGuard-like lightweight Paper/Spigot protection plugin.

## Features

- Global flags apply to all worlds: overworld, nether and end.
- Region flags override global flags.
- Wooden axe region selection, similar to WorldEdit.
- Region YAML files with per-region title/subtitle.
- Flags:
  - `pvp`
  - `explosion-block-damage`
  - `block-place`
  - `block-break`
  - `keep-inventory`

## Commands

```text
/ws help
/ws wand
/ws pos1
/ws pos2
/ws region create <name>
/ws region delete <name>
/ws region list [world]
/ws region info <name> [world]
/ws flag global <flag> <true|false>
/ws flag region <name> <flag> <true|false|unset> [world]
/ws title <name> <title|subtitle> <text...> [--world <world>]
/ws reload
```

## Region files

Regions are saved in:

```text
plugins/WorldShield/regions/<world>/<region>.yml
```

Example:

```yaml
world: world
name: spawn
priority: 0
min: {x: -10, y: -64, z: -10}
max: {x: 10, y: 319, z: 10}
flags:
  pvp: false
  explosion-block-damage: false
  block-place: false
  block-break: false
  keep-inventory: true
title:
  enabled: true
  title: '&aSpawn'
  subtitle: '&7Welcome!'
  fade-in: 10
  stay: 40
  fade-out: 10
```
