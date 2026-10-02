# Notify

Words that find you. Keep verses, quotes and lessons in a bank of folders, then let rhythms deliver
them as notifications at random moments or fixed times. Everything is stored on the device.

## Build from your phone (Termux + GitHub Actions)

```sh
unzip Notify.zip && cd Notify
git init -b main && git add . && git commit -m "Notify v0.1"
git remote add origin <your-repo-url> && git push -u origin main
```

Open the repo's **Actions** tab, wait for "Build APK", then download the `Notify-debug-apk` artifact
and install `app-debug.apk`.

## Layout

- `engine/Logic.kt` pure scheduling and picking rules, no Android imports (unit-tested)
- `engine/` alarms, notification, boot recovery
- `data/` Room database: folders, words, rhythms
- `ui/` Compose screens and the design system (`Theme.kt` explains the colour idea)

## Behaviour worth knowing

- Random rhythms use inexact alarms; only fixed times ask for exact alarms.
- Notifications need permission on Android 13+; the Today screen prompts when it is missing.
- A reminder is a rhythm pointed at a folder with a single word.
- A new word replaces the previous notification from the same rhythm instead of stacking.
