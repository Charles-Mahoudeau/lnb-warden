# ClearLag

Removes dropped items, experience orbs and hostile mobs on a schedule, and keeps each chunk under a limit of entities per
type. It has nothing to do with LNB's API: it is configured in `config/lnbwarden-clearlag.toml` and only runs on a
dedicated server. The file reloads by itself when saved.

## Schedule

A clear happens every `schedule.intervalSeconds`. While the server's average tick takes longer than `schedule.lagMspt`
milliseconds (50 is the limit of 20 TPS, 0 turns the mode off), the interval is `schedule.lagIntervalSeconds` instead. If
that shortens a countdown that is already past its first warning, the countdown restarts at the first warning mark, so a
clear is never a surprise. If the interval grows back, the warnings come again.

Players are warned in chat at each `schedule.warningSeconds`, see a countdown in the action bar during the last
`schedule.actionBarSeconds`, and are told what was removed when `schedule.announceResult` is on.

## What is removed

`targets.items`, `targets.experienceOrbs` and `targets.hostileMobs`. Passive mobs, item frames and paintings are never cleared. A chunk limit
removes any type you list, item frames and paintings included.

Spared (`protection.*`):

| Rule                               | Spares                                                                          |
| ---------------------------------- | ------------------------------------------------------------------------------- |
| `minItemAgeSeconds`                | Items younger than this: a fresh drop or a death drop.                          |
| `keepItems`, `onlyItems`           | Items by id or `#tag`. `onlyItems`, when not empty, limits the clear to them.   |
| `keepEntityTypes`                  | Entity types by id or `#tag`, bosses by default.                                |
| `keepNamed`, `keepTamed`, `keepLeashed`, `keepPersistent` | Mobs with a name tag, an owner, a leash, or that don't despawn. |
| `keepNearPlayersBlocks`            | Everything this close to a player who isn't a spectator.                        |

## Chunk limits

Every `chunkLimits.checkSeconds`, a chunk that holds more entities of a type than its limit loses the oldest until it
fits. `chunkLimits.limits` lists `type=maximum` pairs (any non-player entity type, e.g. `minecraft:zombie=30`), and the
protection rules apply: spared entities still count, but are never removed.

## Commands

All need permission level 2.

- `/lnbwarden clearlag run`: clears now, without touching the countdown.
- `/lnbwarden clearlag status`: time to the next clear, whether the lag interval is in use, the average tick, and the last result.
- `/lnbwarden clearlag reload`: restarts the countdown. The config itself needs no reload.
