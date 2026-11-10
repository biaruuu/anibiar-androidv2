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

## 38 | Fix freezing on long series | 2026-11-06
- Episode and stream lookups now run off the main thread, so opening One Piece no longer freezes the app
- Faster parsing of very long episode lists

## 39 | New captions, playback fixes | 2026-11-06
- New caption style: clean Inter on a rounded dark box, no outline or shadow, scales with the player (Default and Solid box)
- The "closed unexpectedly" box now only appears after a real crash or freeze
- Allowed servers that stream over plain http, and fixed a link-decoding bug for the Sakura server
- Player errors show the real cause (HTTP code and host) and have a Copy report button

## 40 | Nami server and outlined captions | 2026-11-06
- New in-app server Nami (AniKoto): sub and dub, picks the right show by MyAnimeList id
- Captions now default to a bold outlined style with no shadow; Dark box is the other option

## 41 | Mizu server and faster stream start | 2026-11-06
- New in-app server Mizu (Ani.pm): sub and dub, matched by exact AniList / MyAnimeList id
- "Finding the best stream" now starts playing as soon as the first server has a stream instead of waiting for the slowest one

## 42 | Mizu playback headers | 2026-11-06
- Mizu streams now send the player site's cookie and headers when loading the video

## 43 | Community comments, best-quality server | 2026-11-06
- New Community chat on every anime page and episode: Episode, Anime and All episodes tabs, replies, emoji reactions, live updates
- The player now reads each server's available resolutions and starts on the one with the highest quality
- Removed the Mizu (Ani.pm) server

## 44 | Dark profile restyle | 2026-11-06
- Profile redesigned: big bold name, @handle, Following / Followers / Friends counts, avatar on the right with your status as a speech bubble, pill buttons, deep dark background
- Removed the Community comments feature

## 45 | Banner profile and new tab bar | 2026-11-06
- Profile: full banner at the top with your name, handle and counts on it, avatar on the right, smaller text
- Glass buttons on the profile like the video player; pencil edits your profile, share and settings at the top; no more Edit/Share buttons or "Set a status" bubble (status is in Edit profile)
- New flat dark bottom tab bar (icon over label), also shown when you open someone's profile

## 46 | Glass tab bar, matching edit profile and settings | 2026-11-07
- Profile: sharper banner, smaller top buttons, no back arrow on your own profile
- Bottom tab bar now uses the same glass buttons as the profile top bar
- Edit profile and Settings restyled to match (dark page, glass buttons, rounded dark cards); the tab bar hides on Edit profile and follower lists

## 47 | Flat gray tab bar, clean avatar | 2026-11-07
- Bottom bar is now a flat gray bar with plain icons and labels (no round buttons), easy to see on dark
- Profile avatar has no white ring and loads at full resolution so it is sharper; banner is brighter and only fades to black near the bottom

## 48 | Inbox, messages and streaks | 2026-11-07
- New Inbox tab in the bottom bar with an unread badge: Activity entry on top, then your chats
- Message people you follow (or who follow you) from their profile, a follower list or the Inbox; seen/sent marks and live updates
- Streak: message each other every day and a pulsing flame with the day count appears on the chat
- Suggested accounts strip on other profiles (arrow button) and in the Inbox
- Followers / Following / Friends lists restyled as dark name cards with glass back button
- Other profiles now use the same page colour and icon buttons as your own

## 49 | Name cards and message fix | 2026-11-07
- Search name cards redesigned: compact dark card with the banner fading behind, avatar, name, bio, follower count and a Follow button
- Fixed "permission denied" when sending a message

## 50 | Liquid glass tab bar and chat | 2026-11-07
- Bottom bar is now a floating liquid-glass pill with a glass capsule on the selected tab
- Chat redesigned with rounded gradient bubbles, grouped messages with tails, avatars, time stamps, glass input bar and glass streak pill

## 51 | Chat header, pin and smaller bubbles | 2026-11-07
- Chat header: thin back chevron, star to pin the chat to the top of the Inbox, and a three-dot menu
- Smaller bubbles, smaller + button and message bar

## 52 | Flat iMessage bubbles | 2026-11-07
- Chat bubbles are flat solid colours with a small curled tail, like iMessage; header buttons get a glossier glass look

## 53 | Tails on every bubble, copy and unsend | 2026-11-07
- Every chat bubble now has the tail, not just the last one
- Bigger + button matching the message bar height
- Long-press a message to copy its text; long-press your own message to unsend it

