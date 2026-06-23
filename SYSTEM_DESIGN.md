# Social Media Limiter — System Design & Progress

> **Status:** M1 Phase 2 code done (CI-verified) · **pending device run, then Phase 3**
> **Last updated:** 2026-06-23
> **Platform:** Android-first (native Kotlin)
> **Working name:** BuddyLimit _(placeholder, renamable)_
> **Repo (private):** github.com/Blackrevan1/buddylimit · local root `/home/krish/Desktop/ideas`

This is a living document. It holds the vision, the architecture, every locked
design decision (with rationale), and a checkpoint-based progress tracker.
Update the **Progress Log** at the bottom whenever something changes.

---

## 1. Overview & Vision

An app that enforces **daily per-app time limits** on social media — but unlike
the built-in Android Digital Wellbeing timers, **you can't just reset or extend
the limit yourself.** Extending time or loosening a limit requires approval from
a **buddy** (a friend, partner, or fellow struggler) who approves the request
from *their own* account.

**Personal motivation:** built to help beat the owner's own social media
addiction. Dogfooding on the owner's phone is the first real test.

**Why the buddy is the whole point:** the built-in tools fail because the addict
*is* the person who can disable them in a weak moment. Putting another human in
the loop turns "loosen my limit" into a socially costly act. The buddy mechanism
is a **commitment device / social accountability layer** — that's the real
product, not the timer.

**Reciprocal twist:** two people fighting the same habit can be each other's
buddy and reviewer.

---

## 2. The One Principle That Shapes Everything

**You own the device, so no app-level block is truly un-bypassable.** A user can
force-stop, uninstall, clear data, revoke permissions, or boot to safe mode.

Consequences we design around:
- "Blocking" is **cover-and-redirect**, not true prevention (see §5).
- Enforcement strength = **social + friction cost**, not technical impossibility.
- We **make it exist first, then make it harder to bypass** (milestones below).

---

## 3. Locked Design Decisions

| # | Decision | Choice | Rationale |
|---|----------|--------|-----------|
| D1 | Platform / stack | **Android-first, native Kotlin** | Blocking is all native system access; a cross-platform layer adds friction with no payoff for v1. iOS (Family Controls) is a future consideration. |
| D2 | Foreground detection | **UsageStats polling** (~1s) | Low Play-Store risk vs. AccessibilityService. Accept a brief feed-flash for now; revisit accessibility in M3 to kill the flash. |
| D3 | Blocking model | **Re-entry denial** (pure, no session cap in v1) | Simplest timer logic. Known trade-off in §10 (the "never close it" loophole). |
| D4 | Reset timing | **4am daily reset, configurable** | Avoids the midnight-doomscroll loophole (fresh budget at 12:01am). Changing the reset time will be buddy-gated in M2. |
| D5 | Budget scope | **Per-app budgets** | Simple, clear mental model for v1. Combined pool is a future option. |
| D6 | Build sequencing | **Local-only M1, buddy layer M2** | The blocking loop is the technically risky core and must exist first. Buddy layer is standard backend, added once the core works. |
| D7 | Buddy approval design | **Account-based, server-mediated** | Buddy approves from their own device; user never holds a shared secret. (Shared passwords get memorized — explicitly rejected.) |
| D8 | Backend (M2) | **Firebase** — Auth + Firestore + FCM + Cloud Functions (serverless/managed) | Chosen for least operational overhead given no cloud preference: one SDK (auth+DB+push+functions), no servers/IaC/IAM, push native, ~$0 at MVP scale. AWS serverless/Amplify was the runner-up. Lock-in hedged via repository abstraction; revisit at M2. |

> ⚠️ **M1 caveat:** until the buddy layer (M2) lands, the user can still change
> their own limits — so M1 is roughly as strong as built-in Digital Wellbeing.
> That's expected. M1 proves the mechanism; M2 makes it worth using.

---

## 4. System Architecture

### Milestone 1 — Local blocker (no backend)

