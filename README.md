# BuddyLimit _(working name)_

Android app that enforces **daily per-app social-media time limits** that can only
be loosened or extended with a **buddy's approval**. Built to beat social media
addiction by adding a human accountability layer the built-in screen-time tools lack.

See **[SYSTEM_DESIGN.md](SYSTEM_DESIGN.md)** for the full design, locked decisions,
architecture, deployment plan, and the milestone/checkpoint tracker.

## Status

**M0 — project scaffold.** Local blocker (M1) is next.

## Build

Open in **Android Studio** (latest stable), let it sync Gradle, and run the `app`
configuration on a device/emulator.

- **JDK:** 17
- **Min SDK:** 26 · **Target/Compile SDK:** 35
- **Stack:** Kotlin · Jetpack Compose · Hilt · Room · Coroutines

> First sync will download the Android SDK components and generate the Gradle
> wrapper if not already present.
