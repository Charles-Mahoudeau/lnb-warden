# File verification

How LNB Warden tells LNB which mods, resource packs and shader pack a player runs, and what the server does with
LNB's answer.

The client hashes its files and lists them to the Minecraft server. The server forwards the list to LNB's API, which
scores each file and each player and answers a **verdict**. The server applies it: nothing, a message, or a
disconnection. The mod holds no policy: which files are allowed, how trust moves and what each score leads to are
decided on the website, and documented in its `docs/anticheat.md`.

This stops casual cheating: a player who drops a cheat mod or an x-ray pack in their instance. It doesn't stop someone
who modifies their client to lie about its files. The server's rules must tell players that the hashes of their files
are read and that their use is recorded.

## Overview

| Term          | What it is                                                                                                   |
|---------------|--------------------------------------------------------------------------------------------------------------|
| Reported file | One mod jar, resource pack or shader pack of the client: its SHA-512, CurseForge fingerprint and declared metadata. |
| Report        | The list of a client's reported files, sent to LNB's API under the player's Warden session.                  |
| Verdict       | LNB's answer: one action, and the reasons (rule and file) behind it.                                         |
| Connection ID | 16 random bytes the server draws for each connection, mixed into every challenge answer.                     |
| Challenge     | A nonce the server sends at random times; the client answers with a digest of its files.                     |

Only authenticated players are checked: a report belongs to a Warden session, and guests have none. Nothing here runs
in singleplayer, on LAN games, or when `auth.mode` is `off`.

## What is reported

For each file: `kind`, `sha512`, `fingerprint`, `filename`, and when known `modId`, `name`, `version`, `loader` and
`facts`.

| Kind           | Files                                                                                                            |
|----------------|------------------------------------------------------------------------------------------------------------------|
| `mod`          | Every top-level file NeoForge loaded mods from (`LoadingModList`), and loader plugins sitting in the mods folder. |
| `client`       | The files of `minecraft`, `neoforge` and LNB Warden itself.                                                       |
| `resourcepack` | The selected resource packs that are a zip or a folder of `resourcepacks/`. Built-in and mod-provided packs aren't. |
| `shader`       | The pack Iris or Oculus has enabled, read from `config/iris.properties` or `config/oculus.properties`.           |

- Jars nested in another jar aren't reported: the outer jar's hash already covers them. Their mod IDs are attached to
  the outer file, for the channel check.
- A folder is hashed as the SHA-512 of its sorted relative paths and contents, without a fingerprint.
- The fingerprint is CurseForge's: MurmurHash2 with seed 1 over the file without its bytes 9, 10, 13 and 32. LNB's API
  uses it to look the file up on CurseForge, and the SHA-512 to look it up on Modrinth.
- In a development run, mods are folders of classes and aren't reported. A client with nothing to report is let in.

`facts` are set for resource packs only (`PackInspector`):

| Fact              | True when                                                                                                         |
|-------------------|-------------------------------------------------------------------------------------------------------------------|
| `coreShaders`     | The pack has any file under `assets/minecraft/shaders/`.                                                          |
| `alteredTextures` | A terrain or ore block texture (stone, deepslate, dirt, `*_ore`…) has a non-opaque pixel, or can't be read. Overlays count. |

Hashes are cached by path, size and modification time, and the mods are hashed on a background thread at startup, so
that the list is ready when the server asks.

## Payloads

`PROTOCOL_VERSION` is `4`. All four work in the configuration and in the play phase.

| Payload                             | Direction | Content                                    |
|-------------------------------------|-----------|--------------------------------------------|
| `lnbwarden:files_request`           | S→C       | `connectionId` (16 bytes)                  |
| `lnbwarden:files_report`            | C→S       | `files[]`, `last`                          |
| `lnbwarden:files_challenge`         | S→C       | `nonce` (32 bytes)                         |
| `lnbwarden:files_challenge_response` | C→S       | `digest` (32 bytes)                        |

A serverbound payload is capped at 32 KiB, so a report is split in parts of at most 24,000 bytes; `last` marks the
final one. The server takes at most 1,000 files and drops duplicates.

The digest is `SHA-256(nonce ‖ connectionId ‖ the SHA-512 hashes, sorted)`, computed the same way on both sides
(`FileDigest`).

Like for join tickets, the client only answers the host of `serverAddress` (`LnbServer.isLnb`): another server can't
ask a player for their file list.

## Joining

`lnbwarden:files` is a configuration task that runs right after `lnbwarden:auth`:

1. Guests, and clients without the channel, skip it.
2. The server sends `files_request` and waits up to 60 seconds for the whole list. Hashing a large modpack for the
   first time takes a while.
3. It posts the list to `POST /api/warden/server/anticheat/reports` and keeps the player in the configuration phase
   until the API answered.
4. It runs the channel check, then applies the verdict.