## 54 | Tail on newest bubble, glass menus and cards | 2026-11-07
- Only the newest bubble in a run keeps the tail; older ones are plain rounded
- Smaller + button; long-press and three-dot menus restyled as glass popups
- Inbox, Profile, Settings and Edit profile cards now use the same glass surface

## 55 | One back button, no tab bar on Inbox, Profile, Settings | 2026-11-07
- Inbox, Profile and Settings drop the bottom bar and use a glass < button instead
- All round icon buttons share the same glass style as the chat

## 56 | Glass pills for grouped icons | 2026-11-07
- Profile top bar: back + edit share one glass pill, bell + share + settings share another; single icons stay round glass buttons

## 57 | App-wide iOS glass restyle | 2026-11-07
- Darker near-black pages, softer surfaces, rounder buttons and cards, glass card fill, tighter large text, rounder dialogs and menus across the whole app

## 58 | Glass buttons everywhere | 2026-11-07
- Details, Watch, Watch together, Search, Login and Follow buttons, chips and round icon buttons now use the same glass style as the chat; every full page gets the glass < button

## 59 | Glass chips, + buttons; header removed | 2026-11-07
- Home card badges are lighter, bolder glass pills; the + / bookmark buttons are glass
- Removed the Kiro header and search icon from Home and Details (search lives in the Search tab)
- Details page icons (bookmark, site links, card badges) restyled as glass

## 60 | Compact liquid-glass tab bar | 2026-11-07
- Tab bar is smaller and narrower; press and slide across it and a glass lens follows your finger and magnifies the icons

## 61 | Glass finish pass | 2026-11-07
- Search fields, login fields and buttons, call buttons and the Home page arrows now use the same glass style

## 62 | Dark glass hero chips, pager arrows, profile counts | 2026-11-07
- Home pager arrows (1/5) back to the plain icons; hero chips are dark liquid glass
- Profile Following / Followers / Friends counts no longer cut off

## 63 | TikTok-style Inbox list | 2026-11-07
- Inbox chats are a flat list (avatar, name, message and time, red unread badge) instead of cards
- New chat-bubble icon for the Inbox tab; Activity row uses a glass bell circle

## 64 | Swipe to reply, typing indicator, dark liquid menus | 2026-11-07
- Swipe a message sideways to reply; the reply shows quoted above the bubble
- Typing indicator: bouncing dots in a bubble when the other person is typing
- Long-press menu is dark glass with real icons (Reply, Copy, Unsend) that press like liquid
- Removed the empty header bar on Home

## 65 | Real-time chat, stickers, photos, bubble styles | 2026-11-07
- Messages and typing show up instantly (live channel with a polling backup)
- Reply bar is now flat dark like TikTok: "You: hi" with a thin bar and a round X, no glass, no purple
- Send photos, GIFs, your own stickers, or pick from the built-in sticker pack with the + button
- New bubble styles with little decorations on top; choose and save yours from + > Bubble
- Default bubble is blue; tap a photo to view it full screen

## 66 | Avatar decorations and namecard effects | 2026-11-07
- Removed stickers and chat bubble styles; the + button now just sends a photo
- New avatar decorations (Kitty, Thunder, Burst, Butterflies, Study buddy, Furious) that animate around your photo and show everywhere your avatar appears
- New namecard effects (Sparkles, Sakura, Embers, Snowfall, Aura) that animate on your name card and profile banner
- Pick both in Edit profile > Avatar decoration & effect, with a live preview
- Top Airing and Upcoming now look the same

## 67 | Rust core + profile save fix | 2026-11-08
- Added a Rust library (kiro_core) to the app; the subtitle parser and ranked follower search now run in Rust, with the old Kotlin code as automatic backup
- Settings > Rust core shows if it loaded on your phone
- Fixed "permission denied for table profiles" when saving an avatar decoration or effect

## 68 | Real animated decorations, Storm effect, lighter images | 2026-11-08
- Avatar decorations now play their own animation (no extra motion added); small avatars in lists show a still frame to stay smooth
- New Storm namecard effect (blue lightning)
- Avatars and Home row backdrops load at the size they are shown, which uses less memory and scrolls smoother

## 69 | More Rust: title matching and text cleanup | 2026-11-08
- Title matching for stream sources and HTML/description cleanup now run in Rust (with Kotlin backup)
- Regexes are compiled once instead of on every call, so Home descriptions and source searches do less work

