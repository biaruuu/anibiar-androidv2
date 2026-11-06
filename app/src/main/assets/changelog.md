## 32 | More in-app servers: Kaze, Tsuki | 2026-11-06
- Two more servers that run inside the app, Kaze (sub and dub, several servers) and Tsuki (HLS with English subtitles)
- The in-app servers now have their own names: Kaze, Tsuki, Sakura and Momiji

## 31 | Streams that work without the server | 2026-11-06
- Episodes and streams from AnimeDunya and AnimeGG now load inside the app itself, so you can still watch when the AniBiar server is slow, out of quota or down
- These appear as extra servers in the Server menu

## 30 | Backup server for "HTTP 402" | 2026-11-06
- When the main streaming server says it is out of quota (HTTP 402), rate limited or down, the app switches to the backup server by itself for 10 minutes

## 29 | Fix guest captions not turning back on | 2026-11-06
- When the host turned captions off and on again, guests stayed off; they now follow the host both ways

## 28 | Captions stay on for guests | 2026-11-05
- Guests' captions now turn off only when the host turns them off
- In a watch party, guests always use the host's caption style, so size and look match for everyone

## 27 | Guests always get the host's captions | 2026-11-05
- If a friend's server has no captions, they now show the host's captions automatically, so everyone sees the same subtitles

## 26 | Crunchyroll caption size, new invite card | 2026-11-05
- Captions are sized like Crunchyroll's, by the height of the picture, so they are bigger and stay the same size when the controls show
- The invite you get from a host is now a card with the anime cover, who invited you, and Join / Not now buttons

## 25 | Watch together restyle, caption size in fullscreen | 2026-11-05
- Captions no longer get too big in fullscreen
- Watch together button, call screen, call bar and invite list restyled to match the watch page cards (dark panels, soft borders, lavender accents)

## 24 | Exact Crunchyroll caption outline | 2026-11-05
- Caption outline and drop shadow now follow the Crunchyroll subtitle CSS: thicker solid black outline and a soft shadow

## 23 | Bigger captions, host-only caption button | 2026-11-05
- Captions a bit bigger
- The captions button on the top bar is host-only in a watch party

## 22 | One caption size, Crunchyroll look | 2026-11-05
- Removed the Small / Medium / Large caption sizes; captions use one default size that scales with the player
- Default caption style matches Crunchyroll: Trebuchet, white with a black outline

## 21 | Host settings shown live to everyone | 2026-11-05
- When the host opens the settings menu or the volume slider, everyone in the room sees it open on their player too

## 20 | Captions for friends who join late | 2026-11-05
- A friend joining mid-episode now switches to the host's server and picks the matching English captions
- Captions that fail to load retry on their own

## 19 | Call volume, invites, captions | 2026-11-05
- Volume buttons now control the video and your friends' voices together, so the call no longer makes the video quiet
- Invite again works: it shows "Invite sent", and the invited friend gets the banner even after leaving a room
- Subtitles a little smaller and fit the player size better

## 18 | Watch party sync fixes | 2026-11-05
- Guests now catch up automatically if they missed an episode or anime change, so nobody is stuck loading
- Joining the call asks whether you want your mic on or muted; the "Voice connected" label is gone
- Only the host can use the player; tapping it as a guest shows nothing, and the host's controls show on everyone's screen
- Subtitles a little bigger

## 17 | Continue watching fit, server restored | 2026-11-05
- Continue watching prompt resized so it fits the player
- Streaming server settings returned to the previous version

## 16 | Faster episodes, bigger Continue watching | 2026-11-05
- Episode lists and streams are cached, and the next episode is loaded ahead of time
- Continue watching: bigger text, buttons side by side, and the frame where you stopped shows behind it

## 15 | Dark glass player | 2026-11-05
- Player buttons use soft dark glass (like YouTube) so icons stay visible on bright scenes
- Volume slider only opens when you tap the speaker (tap again to mute)
- "Continue watching?" fits small players and lets the video show through
- Watch page: bookmark icon is back, extra playback menu removed (switches are on the player)