```
┌─────────────────────────────────────────────────────────┐
│  Foreground Service (persistent, restarts on boot)        │
│   ├─ Poll UsageStatsManager.queryEvents (~1s)             │
│   ├─ Track current foreground app                         │
│   ├─ Accumulate per-app usage for current day-window      │
│   └─ Block decision: used_today >= budget ?               │
│           └─ yes → show Overlay + send to Home            │
└─────────────────────────────────────────────────────────┘
        │ reads/writes                    │ shows
        ▼                                 ▼
┌──────────────────┐            ┌──────────────────────────┐
│  Local store      │            │  Full-screen Overlay      │
│  (Room)           │            │  "Instagram is done —     │
│   ├─ budgets      │            │   back in 3h 41m"         │
│   ├─ usage_today  │            │  (live countdown to 4am)  │
│   └─ day-anchor   │            └──────────────────────────┘
└──────────────────┘
        ▲
        │ configured by
┌──────────────────┐
│  Settings UI      │  list apps · pick monitored · set minutes/day
└──────────────────┘
```

### Milestone 2 — Buddy layer (cloud)

```
User app  ──"request +15 min"──►  Backend (Firebase: Auth/Firestore/FCM)
                                        │ push notification
                                        ▼
                                   Buddy app  ──approve / deny──►  Backend
                                        │ apply result
                                        ▼
User app  ◄──"approved: +15 min"──  Backend
```

- Limit/reset changes and time-extensions become **server-gated** actions.
- Fallback when buddy is unavailable: **timed auto-unlock** after a long wait
  (also notifies the buddy) — so a real need (e.g. a 2FA code) isn't a brick wall.