## 70 | Soft-sub first streams, episode descriptions, providers | 2026-11-08
- Best-stream picking (done in Rust) now prefers soft subtitles, English first, so captions use Kiro's own caption style; hard-sub streams come last, then picture quality decides
- Episode descriptions now show in the episode list (from ani.zip summaries)
- Each episode row shows which providers have it; the Server menu shows s-sub / h-sub and the picture quality

## 71 | Quality first stream picking | 2026-11-08
- Best stream is now the highest picture quality first; among equal quality, soft subs (English first) come before hard subs
- Stream quality is read from the playlist in Rust

## 72 | Slow providers (Nami) no longer cut off | 2026-11-08
- Stream search now waits for every provider (up to 8 s after the first answer) instead of 1.2 s, so Nami's 1080p soft-sub streams are included; it stops early once a 1080p+ soft-sub stream is found
- Quality check waits up to 5 s per stream, and a stream whose quality can't be read is treated as HD instead of the worst
- Up to 10 server providers are asked per episode

## 73 | Fix "AniList HTTP 429" on episodes | 2026-11-08
- Provider title lookups now retry when AniList rate-limits, remember the result for 30 days, and fall back to ani.zip when AniList keeps refusing
- If a fresh episode list can't be loaded but a saved one exists, the saved list stays on screen instead of an error

## 74 | Fresher episodes and streams | 2026-11-08
- Retry now really asks the providers again instead of reusing the saved stream
- Airing shows re-check for new episodes every 3 minutes while you watch

## 75 | Watching upgrades | 2026-11-08
- If a stream fails, Kiro switches to the next best one that hasn't failed and tells you
- Caption size (4 sizes), 2 new caption styles (Yellow outline, Soft shadow) and a Caption timing fix (earlier/later) in the player menu; your caption look and last quality are remembered
- Audio (sub/dub) and server are remembered per anime
- Continue Watching now syncs with your account across devices

## 76 | Smarter search, picks for you, watch stats | 2026-11-08
- Search forgives typos ("naruot" finds Naruto) and shows matches saved on your device instantly, even offline, with a "Did you mean" note
- Home has a "Because you watched ..." row picked from the genres you watch and save
- Your profile shows watch stats: total time, this week, streak and top genres

## 77 | Airing schedule and reminders | 2026-11-08
- New Airing schedule on Home: the next 7 days in your own time zone with live countdowns
- Tap the bell on an anime to get a notification when each new episode airs (reminders refresh whenever you open the app)

## 78 | Offline downloads and offline Home | 2026-11-08
- New Download button in the player: saves the episode with its subtitles so you can watch without internet
- Downloads screen on Home: play offline, pause, resume or remove
- Home shows an "offline" note and keeps showing what was saved on your device when there's no connection

## 79 | Chat reactions, search and link previews | 2026-11-08
- React to any message with an emoji (long-press), see who reacted, tap a reaction to remove it
- Search inside a chat from the ⋯ menu, with typo tolerance
- Links in messages show a preview card with title and picture
- Watch Together: tap an emoji on the video and everyone in the room sees it float up

## 80 | Profile themes and more effects | 2026-11-08
- Pick a profile theme colour (Ocean, Rose, Forest, Sunset, Gold, Crimson) for your banner and namecard
- New namecard effects: Rain, Fireflies and Hearts

## 81 | Share card and faster startup | 2026-11-09
- Share your profile as a picture card (theme colour, avatar, name and link)
- Smaller app and a startup profile so the app opens and scrolls faster

## 82 | Backup, PIN lock, data saver, diagnostics | 2026-11-09
- Back up and restore your history, watchlist and settings as one file (Settings, App)
- Optional PIN lock that asks again after the app was in the background for 30 seconds
- Data saver caps video at 480p on mobile data
- Adult filter is hidden from Search until you turn it on in Settings
- Diagnostics screen with a Share report button for playback and caption problems

## 83 | Smarter playback | 2026-11-09
- Playback starts on a quality that fits your connection, learned from your recent speeds (Rust)
- Swipe up/down on the left of the video for brightness, on the right for volume; hold anywhere for 2x speed
- Leave the app while watching and the video keeps playing in a floating picture-in-picture window
- The next episode is warmed up while you watch, so it starts faster
- Optional: auto-download the next 3 episodes on Wi-Fi and delete watched downloads (Settings, App)

## 84 | Explore, My shows and widget | 2026-11-09
- Explore: pick a mood (Hype, Chill, Funny, Emotional...) and how long you want to watch, or browse by genre
- My shows: what you're behind on for airing anime, what to keep watching, what you haven't touched in 30+ days (drop it) and your plan-to-watch list
- Home-screen widget showing your last three shows and the next episode to air from your reminders