## 14 | Smaller player, Crunchyroll captions | 2026-11-05
- Player controls are smaller and fit better on phones; settings menu is more compact
- Captions sit right above the seek slider while controls are showing, and drop to the bottom when they hide
- Cleaner Crunchyroll-style caption text (regular Trebuchet, thin outline)
- Removed Autoplay / Auto skip / Auto next from settings (they're in the top bar)
- "Continue watching?" prompt is smaller and hides the controls behind it

## 13 | New watch page like the web app | 2026-11-05
- Episode sheet redesigned: thumbnail cards, sort / hide-spoilers / grid buttons, dub and sub marks, episode ranges
- Watch page: Episode heading, Sub / Provider / options row, Seasons, Related and Recommendations cards in the new web style
- Continue watching prompt: "Continue from 0:05" or start from the beginning
- Provider picker lists every source with its episode count
- Player: glass top bar with Autoplay / Auto skip / Auto next switches, stronger glass look like the web player

## 12 | Faster streams, mic before joining | 2026-11-05
- Joining or starting a watch party now asks for the microphone first; once you're in you can mute yourself any time
- Episodes and streams now load through the Kiro-WebApp gateway: several servers are asked at once and the first one to answer plays, with the old server as backup
- Audio picker shows how many episodes each of Sub and Dub has

## 11 | Captions yours, reactions removed, voice fix | 2026-11-05
- Caption language, size and style can now be changed by everyone in a watch party and apply right away for you (the host's settings are the starting point)
- Removed the emoji reactions
- Voice: people now announce when they're ready so call setup can't be missed, plus more relay servers for mobile data
- Landscape: the video no longer takes the whole screen in a room

## 10 | Reactions and bolder captions | 2026-11-05
- Watch together: tap an emoji under the video and everyone in the room sees it float up over the video
- Captions are bolder and easier to read, still in the Crunchyroll style

## 9 | English captions by default | 2026-11-05
- Captions now always start on English; if a video has no English track they stay off instead of picking another language
- Subtitle text is thinner and cleaner, closer to Crunchyroll

## 8 | Captions: Crunchyroll look by default | 2026-11-05
- Captions now default to Small, thinner Trebuchet text with a light outline, sitting lower on the screen like Crunchyroll
- The "Crunchyroll" caption style is now just "Default"; Dark box is still available

## 7 | Crunchyroll captions, mic fixes | 2026-11-05
- New caption look: Trebuchet MS with a black outline and soft shadow, like Crunchyroll (Dark box still available in caption style); italics for thoughts and narration
- Mic fix: call setup messages are now handled in order (they could be dropped before), the call keeps the mic alive in the background, and a working relay server helps on mobile data
- Each friend's tile shows whether their voice is connected, connecting or failed

## 6 | Captions, host-mirrored player, wait-for-everyone | 2026-11-05
- Captions now load reliably (retries, remembered per episode) and reload after you leave and come back
- Everything the host does on the player shows for guests: captions on/off and language, caption size and style, speed, volume boost, seeking, play and pause
- If a guest's video stalls, the host's video waits for them and carries on when they're back, so everyone stays on the same moment

## 5 | Watch party sync & host controls | 2026-11-04
- Host controls fullscreen for the whole room; guests can't toggle it
- Tighter sync: host position shared every second, server-clock aligned, smoother catch-up (no more drifting behind)
- Removed the "You control playback" / Live banner from the room screen
- Added this changelog: you'll see what's new after every update, and before you install one

## 4 | Persistent party, in-call bar, HD | 2026-11-04
- Watch party and voice call stay alive when you press back or browse the app; an "In call" bar lets you jump back in
- Leave the call only with the end-call button (host leaving ends the room)
- Video starts at high quality but still adapts; playback errors retry quietly, then try the next server
- Clearer voice (96 kbps, WebRTC echo and noise removal)

## 3 | Watch together | 2026-11-04
- Rooms with friend invites (live banner + Inbox), voice call, Discord-style tiles with speaking rings and mute badges
- Join/leave sounds, host-only playback, guests can request an anime or episode
- In-app updater: installs over your current app, no more uninstalling

## 2 | Watch page | 2026-11-03
- Native player with Sub/Dub, servers, captions, skip intro/outro, quality, speed, volume boost
- Bookmark on every anime card, live favorites counter

## 1 | Profiles & friends | 2026-11-02
- Edit profile, online status, favorites with privacy, friend system fixes

## 33 | Faster episodes, more servers | 2026-11-06
- Episode list now loads in stages: it appears as soon as the first server answers, and a saved copy shows instantly on repeat visits
- Two new in-app servers: Hoshi (sub) and Yuki (sub and dub)
- Shorter provider timeouts and one shared anime lookup, so slow servers no longer hold up the list

## 34 | Always fresh episodes | 2026-11-06
- Episode lists are re-fetched live every time; the saved copy only shows while the fresh list loads
- Newly aired episodes now appear within about a minute instead of waiting for an app restart

## 35 | Crash protection | 2026-11-06
- Errors on background threads (network, player loading) no longer close the app
- If Kiro does close, the next launch shows what went wrong with a Copy report button
- Episode list ignores duplicate episode numbers from different servers

## 36 | Episode details and error reports | 2026-11-06
- Episode titles, thumbnails and air dates are filled in live (ani.zip and MyAnimeList) when a streaming server leaves them blank
- When an episode or server fails, a Copy report button gives you the exact error to send

## 37 | Lighter episode list, crash detection | 2026-11-06
- The episode list builds only the first few rows and loads more as you scroll
- Kiro now detects out-of-memory kills, freezes and native crashes (not just errors) and shows a report on next launch
- More memory allowed for long series like One Piece
