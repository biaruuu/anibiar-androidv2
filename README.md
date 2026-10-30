# AniBiar (Android)

Native Kotlin + Jetpack Compose anime app with an onyx theme modeled on Miruro TV.

**Milestone 1: Home screen** — hero carousel with TMDB logo + backdrop, Latest Episodes grid
(Sub/Dub toggle, 1/5 pager), Top Airing, Upcoming, Continue Watching.

## Data

- **AniList GraphQL** for catalog data. Requests are retried on HTTP 429, and the last good response
  is cached on disk so the app opens instantly. Posters are cached in memory and on disk (Coil).
- **TMDB** for the title logo and wide hero backdrop.

## Build

Add your TMDB key (v3 key or v4 read token) to `local.properties`:

```
TMDB_API_KEY=your_key_here
```

Then `./gradlew assembleRelease`. Without a key the app still works and falls back to AniList artwork.

GitHub Actions builds the APK on every push and attaches it to a Release. Add the key as the
repository secret `TMDB_API_KEY` (Settings → Secrets and variables → Actions) so CI builds include logos.