## 85 | Voice messages | 2026-11-09
- Send voice notes in chat: tap the mic, talk, tap send. Messages show a waveform and play in place

## 86 | Friends hub and badges | 2026-11-09
- New Friends screen on Home: see what your friends are watching (turn sharing off any time)
- Shared lists: make a list with friends, add anime and vote on what to watch next
- Profile badges for watch time, streaks and how many anime you've watched

## 87 | Cleanup | 2026-11-09
- Removed Airing schedule, Explore, My shows, Friends, Downloads, auto-download, delete watched downloads, the adult filter switch and App PIN lock
- The adult option is back in Search filters as before
- Back up and restore are still in Settings

## 88 | Speed and live data | 2026-11-09
- Faster, steadier network: connection reuse, quick retry on flaky connections, hosts looked up at startup
- Video buffer adapts to Wi-Fi vs mobile data; providers that keep failing are skipped for a few minutes
- Home refreshes when you return to it or the connection comes back; live updates pause while the app is in the background
- Progress saved offline goes up automatically when you are back online
- New episode alerts (Settings, off by default), Clear image cache, anonymous daily health report (can be turned off)
- Slow frames are logged to Diagnostics; search index is cached


## 89 | Netflix-style captions | 2026-11-09
- New "Netflix" caption style, now the default: white medium-weight text, soft drop shadow, no outline or box
- Long lines are split into two balanced lines
- Removed the unused comment and shared-list tables from the server

## 90 | Netflix Sans | 2026-11-09
- The whole app now uses Netflix Sans, including dialogs, buttons, the profile share card and video captions

## 91 | Faster chat, one caption menu | 2026-11-09
- The app is back on its original font; Netflix Sans is used only inside the video player
- Chat and inbox open instantly with the last copy, your message shows the moment you send it, and reactions load in the same request
- Captions are now a single menu: language, then Style, Size and Timing inside it
- Captions follow the video every frame, so lines appear and leave on time; long lines are broken Netflix-style by the Rust core

## 92 | Real-time chat | 2026-11-09
- Photos and voice notes appear in the chat the moment you send them, while they upload
- New messages are pushed straight to the other phone over the realtime connection instead of waiting for a refresh
- Photos are shrunk by the Rust core (smaller on mobile data, small files sent untouched) and keep the right way up
- Text messages clear the box instantly and come back only if sending fails
- Realtime (chat and Watch Together) reconnects the moment your network returns

## 93 | Continue where you left off | 2026-11-10
- Continue watching, the Watch button on Details and on Home, and switching seasons now open at the episode you were on (or the next one if you had nearly finished it) instead of episode 1
- If the saved episode is past the last one listed, it opens the last episode instead of jumping back to the first
- Removed the "Because you watched" row from Home

## 94 | Profile cleanup | 2026-11-10
- Removed the watch stats card from your profile

## 95 | Live Online and Watching | 2026-11-10
- Home now shows a live "Watching | Online" box: how many people have the app open and how many are watching a video right now, updating in real time

## 96 | Admin panel | 2026-11-10
- Admin panel support: anime and stream links added by the owner show up in Home and play in the player; app updates are offered only after they are published.

## 97 | Admin panel inside the app | 2026-11-10
- Owner-only Admin panel in Settings: add anime and stream links (mp4 / HLS), draft or publish updates to test them first, and see live users and online stats

## 98 | Announcements | 2026-11-10
- Important notes and announcements from the team now appear on Home and at the top of your Inbox

## 99 | Live updates and announcements | 2026-11-10
- New announcements and app updates now show up on their own while the app is open, with no need to close and reopen it

## 100 | Design polish, part 1 | 2026-11-10
- Skeleton loading on Home, Search and Details, accent colors and a pure-black AMOLED theme (Settings > Appearance), light haptics, friendlier empty states, and a parallax Home banner

## 101 | Design polish, part 2 | 2026-11-10
- Episode cards show Watched, NEW and a progress bar, an Up next card appears near the end of an episode, softer screen transitions, back-swipe preview, and the Details page is tinted with the anime cover color

## 102 | Design polish, part 3 | 2026-11-10
- Text size setting, pull-to-refresh with the Kiro mascot, snap-scrolling rows, a score ring on Details, and a quick first-launch setup screen

## 103 | Genre chips and seek-bar markers | 2026-11-10
- Genre chips on Home show picks for the genre you tap, and the intro and outro are marked in amber on the video seek bar
