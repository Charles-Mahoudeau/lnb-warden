# Changelog

LNB's site holds the changelog posts. The mod **shows** them and tells the site when a player has read one.
It stores nothing.

## Where it shows

| Where                              | What                                                                                                  |
| ---------------------------------- | ----------------------------------------------------------------------------------------------------- |
| Login on a server | The newest post the player hasn't opened opens by itself, with a chime and a clickable chat line, once they are in the world. Closing it goes back to the game. Singleplayer never opens it by itself. |
| Title screen | A toast when the newest post is under 14 days old and not the one last opened. It isn't clickable, and it is checked again every 2 minutes while the screen stays open. |
| Pause menu | A Changelog button beside Account (they replace Feedback and Bug report), with a red badge while the latest post is unread. |
| Title screen button | A small newspaper button right of Account, with the same red badge. |
| `/changelog`                       | Opens the same screen. Client-side only, so it works on any server and in singleplayer.               |

The screen lists posts, newest first (10 at a time, "Load older posts"). A post is drawn from the site's `blocks`:
headings, paragraphs, quotes, lists, code, tables and rules. Links ask for confirmation before opening. Images aren't
in the blocks yet (the site sends their alt text), so they show as text.

## Who calls what

The server secret never leaves the server, so the two halves differ:

- **Server**, on join (dedicated server, API configured, auth mode not `OFF`, and the client has the mod's channel):
  `GET /api/changelog/server/unseen?player=<uuid>&lang=<fr|en>`, asynchronous with a 5 second timeout and no retry.
  A post is sent to the client as a `changelog_notice` payload, and the client opens it. Nothing is retried and nothing blocks joining.
- **Client**: the public routes `GET /api/changelog/public/posts` and `/public/posts/:id`, with no secret, so the
  title screen works too. The language is the game's (`fr*` is French, anything else English).
- **Seen**: opening the offered post sends `changelog_seen` to the server, which calls
  `POST /api/changelog/server/posts/<id>/seen`. The server only accepts the id it offered that player.

The network protocol version is 5.
