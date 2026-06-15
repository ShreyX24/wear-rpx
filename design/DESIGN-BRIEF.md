# wear-rpx — UI Design Brief

A read-only **Wear OS** companion app for the RPX game-benchmark automation
platform. Goal on the wrist: **glance to see if runs are alive, and get buzzed
the instant one fails** so the operator can react. It streams live from the RPX
master (Socket.IO + REST); it does **not** control anything (no start/stop).

This folder is a self-contained design package: **real data samples** the app
renders + **current-state screenshots** + this brief. Pair it with the Kotlin
source (paths below) to redesign the UI.

---

## 1. The three screens (current state)

| Screen | Purpose | Source |
|---|---|---|
| **RunList** (home) | "Are any runs ongoing?" — list of active runs, each a card with status dot, game, SUT, iteration. Connection dot in the header. | `app/src/main/java/com/raptorx/wear/presentation/screens/RunListScreen.kt` |
| **RunDetail** | Live per-run view: big status, game/SUT/iteration/phase, error card on failure, and the **Activity feed** (the timeline). | `.../screens/RunDetailScreen.kt` |
| **Connect** | Find the master via mDNS scan or manual IP. | `.../screens/ConnectScreen.kt` |

Status→color lives in `.../screens/RunStatusVisuals.kt`. Theme is **bare** (`.../presentation/theme/Theme.kt` just wraps Wear Compose Material3 default) — wide open for art direction. Nav: `.../presentation/RpxApp.kt`.

Screenshots of the **current** look:
- `screenshot-runlist.png` — the home list (BMW run, green dot)
- `screenshot-rundetail.png` — the Activity feed (ticks, cost, a failed service call)

The current UI is **functional but plain** — default Material3, flat list of text rows. That's the thing to elevate.

---

## 2. What I want from the redesign

- **Glanceability first.** On a 454×454 round screen, the most important fact (is it alive? did it fail?) should read in <1s. Status is the hero.
- **A better Activity feed.** Right now every event is an identical text row + a colored dot. It's the heart of the app and the densest surface — it needs hierarchy: group/cluster related events, distinguish ticks vs service-calls vs checkpoints, make failures pop, show iteration progress, handle the 26 event types gracefully.
- **Surface the rich data we currently ignore.** The run object carries cost, token budget/usage, fps scores, checkpoints reached, phase detail, SUT specs (see §3) — none of it is shown yet. A great design would weave the right bits in without clutter.
- **Round-screen native.** Respect the circular viewport (curved headers, edge-aware padding), rotary/scroll, and ideally an **ambient** (always-on, low-power) treatment.
- **Strong empty/error/loading states.** "No active runs", "Couldn't load", "Waiting for events", disconnected.
- Read-only: **no action buttons** beyond navigation/refresh.

---

## 3. The data contract (what's available to render)

### RunList ← `sample-runs-list.json` (`GET /api/runs`)
`{ active: { <run_id>: Run }, history: [Run] }`. The app shows `active` as cards. Per-run fields the card uses today: `game_name`, `sut_display_name`, `status`, `current_iteration`, `iterations`.

### RunDetail header/meta ← `sample-run.json` (`GET /api/runs/<id>`)
One run object. **Lots is available, most unused today.** Highlights:
- Identity: `game_name`, `run_name`, `sut_display_name`, `sut_ip`
- State: `status` (`queued|running|paused|completed|partially_completed|failed|stopped|skipped`), `current_phase`, `current_phase_detail` (e.g. "Perf Run 2/2"), `error_message`, `termination_reason`
- Progress: `iterations`, `progress.current_iteration` / `progress.total_iterations`
- **Agentic** (`agentic_summary`): `total_ticks`/`max_ticks`, `total_tokens`, `total_cost_usd`, `model`, `checkpoints_reached[]` (e.g. launch_visible → main_menu → benchmark_running → results_screen)
- **Scores** (`perf_scores`): `avg`, `median`, `fps_values[]`, `metric_type` ("fps"), `variance_pct`
- Config: `quality`, `resolution`, `token_budget`, `provider`, `claude_model`
- **SUT specs** (`sut_info`): `cpu.brand_string`, `gpu.name`, `ram.total_gb`, `os`, `screen`, `bios`

### Activity feed ← `sample-activity-log.json` (`timeline_events[]` from `/logbook`)
**This is "the one full log under Activity"** — 144 real events from a Black Myth: Wukong run. The watch renders each as a row: the event's `message` with a status-colored dot. Per-event fields:
- `event_type` — **26 distinct types present**: `run_started`, `info`, `warning`, `iteration_started`, `sut_connecting/connected`, `resolution_detecting/detected`, `de2_connecting/connected`, `preset_syncing/synced`, `apo_mode_applied`, `apo_drift_detected`, `game_launching/launched/ready/handoff_to_agent`, `agentic_tick_started`, `service_call_started/completed/failed`, `agent_cost_update`, `checkpoint_reached`, `benchmark_sleeping/sleep_done`, `agent_cost_summary`, `iteration_completed`, `cooldown_started`
- `status` — `in_progress` | `completed` | `failed` (drives the dot color)
- `message` — human string (what's shown today)
- `iteration` — a **label string** like `"perf-run-1"` (NOT a number)
- `timestamp` — ISO-8601 string
- `metadata` — type-specific object (e.g. cost numbers, error text, fps) — rich, currently unused on the watch
- `group` — some events are grouped (e.g. `service_calls`)
- `replaces_event_id` — an event can **supersede** an earlier one in place (pending→completed); the feed should update the row, not append a duplicate
- `event_id`, `run_id`, `duration_ms`

Design implication: there's a natural hierarchy here — lifecycle/setup events (connect, launch), per-iteration agentic loop (tick → service call → cost), checkpoints, and results. The current flat list flattens all of it.

### Status → color (current — feel free to redo)
running/completed = green, paused/partial = amber, failed = red, stopped/skipped = grey, queued = slate. (`RunStatusVisuals.kt`)

---

## 4. Constraints / platform notes
- **Wear OS, Jetpack Compose (Wear Compose Material3 1.6.0).** Round 454×454. minSdk 30.
- Scrolling list = `ScalingLazyColumn`. Headers via `ListHeader`, screen via `ScreenScaffold` + `TimeText`.
- Read-only. No keyboard except the Connect screen's manual-IP entry (Wear `RemoteInput`).
- Consider **ambient mode** (always-on) and battery — the app holds a foreground service for alerts.
- Fonts: currently Material3 defaults. (The broader RPX brand uses IntelOne Display for numbers / Tenorite for text — optional inspiration, not required on watch.)

---

## 5. Files to read/edit (Kotlin source)
- Data shapes: `app/src/main/java/com/raptorx/wear/data/RpxModels.kt`
- Screens: `.../presentation/screens/{RunListScreen,RunDetailScreen,ConnectScreen,RunStatusVisuals}.kt`
- Theme: `.../presentation/theme/Theme.kt`  ·  Nav: `.../presentation/RpxApp.kt`
- ViewModel (state shape): `.../presentation/RpxViewModel.kt`

---

## 6. Files in this design package
- `DESIGN-BRIEF.md` — this file
- `sample-activity-log.json` — **the full Activity timeline (144 events)** ← the key artifact
- `sample-run.json` — one run object (RunDetail header/meta data)
- `sample-runs-list.json` — `GET /api/runs` (RunList data)
- `screenshot-runlist.png`, `screenshot-rundetail.png` — current look
