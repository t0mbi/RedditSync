# Reddit Sync fixes

ReVanced patches that fix two features in **Sync for Reddit** after Reddit blocked the API endpoints they used:

| Patch | What it fixes |
| --- | --- |
| Fix Reddit galleries | Galleries failed to open (`/api/info` is blocked). The post is loaded from `/comments` instead. |
| Fix load more comments | "Load more comments" did nothing (`/api/morechildren` is blocked). Each child is loaded from `/comments` instead, up to 20 per tap. |

## Supported version

Only **Sync v23.06.30-13:39** (package `com.laurencedawson.reddit_sync`). Other versions are not supported.

## How to use

1. Get the clean Sync v23.06.30 APK from a source you trust (for example APKMirror). This repo does not provide it.
2. In ReVanced Manager, add this bundle as a patch source. Use the `.rvp` from the [latest release](https://github.com/t0mbi/RedditSync/releases/latest), either as a local file or by its URL.
3. Select the Sync APK and enable **Fix Reddit galleries** and **Fix load more comments**.
4. A clean Sync can only log in with a patch that spoofs the client. That patch is not in this bundle; use one from a ReVanced source you already use.
5. Patch and install. If a different build of Sync is installed, Android requires you to uninstall it first, which removes its data. Use Sync's own settings export first.

## Build

Needs JDK 17+ and a GitHub token with `read:packages` (the ReVanced patcher plugin is hosted on GitHub Packages).

```
export GITHUB_ACTOR=<your username>
export GITHUB_TOKEN=<token with read:packages>
./gradlew buildAndroid
```

The bundle is written to `patches/build/libs/`.

## Notes

- Not affiliated with Reddit, Sync's developer or ReVanced.
- No guarantee these keep working if Reddit changes its responses again.
- Licensed under GPL v3.0 (see `LICENSE`).