| What happens                                    | Result                                                                      |
|-------------------------------------------------|-----------------------------------------------------------------------------|
| The client doesn't list its files in time       | `challengeMissed` event, disconnected (`anticheat.timed_out`).              |
| The API is unreachable, fails, or answers 503   | Let in. Logged once per outage.                                             |
| Verdict `allow` or `flag`                       | Let in.                                                                     |
| Verdict `warn`                                  | Let in, and told in chat which packs staff will review.                     |
| Verdict `hold`, `refuse`, `kick` or `ban`       | Disconnected with the files named. See [Verdicts](#verdicts).               |

A player refused here was already allowed by `lnbwarden:auth`, so the task also ends their Warden session
(`sessions/end`), which would otherwise linger until the API times it out.

## Verdicts

The API decides, the server applies (`Verdicts`). Actions, from the least to the most severe: `allow`, `flag`,
`warn`, `hold`, `refuse`, `kick`, `ban`. The reasons that carry the verdict's action are the ones shown.

| Action          | What the server does                                                                                         |
|-----------------|--------------------------------------------------------------------------------------------------------------|
| `allow`, `flag` | Nothing the player sees.                                                                                     |
| `warn`          | One chat message naming the packs. Not repeated while the same packs are warned about.                       |
| `hold`          | Disconnects: staff has to review these files (or the account, for `playerHeld` and `manualHold`).            |
| `refuse`        | Disconnects: these files aren't allowed.                                                                     |
| `kick`          | Disconnects: these files are banned (or the account is no longer trusted, for `playerZero`).                 |
| `ban`           | Bans through `POST /api/warden/server/players/{uuid}/bans`, permanently, with `anticheat.banReason`, then disconnects. |

After a disconnection the server posts what it did to `POST /api/warden/server/anticheat/actions`. Messages name at
most 8 files. An action the server doesn't know lets the player in, with a warning in the log.

While the website's anticheat is in `observe` mode the verdict is always `allow`, with the action it would have been
in `wouldBe`: the server only logs it, once per change.

A verdict can change while the player is online: a lookup ends, staff bans a file, trust drops. It then comes

- in the answer to a new report or to an event,
- as an `anticheatVerdict` event on the event stream,
- or, after the stream was down, from `GET /api/warden/server/anticheat/sessions/{id}`, asked for each online player
  when the stream reconnects.

All three are applied the same way. `anticheatAlert` events are for staff, who handle them on the website: the server
logs them and tells online operators that there is one.

## While playing

### Challenges

Every 2 to 5 minutes, at a random time, the server sends each checked player a `files_challenge`. The client answers
within 10 seconds with the digest of the list it last sent.

| Answer                         | Result                                                                                         |
|--------------------------------|------------------------------------------------------------------------------------------------|
| Right                          | Nothing.                                                                                       |
| None in time                   | `challengeMissed` event.                                                                       |
| Wrong                          | The server sends `files_request` again (30 seconds to answer), then a second challenge.        |
| Wrong again after a fresh list | `challengeWrong` event.                                                                        |

The first wrong answer isn't held against the player, because their packs may just have changed.

`-Dlnbwarden.anticheat.challengeMinSeconds` and `-Dlnbwarden.anticheat.challengeMaxSeconds` shorten the interval on
a test server.

### Changes of packs

Once a second the client checks whether its selected resource packs or its shader pack changed, and if so sends a new
list on its own. The server takes at most one unasked list every 5 seconds.

When a new list differs from the last one, the server posts a new report. Packs can be changed from the options
without a penalty. Mods can't be loaded or unloaded while the game runs: when the `mod` or `client` files differ, the
server also sends a `modSetChanged` event with the hashes added and removed.

### Channel check

A client can't use a mod's network channels without the mod. At join, the server takes the namespaces of the channels
NeoForge negotiated for the connection, keeps those that are the ID of a mod loaded on the server, and removes
`minecraft`, `neoforge`, `c`, `lnbwarden` and every mod ID the client reported. What is left is sent as one
`channelMismatch` event.

Only the server's own mod IDs are looked at, because a channel's namespace isn't always its mod's ID. The check reads
NeoForge's internal `ChannelAttributes`; `anticheat.channelCheck` turns it off.

## Server settings

Added to `ServerConfig` (`config/lnbwarden-server.toml`):

| Key                      | Default                                 | Use                                                               |
|--------------------------|-----------------------------------------|-------------------------------------------------------------------|
| `anticheat.enabled`      | `true`                                  | Whether players' files are checked. Needs `auth.mode` not `off`.  |
| `anticheat.channelCheck` | `true`                                  | Whether the channel check runs at join.                           |
| `anticheat.banReason`    | `Fichier interdit par l'anticheat : %s` | Reason of the bans issued, shown to the player. `%s` is the file. |

`/lnbwarden status` shows whether file verification is on and how many players are checked.

## Failing open

LNB's API is never in the way of players. When a report or an event can't be sent (unreachable, 5xx, 4xx) the player
plays, and the server logs it once until the API answers again. A 503 with `problem: disabled` means the anticheat is
turned off on the website: it is logged as information and doesn't count against the API's health.

The one thing that doesn't fail open is a client that never lists its files at join: letting it in would let anyone
skip the check by staying silent.

## Threading

- The client hashes and reads packs on a background thread. Listing the selected packs happens on the client thread.
- On the server, everything after an API call completes runs on the server thread, like `AuthTask`.

## Limits

- A modified client can report whatever it wants. The challenges and the channel check only make lying less trivial.
- X-ray packs built on block models or blockstates, without transparent textures or core shaders, aren't detected by
  `facts`. They are still hashed, so staff can ban them by file.
- Only the transparency of textures is looked at, not what they depict.
- Files a mod loads from elsewhere than the mods, resource packs and shader packs folders aren't seen.
