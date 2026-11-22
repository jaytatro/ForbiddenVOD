# Forbidden Vid

*plug in the revolution*

A personal rebrand of [NuvioTV](https://github.com/NuvioMedia/NuvioTV) (GPL-3.0) for Android TV.

- Name, logo, launcher banner and toxic-green theme
- Sidebar: Home, Search, Movies, TV Series (+ Settings on the primary profile)
- Settings: Account and Profiles only
- Extra profiles always use the primary profile's addons and settings
- Kids profiles: only G / PG / PG-13 (TV-Y … TV-14); unrated titles hidden
- Update pop-up inside the app whenever a new build is published
- Sign in with your Nuvio email and password; manage addons on nuvio.tv

`patches/forbiddenvid.patch` holds all changes. GitHub Actions downloads NuvioTV at the pinned commit,
applies the patch and builds the APK into **Releases**.

**Download:** https://github.com/jaytatro/ForbiddenVOD/releases/latest/download/ForbiddenVid-arm64-v8a.apk

Optional repository secrets (Settings → Secrets and variables → Actions):
`TMDB_API_KEY` (free from themoviedb.org — needed for kids profile ratings).

Builds are always signed with `signing/forbiddenvid.jks`, so each new build installs over the previous one.

Licensed GPL-3.0, like NuvioTV.
