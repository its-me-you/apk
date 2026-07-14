# Broraa — Android app wrapper

This packages your web messenger (https://its-me-you.github.io/cutie) as a real
Android app. It's a native WebView shell, not a copy of your site's code — it
just loads that URL and adds the native bits a web page can't do on its own: a
launcher icon, a splash screen, and permission prompts for camera, microphone,
notifications, and storage/media.

## Why there's no .apk file in this download

Building an Android APK needs the Android SDK, Gradle, and a live internet
connection to fetch build dependencies — none of which are available in the
sandbox I write files in. So instead of a broken or fake `.apk`, this is a
complete, ready-to-build project. Pick one of the two paths below to get the
real file.

## Option A — let GitHub build it for you (no Android Studio needed)

1. Create a new GitHub repository and push everything in this folder to it
   (or add it into your existing `its-me-you/cutie` repo, in its own folder).
2. Push to `main` or `master` — the included workflow
   (`.github/workflows/build-apk.yml`) builds the APK automatically, or you
   can trigger it by hand from the repo's "Actions" tab ("Run workflow").
3. When the run finishes, open it and download the `broraa-debug-apk`
   artifact near the bottom of the page — that zip contains `app-debug.apk`.
4. Get the APK onto your phone (download it directly on the phone, or copy it
   over) and tap it to install. You'll need to allow "install unknown apps"
   for whichever app you used to open it, since it isn't from the Play Store.

## Option B — build it yourself in Android Studio

1. Open this folder in Android Studio (File ▸ Open ▸ pick `broraa-android`).
2. If it asks about a missing Gradle wrapper, let it create one automatically
   — accept the prompt. (It's left out on purpose: it's a binary file I can't
   hand-write reliably. Android Studio regenerates it in seconds.)
3. Let Gradle sync, then Build ▸ Build App Bundle(s) / APK(s) ▸ Build APK(s).
4. Find the APK under `app/build/outputs/apk/debug/`.

## What's set up

- **Permissions**: camera, microphone, notifications (Android 13+), and both
  the modern per-media-type storage permissions (Android 13+) and the classic
  ones (Android 12 and below) — all requested up front when the app opens.
  The camera/mic ones are also checked live whenever the web page itself asks
  for them (e.g. during a call).
- **File uploads**: tapping a file/photo input on the page opens a chooser
  with both "take a photo" and "pick a file" options.
- **Downloads**: anything the page tries to save goes through Android's
  normal Download Manager, into the phone's Downloads folder.
- **Icon**: generated from your uploaded logo — the heart/B/R/checkmark mark,
  cropped away from the wordmark and outer border so it doesn't get clipped
  when Android masks it into a circle or rounded square. All five densities
  plus a proper adaptive icon live under `app/src/main/res/mipmap-*`. A spare
  512×512 copy sits at the project root in case you ever want it for a Play
  Store listing.
- **Splash screen**: black background, icon in the middle, using Android's
  native splash screen API.

## Worth knowing

- The APK from either path is **debug-signed** — fine to install and use
  yourself, but not what you'd submit to the Play Store, which needs a
  release keystore and signing config as a separate step.
- Requesting the notification *permission* is included, but actually pushing
  a notification while the app is closed (e.g. "new message from Raa") needs
  a backend push service like Firebase Cloud Messaging wired into the web
  app — that's a bigger addition on its own and isn't included here.
- The file chooser handles one file at a time; multi-select could be added
  later if you need it.
- Package name is `com.broraa.messenger`, app name is "Broraa" — both easy to
  change in `app/build.gradle.kts` (applicationId/namespace) and
  `res/values/strings.xml` (app_name) if you'd rather use something else.
