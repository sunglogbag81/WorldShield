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
  - `mob-target`
  - `mob-damage`
  - `mob-entry`
  - `equipment-durability`
  - `waterlogging`

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
/ws title <name> spectator <true|false> [world]
/ws combat <name> exit-delay <seconds> [world]
/자동차단
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
  mob-target: false
  mob-damage: false
  mob-entry: false
  equipment-durability: false
  waterlogging: false
title:
  enabled: true
  spectator: true
  title: '&c결투장에 입장했습니다.'
  subtitle: '&7이 구역은 PVP가 허용되며, 인벤세이브가 적용됩니다.'
  fade-in: 10
  stay: 40
  fade-out: 10
combat:
  exit-delay-seconds: 10
spawn:
  world: world
  x: 0.5
  y: 64.0
  z: 0.5
  yaw: 0.0
  pitch: 0.0
```

`combat.exit-delay-seconds`가 0보다 크면 해당 구역 안에서 PVP 데미지를 입거나 준 뒤 지정된 초가 지나야 구역 밖으로 나갈 수 있습니다.

`/ws region setspawn <name>`은 리전 밖에서도 실행할 수 있으며, 현재 플레이어 위치와 월드를 리전 spawn으로 저장합니다. 플레이어의 개인/침대 스폰을 바꾸지 않습니다. 해당 리전 안에서 죽었거나, 해당 리전 안에서 로그아웃한 플레이어가 다시 접속할 때만 리전 spawn으로 보냅니다.

`mob-entry: false`는 외부 몹이 해당 리전으로 들어오는 것을 막습니다. `mob-target: false`인 리전에 플레이어가 들어오면, 밖에서 이미 끌고 온 몹 어그로도 자동으로 해제됩니다.

`equipment-durability: false`는 해당 위치의 플레이어 장비/도구 내구도 감소를 막습니다.

`block-place`는 일반 블록 설치뿐 아니라 물/용암 설치도 막습니다. `block-break`는 일반 블록 파괴뿐 아니라 물/용암을 양동이로 퍼내는 동작도 막습니다. `waterlogging: false`는 반블럭 같은 waterlogged 블록에 물을 채우거나 빼는 동작을 별도로 막습니다.

`/ws title <name> spectator <true|false> [world]`로 관전모드 플레이어가 리전에 들어갈 때 입장 타이틀을 볼지 설정할 수 있습니다.

리전 경계 안팎을 넘는 직접/투사체 데미지는 항상 차단됩니다. 즉 내부에서 외부로, 외부에서 내부로, 서로 다른 리전 사이로 데미지를 줄 수 없습니다.

`/자동차단`은 실행자의 시선 앞쪽 60블록, 반경 10블록 안에 있는 불을 찾고 최근 5분 화재 로그에서 근원 플레이어를 추적합니다. 근원이 확인된 플레이어는 전원 즉시 차단되며, 접속 중이면 `알고리즘에 의해 자동 차단되었습니다. 관리자가 로그를 확인중입니다. 잠시만 기다려주세요.` 메시지로 추방됩니다. `worldshield.autoban.exempt` 권한이 있으면 자동차단 대상에서 제외됩니다.

`/ws gui global` 또는 `/ws gui <region>`으로 플래그를 GUI에서 정리할 수 있습니다. 구역 GUI에서 우클릭하면 해당 플래그를 `unset`으로 되돌려 전체 설정을 상속합니다.

`/ws reload`는 `config.yml`과 `regions/<world>/<region>.yml` 변경사항을 서버가 켜진 상태에서 다시 불러옵니다.
