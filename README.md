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
  - `mob-spawning`
  - `item-drop`
  - `item-pickup`
  - `enderpearl`

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
/ws region setspawn <name> [world]
/ws gui <global|region> [world]
/ws flag global <flag> <true|false>
/ws flag region <name> <flag> <true|false|unset> [world]
/ws title <name> <title|subtitle> <text...> [--world <world>]
/ws combat <name> exit-delay <seconds> [world]
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
  mob-spawning: false
  item-drop: false
  item-pickup: false
  enderpearl: false
title:
  enabled: true
  title: '&c결투장에 입장했습니다.'
  subtitle: '&7이 구역은 PVP가 허용되며, 인벤세이브가 적용됩니다.'
  fade-in: 10
  stay: 40
  fade-out: 10
combat:
  exit-delay-seconds: 10
spawn:
  x: 0.5
  y: 64.0
  z: 0.5
  yaw: 0.0
  pitch: 0.0
```

`combat.exit-delay-seconds`가 0보다 크면 해당 구역 안에서 PVP 데미지를 입거나 준 뒤 지정된 초가 지나야 구역 밖으로 나갈 수 있습니다.

`/ws region setspawn <name>`은 플레이어의 개인/침대 스폰을 바꾸지 않습니다. 해당 리전 안에서 죽었거나, 해당 리전 안에서 로그아웃한 플레이어가 다시 접속할 때만 리전 spawn으로 보냅니다.

`/ws gui global` 또는 `/ws gui <region>`으로 플래그를 GUI에서 정리할 수 있습니다. 구역 GUI에서 우클릭하면 해당 플래그를 `unset`으로 되돌려 전체 설정을 상속합니다.

`/ws reload`는 `config.yml`과 `regions/<world>/<region>.yml` 변경사항을 서버가 켜진 상태에서 다시 불러옵니다.