- Reciprocal buddies supported (each user is the other's reviewer).

---

## 5. Blocking Flow (runtime detail)

```
loop every ~1s in the foreground service:
    fg = current foreground package (from queryEvents)
    if fg is monitored:
        accumulate elapsed time into usage_today[fg]

    on foreground-ENTER event for monitored app:   # re-entry denial
        if usage_today[fg] >= budget[fg]:
            show full-screen overlay (countdown to next reset)
            send user to Home (CATEGORY_HOME intent)
```

**Honest behavior:** the target app *does* open; we detect it (~1s with polling)
and throw a full-screen overlay over it, then push it to background. There's a
brief flash of the real app before the overlay lands.

**Re-entry denial means:** an in-progress session is *not* interrupted; the block
only fires on the next open after the budget is spent. (See loophole in §10.)

---

## 6. Reset Logic (4am day-window)

```
day_start = most recent 04:00 (local) at or before now
next_reset = next 04:00 after now
remaining_to_show = next_reset - now      # what the overlay counts down to
on crossing next_reset: usage_today = 0   # fresh budgets
```

- Reset hour is a **setting** (default 04:00); changing it is buddy-gated in M2.
- Known gap: based on device local time → setting the clock forward is a bypass.
  Closed in M3 by anchoring to server time.

---

## 7. Permissions (M1)

| Permission | Purpose | Notes |
|-----------|---------|-------|
| `PACKAGE_USAGE_STATS` | Read app usage | Special access; user grants via Settings → Usage Access |
| `SYSTEM_ALERT_WINDOW` | Draw the blocking overlay | Special access |
| `FOREGROUND_SERVICE` (+ type) | Keep the monitor alive | Android 14 needs a service type (likely `specialUse` + Play justification) |
| `POST_NOTIFICATIONS` | Foreground-service notification | Android 13+ |
| `RECEIVE_BOOT_COMPLETED` | Restart monitor after reboot | |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Avoid the OS killing the service | Play-sensitive |
| `QUERY_ALL_PACKAGES` | List installed apps to choose from | Play-sensitive; needs a policy declaration |

---

## 8. Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose (confirmed)
- **Local store:** Room (structured data) + DataStore (settings, e.g. reset hour)
- **Async:** Coroutines + Flow
- **Architecture:** MVVM + repository — keeps the data layer swappable (also the backend lock-in hedge)
- **DI:** Hilt (manual DI acceptable to start)
- **Monitor runtime:** Foreground service (live ~1s poll) **+ WorkManager watchdog** to restart it if the OS kills it
- **Backend (M2):** Firebase — Auth + Firestore + FCM + Cloud Functions (Functions enforce server-side approval authority)
- **Min / Target SDK:** TBD at M0 (leaning min SDK 26)

---

## 8.5 Deployment & Release

Staged to the milestones — **no store is needed to start.**

**Stage 0–1 · Dogfood (now):** Release APK from Android Studio → `adb install` to own phone. All sensitive permissions (overlay, usage access, battery exemption) grant fine on a sideloaded build, so M1 is usable on yourself with zero deployment infra.

**Stage 2 · Buddy + a few testers:** Firebase App Distribution (already on Firebase; testers get update notifications). Lower friction than the Play internal track for the first handful.

**Stage 3 · Public on Play Store — high-risk review profile.** Each of these needs an in-console declaration + justification: `QUERY_ALL_PACKAGES`, `SYSTEM_ALERT_WINDOW`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, the `specialUse` FGS type — and in M3, **AccessibilityService + Device Admin** (strictest; common rejection/removal cause). Plan for review iterations + a required privacy policy. *Strategic option for later:* ship a Play-safe build alongside a more aggressive sideload/F-Droid build for the power permissions.

**CI/CD:** GitHub Actions — build + unit tests + lint on push (set up early in M1). Add auto-distribute to Firebase App Distribution at Stage 2, and the Play internal track later (Fastlane / Gradle Play Publisher).

**Signing:** Play App Signing (Google holds the signing key; you keep an upload key). Local release keystore for sideload builds.

**Backend deploy (M2):** Firebase CLI (`firebase deploy`) for Firestore **security rules** + Cloud Functions. The security rules are load-bearing — they enforce that a user can't approve their own request or edit limits directly from the client.

**Prereq (M0):** `git init` + push to a private GitHub repo — this unlocks the whole CI/CD chain.

---

## 9. Milestones & Checkpoints

> **Definition of Done (every checkpoint):** a checkpoint is not "done" until it is
> *verified working*, not just written. Before checking a box and moving on:
> 1. It **builds** — CI green, or a clean local Android Studio build.
> 2. Relevant **tests pass** — unit tests, plus a **manual run on a device/emulator**
>    for anything touching permissions, the foreground service, or the overlay.
> 3. It's **committed** and this tracker is updated.
>
> **How we verify here:** this dev environment has **no Android SDK/Gradle**, so the
> build oracle is **GitHub Actions CI** (push → build + test + lint) and/or a manual
> run in **Android Studio**. Never advance to the next step on unverified code.

### M0 — Project setup ✅ (CI-verified 2026-06-18)
- [x] Initialize git + push to a private GitHub repo → `github.com/Blackrevan1/buddylimit`
- [x] Create Android project (Kotlin) — scaffolded by hand, verified by CI
- [x] Decide min SDK / target SDK → **min 26 / target 35**
- [x] Set up package structure → `com.buddylimit.app`
- [x] Add base dependencies (Room, Coroutines, Compose, Hilt)
- [x] GitHub Actions skeleton: build + unit tests + lint on push → **passing (5m18s)**

### M1 — Local blocker (the "exists" version)
> Each phase ends with a ✔ **verify gate** — don't start the next phase until it passes.

**Phase 1 · App selection & budgets** ✅ _complete_ — _CI-verified 2026-06-18 (commit aada1e2); device-verified 2026-06-23_
- [x] List installed apps (`PackageManager` launcher `<queries>` — QUERY_ALL_PACKAGES deferred)
- [x] UI to pick monitored apps
- [x] UI to set per-app daily minutes (stepper, 5-min steps, default 30)
- [x] Persist budgets (Room, behind `AppRepository` interface)
- [x] ✔ **Verify:** CI green ✅ + **device run passed 2026-06-23** (selections + budgets survived an app restart)

**Phase 2 · Usage tracking** — _code done, CI-verified 2026-06-23 (commit 171ea9e)_
- [x] Request + verify Usage Access permission (`UsageAccess`: AppOps check + Settings intent; UI grant card)
- [x] Foreground service skeleton + persistent notification (`UsageMonitorService`, `specialUse` FGS type)
- [x] Poll `UsageStatsManager.queryEvents` for foreground app (~1s)
- [x] Accumulate per-app usage for current day-window (`DayWindow` 4am anchor; Doze-gap cap)
- [x] Persist usage counters (Room v2 `usage_records` behind `UsageRepository`; migrated, not destructive)
- [ ] ✔ **Verify:** CI green ✅ — **pending device run** (tracked time for a real app matches reality, ±poll interval)

**Phase 3 · Reset logic**
- [ ] Implement 4am day-window anchor
- [ ] Zero counters on crossing reset
- [ ] Configurable reset time setting
- [ ] ✔ **Verify:** counters zero at the boundary (test with a near-term reset time)

**Phase 4 · Blocking**
- [ ] Request overlay permission
- [ ] Block decision on foreground-enter (used ≥ budget)
- [ ] Full-screen overlay with live countdown
- [ ] Send user to Home on block
- [ ] ✔ **Verify:** exceeding budget on a real app shows the overlay + countdown on re-entry

**Phase 5 · Reliability**
- [ ] Battery-optimization exemption prompt
- [ ] `BOOT_COMPLETED` receiver to restart service
- [ ] Handle `POST_NOTIFICATIONS` (Android 13+)
- [ ] ✔ **Verify:** blocking still works after a reboot and after the screen's been off a while

**Phase 6 · Validate end-to-end**
- [ ] Dogfood on owner's phone for several days

### M2 — Buddy layer
- [ ] Choose/confirm backend (Firebase Auth + Firestore + FCM)
- [ ] User accounts / auth
- [ ] Buddy linking (invite / pair)
- [ ] Data model: limits, requests, approvals
- [ ] Make limit/reset changes require buddy approval
- [ ] "Request more time" flow (user side)
- [ ] Push notification to buddy
- [ ] Buddy approve/deny screen
- [ ] Apply approved extension locally
- [ ] Timed auto-unlock fallback when buddy unavailable
- [ ] Reciprocal / mutual buddy support

### M3 — Anti-bypass hardening
- [ ] Device Admin to block uninstall
- [ ] Gate device-admin deactivation behind buddy/password
- [ ] Anchor reset to server time (kills clock-change bypass)
- [ ] Watchdog for service being killed
- [ ] Evaluate AccessibilityService detection to kill the flash
- [ ] OEM auto-start / task-killer guidance (Xiaomi, Samsung, etc.)

### M4 — Polish & release
- [ ] Onboarding flow for the sensitive permissions
- [ ] Stats / insights screen
- [ ] Play Store listing + policy declarations
- [ ] Privacy policy
- [ ] Beta testing

---

## 10. Known Risks & Open Issues

| Risk / Issue | Impact | Plan |
|--------------|--------|------|
| **"Never close it" loophole** | Re-entry denial doesn't stop one long uninterrupted binge (e.g. Reels/Shorts) — the core addictive pattern for many | Accepted for v1; optional single-session cap later |
| **Service gets killed** (Doze, OEM task-killers) | Blocking silently stops | Foreground service + battery exemption + boot restart; watchdog in M3 |
| **Play Store accessibility policy** | App rejected/pulled if we misuse AccessibilityService | Avoided in v1 (polling). Re-evaluate carefully in M3 |
| **Clock-change bypass** | User moves clock to 4am to reset early | Server-time anchor in M3 |
| **Uninstall bypass** | User just removes the app | Device Admin (block uninstall) in M3 |
| **Buddy collusion** (reciprocal) | Two addicts rubber-stamp each other | Surface partner stats/streaks to add social cost (M2+) |
| **Buddy unavailable** | User locked out of a genuine need (2FA, etc.) | Timed auto-unlock fallback (M2) |
| `QUERY_ALL_PACKAGES` / battery / FGS-type | All are Play-sensitive declarations | Prepare justifications for review |

---

## 11. Prior Art to Study

- **Accountability software** (closest to the buddy model): Covenant Eyes,
  Ever Accountable, Accountable2You, Truple — they send activity to a partner.
- **Screen-time blockers** (blocking UX + surviving Play review): Opal, one sec,
  Jomo, ScreenZen, AppBlock, Stay Focused.
- **StickK** — commitment-device concept (stake + referee); validates *why* the
  buddy mechanism works.
- **iOS path (future):** Apple's Family Controls / Screen Time API has a
  sanctioned guardian model but requires a special distribution entitlement.

---

## 12. Open Decisions (TBD)

- [ ] App name
- [x] ~~Jetpack Compose vs. XML views~~ → **Compose**
- [x] ~~Min / target SDK~~ → **min 26 / target & compile 35**
- [x] ~~Backend (Firebase vs. alternative)~~ → **Firebase** (D8)

---

## 13. Progress Log

- **2026-06-18** — Created this doc. Locked decisions D1–D7 (platform, detection,
  blocking model, reset, budget scope, sequencing, buddy design). Next: confirm
  open decisions in §12 and start M0.
- **2026-06-18** — Backend decided: **Firebase** (D8), for least operational overhead.
  Added §8.5 Deployment & Release (sideload → Firebase App Distribution → Play Store;
  GitHub Actions CI; Play App Signing). Enriched §8 (Compose, Hilt, foreground service
  + WorkManager watchdog, Cloud Functions). Resolved §12 Compose + backend.
  **Next: M0** — git init + GitHub repo + Android project scaffold.
- **2026-06-18** — **M0 complete & CI-verified.** Scaffolded native Android project
  (Kotlin, Compose, Hilt, Room, Coroutines; AGP 8.7.2 / Kotlin 2.0.21 / Gradle 8.10.2;
  min SDK 26 / target 35; package `com.buddylimit.app`). Repo (private):
  github.com/Blackrevan1/buddylimit. GitHub Actions CI (assembleDebug + unit tests +
  lint) **passed in 5m18s** — first real build verification (this env has no Android
  SDK, so CI is the build oracle). Resolved §12 min SDK. **Next: M1** — local blocker;
  good starting point is app-selection + per-app budget UI, then the UsageStats
  foreground service.
- **2026-06-18** — Adopted a **verify-before-proceed** working process (user request):
  no checkpoint is "done" until it builds (CI green / clean Studio build), relevant
  tests pass (+ manual device run for permissions/service/overlay), and it's committed
  with the tracker updated. Codified as the **Definition of Done** in §9 and added
  per-phase ✔ verify gates to M1. Also set CI to skip docs-only changes (`paths-ignore`).
- **2026-06-18** — **M1 Phase 1 code complete & CI-verified** (build + unit test + lint
  green, 4m5s, commit aada1e2). Room `MonitoredApp` + DAO behind an `AppRepository`
  interface; Hilt DI; `AppListViewModel` merges installed apps (launcher `<queries>`)
  with the monitored set; Compose picker with monitor toggle + per-app minute stepper;
  unit test for the pure merge. **Pending:** manual device run to confirm persistence
  across restart before starting Phase 2.
- **2026-06-23** — **M1 Phase 1 ✅ device-verified — gate closed.** Added a CI step to
  upload the debug APK as the `app-debug` artifact; sideloaded it onto the owner's real
  Android phone (no local SDK needed — this dev box has none, but CI is the build oracle).
  Manual test passed: monitored-app selections + non-default per-app budgets (e.g. 15/45)
  survived a full app kill + relaunch. Phase 1 is now done per the Definition of Done.
  **Next: Phase 2 — usage tracking** (Usage Access permission → foreground service +
  persistent notification → poll `UsageStatsManager.queryEvents` → accumulate per-app
  usage for the day-window → persist counters).
- **2026-06-23** — **M1 Phase 2 code complete & CI-verified** (build + unit tests + lint
  green, 3m27s, commit 171ea9e). `UsageMonitorService` is a persistent `specialUse`
  foreground service that polls `UsageStatsManager.queryEvents` ~1s, resolves the current
  foreground app, accumulates elapsed time for monitored apps, and flushes to Room every
  5s (per-tick elapsed capped to avoid Doze/screen-off overcount). `DayWindow` holds pure,
  unit-tested 4am-anchored day-key/next-reset logic. Room bumped to v2 with a *migrated*
  (non-destructive) `usage_records` table (composite PK pkg+dayKey) behind a new
  `UsageRepository`; atomic increment via INSERT-OR-IGNORE + UPDATE (SQLite <3.24 safe).
  `UsageAccess` checks the AppOps grant + opens Settings; the app list now has a monitoring
  controls card (grant / start-stop) and shows live "used Xm Ys" per monitored app as the
  verification surface. Manifest gained PACKAGE_USAGE_STATS, FOREGROUND_SERVICE(+SPECIAL_USE),
  POST_NOTIFICATIONS + the service declaration. **Pending:** device run to confirm tracked
  time matches reality (±poll) before Phase 3 (reset logic).
