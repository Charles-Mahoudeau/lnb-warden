# Playtime limits

LNB's site decides every limit, window and week. The mod only **shows** and **obeys** what the site sends, and counts nothing itself.
While the site only observes (`playtime.enforced` is false), the mod shows and does nothing.

## What the server handles

| From LNB                                        | What the mod does                                                                                           |
| ----------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Login denied, `playtimeLimitReached`            | Disconnect screen with the date the player can join again (`windowEndsAt`, else `resetsAt`), in Paris time. |
| Login `allowed` with `playtime`                 | Keeps the state per player, for `/playtime`.                                                                |
| `playtimeWarning` (`allowance`)                 | Title and chat message with the time left.                                                                  |
| `playtimeWarning` (`windowStart`)               | Chat message: no time left, the player is disconnected when the window opens.                               |
| `playtimeGrace`                                 | Chat message with the end of the grace period. No kick.                                                     |
| `playtimeUpdate`, and `playtime` in a heartbeat | Replaces the state. It can cancel a countdown (the player is told) and clears everything when not enforced. |
| `kick` with the reason `playtimeLimit`          | Disconnects with the same screen as the login denial.                                                       |

Everything is per Minecraft UUID.

## Fallback

Each warning and update starts a local countdown from the moment it was received. It only kicks when the **event stream is
down**: with the stream up, the site's own kick comes within about 6 seconds, and an event always replaces the local state.
The heartbeat (every 60 seconds) also carries the state of every session, so an outage ends at the next one at the latest.

## Sessions

Open sessions are listed in `lnbwarden-sessions.txt` in the game directory. They are ended (`quit`) on server stop, and those
a crash left open are ended on the next start, so the site doesn't wait for the heartbeat timeout.

## Commands

- `/playtime`: what the player has left this week, the end of the window that counts, or the grace period. It says nothing about schedules or exemptions.
- `/lnbwarden info <player>` (staff): the LNB account behind a Minecraft account, its state (`unlimited`, `free`, `counting`, `grace`, `blocked`) and the time used, left and allowed this week.
