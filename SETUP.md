# KAIROS 2.0 — setup

## Build (Termux)
1. Unzip, `cd KAIROS`. Keep your own working Termux lines (aapt2 override etc.) in `gradle.properties`.
2. Needs Gradle 8.9+ (AGP 8.7.3, Kotlin 2.0.21). If your earlier build used different versions, keep yours and just copy `app/src/main/java`, `res`, `assets`, `AndroidManifest.xml` and the dependencies from `app/build.gradle.kts`.
3. `gradle assembleDebug` → APK in `app/build/outputs/apk/debug/`.

## Logo
Your logo is already at `app/src/main/res/drawable-nodpi/kairos_logo.png` (splash, home header, onboarding) and `mipmap-*/ic_launcher.png` (launcher icon).
To change it, replace those files. `drawable/ic_kairos_logo.xml` is only a vector fallback.

## Gemini key
Open the app → gear icon → Settings → paste the key → "Test API connection". Optional: OpenRouter key as automatic fallback.
Model names in Settings are editable (Custom model ID). If one doesn't exist, KAIROS auto-falls back to gemini-2.5-flash / 2.0-flash.

## Wake word
Put `hey_kairos.ppn` in `app/src/main/assets/`, rebuild, paste the Picovoice AccessKey in Settings, turn on "Hey KAIROS" (Pro).

## Permissions to grant (Settings → Permissions)
Microphone, Display over other apps (orb), Accessibility service (screen reading/tap/scroll/screenshot), Battery exemption.
Then Settings → "Start KAIROS orb".
