# 7Climb

Real-time climb intelligence for Hammerhead Karoo.

[![License](https://img.shields.io/badge/License-MIT-0d1117?style=flat-square&logo=opensourceinitiative&logoColor=white)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Karoo%202%2F3-0d1117?style=flat-square&logo=android&logoColor=white)](https://www.hammerhead.io/)
[![Downloads](https://img.shields.io/github/downloads/yrkan/7climb/total?style=flat-square&color=0d1117&logo=github&logoColor=white)](https://github.com/yrkan/7climb/releases)
[![Release](https://img.shields.io/github/v/release/yrkan/7climb?style=flat-square&color=0d1117&logo=github&logoColor=white)](https://github.com/yrkan/7climb/releases/latest)
[![Website](https://img.shields.io/badge/Web-7climb.com-0d1117?style=flat-square&logo=google-chrome&logoColor=00E676)](https://7climb.com)

W' Balance tracking (Skiba differential model), physics-based pacing, automatic climb detection, PR comparison, tactical insights, climb stats (VAM, W/kg, energy), and 10 specialized data fields — all updating at 1Hz on your Karoo screen.

**Know exactly when to push and when to save.**

## Screenshots

| W' Balance | Pacing Target | Grade | Climb Overview |
|:---:|:---:|:---:|:---:|
| ![W' Balance](screenshots/1.png) | ![Pacing Target](screenshots/2.png) | ![Grade](screenshots/3.png) | ![Climb Overview](screenshots/4.png) |

| Summit ETA | Climb Progress | Climb Profile | Compact Climb |
|:---:|:---:|:---:|:---:|
| ![Summit ETA](screenshots/5.png) | ![Climb Progress](screenshots/6.png) | ![Climb Profile](screenshots/7.png) | ![Compact Climb](screenshots/8.png) |

| Multi-field layout | Multi-field layout |
|:---:|:---:|
| ![Multi-field](screenshots/9.png) | ![Multi-field](screenshots/10.png) |

## Requirements

- Hammerhead Karoo 2 or Karoo 3
- Power meter (required for W' Balance and pacing)
- FTP and weight configured in app settings

## Install

1. Download `7climb.apk` from [Releases](https://github.com/yrkan/7climb/releases/latest)
2. Sideload to Karoo via USB/ADB:
   ```
   adb install 7climb.apk
   ```
3. Open 7Climb on Karoo, enter your FTP and weight
4. Add data fields to your ride profile

## Data Fields

10 data fields, each available in 6 layout sizes (Small, Small Wide, Medium, Medium Wide, Large, Narrow):

| Field | What it shows |
|-------|---------------|
| **W' Balance** | Anaerobic capacity gauge: percentage, kJ remaining, status (FRESH/GOOD/WORKING/DEPLETING/CRITICAL/EMPTY), time to empty or full, depletion rate |
| **Pacing Target** | Target watts for current gradient with advice (PUSH/EASE/STEADY/PERFECT), delta from target, acceptable power range, actual power, live W/kg |
| **Grade** | Current gradient color-coded (green < 4%, amber 4-8%, orange 8-12%, red 12-18%, violet > 18%), altitude, HR (bpm), cadence (rpm) |
| **Climb Overview** | Compound view: W' gauge + target power + climb progress + ETA + tactical insight — the main field for climbing |
| **Summit ETA** | Estimated time to summit based on current speed, distance remaining, elevation remaining, live VAM with color coding |
| **Climb Progress** | Progress percentage through the climb, distance remaining, elevation remaining, average grade |
| **Climb Profile** | Gradient-colored elevation profile with current position marker, climb name, total length, elevation, average and max grade |
| **Next Segment** | Preview of the upcoming 100m gradient segment with tactical insight. Shows "LAST SEGMENT" with grade/length/elevation on final segment |
| **Compact Climb** | Minimal: grade + W'% for small data field slots, progress bar, distance/elevation remaining |
| **Climb Stats** | Real-time climb performance: VAM (rolling 60s), W/kg, HR, cadence, energy (kJ), elapsed time, avg/max power, avg W/kg — full dashboard |

### Layout sizes

Each field adapts to the space available on your Karoo page:

- **Small** — single value (e.g. "70%" or "230W")
- **Small Wide** — value + label (e.g. "TARGET 230W EASE")
- **Medium** — value + secondary info
- **Medium Wide** — value + delta + secondary metrics
- **Large** — full detail with all metrics, dividers, and secondary rows
- **Narrow** — vertical compact layout

## How It Works

### W' Balance (Skiba Differential Model)

Tracks your anaerobic work capacity in real time. When you ride above Critical Power (CP), W' depletes. Below CP, it recovers.

```
if P > CP:  dW'/dt = (W'max - W') / τ - (P - CP)
if P ≤ CP:  dW'/dt = (W'max - W') / τ
```

| Parameter | Value | Description |
|-----------|-------|-------------|
| τ (tau) | 546 s | Skiba recovery time constant |
| CP | Manual or 0.95 × FTP | Critical Power (manual override or auto-calculated) |
| W'max | 20,000 J (default) | Anaerobic work capacity |
| Update rate | 1 Hz | dt clamped to 0-5s for stability |

**Status levels:**

| Status | W' remaining | Color |
|--------|-------------|-------|
| FRESH | > 90% | Green |
| GOOD | 70-90% | Light green |
| WORKING | 50-70% | Amber |
| DEPLETING | 30-50% | Orange |
| CRITICAL | 10-30% | Red |
| EMPTY | < 10% | Grey |

### Pacing Engine

Calculates target power for the current gradient using full physics:

```
F_gravity = m × g × sin(atan(grade/100))
F_rolling = m × g × Crr × cos(atan(grade/100))
F_aero    = 0.5 × ρ × CdA × v²
ρ         = 1.225 × exp(-0.0001185 × altitude)
P_target  = F_total × v_target
```

Pacing only activates on climbs (grade ≥ 1%). Advice tolerance is ±10W:

| Advice | Condition |
|--------|-----------|
| PUSH | Actual power < target - 10W |
| EASE | Actual power > target + 10W |
| PERFECT | Within ±5W of target |
| STEADY | Within ±10W of target |

**Three pacing modes** with different FTP-relative power caps:

| Mode | Power range | Speed range | Use case |
|------|-------------|-------------|----------|
| STEADY | 60-105% FTP | 2.0-8.0 m/s | Even effort, long climbs |
| RACE | 70-115% FTP | 2.5-10.0 m/s | Competition, negative splits |
| SURVIVAL | 50-90% FTP | 1.5-6.0 m/s | Energy conservation, ultra-distance |

### Climb Detection

When no route is loaded, climbs are detected automatically from live gradient data.

**State machine:** NotClimbing → PotentialClimb → ConfirmedClimb

| Parameter | Value |
|-----------|-------|
| Start threshold | ≥ 3% smoothed grade |
| Continue threshold | ≥ 2% smoothed grade |
| Confirmation distance | 100 m |
| End distance | 50 m of flat |
| Smoothing | 5-point moving average |

Route-based climbs from Karoo navigation always take priority over detected climbs.

### Tactical Analyzer

Scans upcoming climb segments and generates prioritized tactical insights:

| Insight | Trigger | Example |
|---------|---------|---------|
| Steep section | > 12% ahead | "Steep in 200m — ease off 10W now" |
| Dangerous section | > 18% ahead | "Extreme gradient — consider standing, lowest gear" |
| Recovery zone | Hard → easy transition | "Recovery zone at 400m — use to rebuild W'" |
| Attack point | Easy → steep transition | "Surge here to gap rivals before the wall" |
| Final kick | Last 100-500m | "Finish is easier — go all out!" |
| Gradient change | > 4% jump | "Grade ramps up in 150m — prepare to shift" |
| Easy section | < 4% in hard climb | "Easier section at 300m — recover here" |

### Climb Stats Tracker

Tracks real-time performance metrics during active climbs. Resets automatically when a new climb begins.

| Metric | How it's calculated |
|--------|---------------------|
| VAM (rolling) | Vertical Ascent Meters/hour over a 60-second rolling window |
| VAM (overall) | Total altitude gain / total climb time × 3600 |
| W/kg | Instant power / rider weight |
| Avg W/kg | Average power-to-weight ratio across the climb |
| Energy (kJ) | Integrated power × time |
| Avg/Max Power | Running averages and peak values |
| Avg HR, Max HR | Heart rate statistics |
| Avg Cadence | Pedaling cadence average |
| Elapsed Time | Duration since climb started |

**VAM color zones:**

| VAM (m/h) | Color | Level |
|-----------|-------|-------|
| < 600 | Green | Easy |
| 600–1000 | Amber | Moderate |
| 1000–1500 | Orange | Hard |
| 1500–1800 | Red | Steep |
| > 1800 | Violet | Extreme |

**W/kg color zones:**

| W/kg | Color | Level |
|------|-------|-------|
| < 2.0 | Green | Easy |
| 2.0–3.0 | Amber | Moderate |
| 3.0–4.0 | Orange | Hard |
| 4.0–5.0 | Red | Steep |
| > 5.0 | Violet | Extreme |

### PR Tracking

Climbs are stored in a local Room database, matched by GPS coordinates. On subsequent attempts, live time delta is shown against your personal record:

- **Ahead**: green, negative delta (e.g. "-12s")
- **Behind**: red, positive delta (e.g. "+8s")

New PRs trigger a celebration alert with ascending tones.

### Alerts

On-screen notifications with configurable sound:

| Alert | Trigger | Duration |
|-------|---------|----------|
| W' Critical | W' drops below 10% | 8s (urgent) |
| Climb Started | New climb begins | 5s |
| Summit Approaching | Near the top | 5s |
| New PR | Faster than previous best | 8s |

- Cooldown between repeated alerts (default: 30s, configurable)
- Screen wakes automatically on alert
- Sound: beep patterns via Karoo speaker (urgent = 3 beeps at 800Hz, normal = 2 beeps at 600Hz, PR = ascending 800→1000→1200Hz)

### FIT Recording

Custom developer fields are recorded to FIT files at 1Hz for post-ride analysis:

| Field | FIT Type | Unit |
|-------|----------|------|
| W_Prime_Balance | Float32 | joules |
| W_Prime_Percent | Uint8 | percent |
| Pacing_Status | Uint8 | enum (0=EASE, 1=STEADY, 2=PUSH, 3=PERFECT) |
| Target_Power | Uint16 | watts |
| PR_Delta | Sint32 | seconds |

### Crash Recovery

Engine state (W' balance, climb progress) is checkpointed periodically and restored if the app restarts during a ride.

## Settings

| Setting | Default | Range | Description |
|---------|---------|-------|-------------|
| FTP | — | 50–600 W | Functional Threshold Power. **Required.** |
| Weight | — | 30–200 kg | Rider weight. **Required.** |
| Bike type | — | Race / Aero / TT / Endurance / Custom | Preset bike weights (7.5 / 8.0 / 9.0 / 9.5 kg) or custom input |
| Bike weight | 8.0 kg | 5–25 kg | Custom bike weight (shown when type = Custom). Added to rider weight for physics. |
| Position | Hoods | Tops / Hoods / Drops / A.Drops / Aero / TT | Riding position preset. Sets CdA for aerodynamic drag calculation. |
| Surface | Road | Smooth / Road / Rough / Cobbles / Gravel | Road surface preset. Sets Crr for rolling resistance calculation. |
| Pacing mode | STEADY | STEADY / RACE / SURVIVAL | Power cap strategy for target watts |
| Alerts enabled | Yes | — | Master toggle for all alerts |
| Alert sound | No | — | Beep patterns on alert |
| Alert cooldown | 30 s | 15 / 30 / 60 / 90 s | Minimum time between repeated alerts |

**Advanced settings** (collapsed by default — only change if you know your values from testing):

| Setting | Default | Range | Description |
|---------|---------|-------|-------------|
| W'max | 20,000 J | 5,000–40,000 J | Anaerobic work capacity. Increase for sprinters, decrease for endurance. |
| CP | Auto | 0–500 W | Critical Power. Set to 0 for auto-calculation (FTP × 0.95), or enter your tested CP from a 3+12 min protocol. |
| CdA | 0.321 m² | 0.15–0.60 | Coefficient of drag × frontal area. Usually set via Position preset. |
| Crr | 0.005 | 0.002–0.015 | Rolling resistance coefficient. Usually set via Surface preset. |

**Position → CdA mapping:**

| Position | CdA | Description |
|----------|-----|-------------|
| Tops | 0.370 | Upright, hands on bar tops |
| Hoods | 0.320 | Standard riding position |
| Drops | 0.300 | Hands in drops |
| A.Drops | 0.270 | Aggressive drops, tucked |
| Aero | 0.240 | Aero bars or deep tuck |
| TT | 0.220 | Full time trial position |

**Surface → Crr mapping:**

| Surface | Crr | Description |
|---------|-----|-------------|
| Smooth | 0.004 | Velodrome or fresh tarmac |
| Road | 0.005 | Standard asphalt |
| Rough | 0.007 | Worn or patchy roads |
| Cobbles | 0.010 | Cobblestones, pavé |
| Gravel | 0.012 | Gravel or unsealed roads |

## Build from source

```bash
git clone https://github.com/yrkan/7climb.git
cd 7climb

# Karoo Extension SDK requires GitHub Packages authentication.
# Add to ~/.gradle/gradle.properties:
#   gpr.user=YOUR_GITHUB_USERNAME
#   gpr.key=YOUR_GITHUB_TOKEN (with read:packages scope)

export ANDROID_HOME=~/Library/Android/sdk
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew assembleRelease

# APK at: app/build/outputs/apk/release/7climb.apk
```

## Tech Stack

| Component | Version |
|-----------|---------|
| Kotlin | 2.2.20 |
| Karoo Extension SDK | 1.1.7 (karoo-ext) |
| Jetpack Glance | RemoteViews for data fields |
| Jetpack Compose | Settings UI |
| Room | Climb history database |
| DataStore | Preferences persistence |
| Kotlin Serialization | Data models |
| AGP | 8.13.2 |
| KSP | Symbol processing for Room |
| Gradle | 8.13 |

## Project Structure

```
app/src/main/kotlin/io/github/climbintelligence/
├── ClimbIntelligenceExtension.kt    # KarooExtension service (singleton)
├── BootReceiver.kt                  # Auto-start on boot
├── MainActivity.kt                  # Settings UI (Compose)
├── engine/                          # Business logic
│   ├── ClimbDataService.kt          # Karoo sensor stream aggregation (1Hz)
│   ├── WPrimeEngine.kt              # Skiba W' Balance model
│   ├── PacingCalculator.kt          # Physics-based target power
│   ├── ClimbDetector.kt             # Auto climb detection (no-route)
│   ├── ClimbStatsTracker.kt         # VAM, W/kg, energy, HR/cadence/power stats
│   ├── PRComparisonEngine.kt        # PR tracking + live delta
│   ├── TacticalAnalyzer.kt          # Segment-aware tactical insights
│   ├── AlertManager.kt              # InRideAlert + sound
│   ├── RideStateMonitor.kt          # Ride lifecycle (recording/paused/idle)
│   └── CheckpointManager.kt         # Crash recovery checkpoints
├── datatypes/                       # 10 Karoo data fields
│   ├── BaseDataType.kt              # Layout size detection (6 sizes)
│   ├── GlanceDataType.kt            # Abstract Glance base class
│   ├── glance/                      # Glance UI for each field
│   │   ├── GlanceColors.kt          # Color palette + color mapping
│   │   ├── GlanceComponents.kt      # Shared components (ValueText, etc.)
│   │   ├── WPrimeGlance.kt
│   │   ├── PacingGlance.kt
│   │   ├── GradeGlance.kt
│   │   ├── ClimbOverviewGlance.kt
│   │   ├── ETAGlance.kt
│   │   ├── ClimbProgressGlance.kt
│   │   ├── ClimbProfileGlance.kt
│   │   ├── NextSegmentGlance.kt
│   │   ├── CompactClimbGlance.kt
│   │   └── ClimbStatsGlance.kt
│   └── fit/
│       └── ClimbFitRecording.kt     # FIT developer fields
├── data/                            # Persistence
│   ├── PreferencesRepository.kt     # DataStore settings
│   ├── ClimbRepository.kt           # Room CRUD
│   ├── database/                    # Room DB
│   │   ├── ClimbDatabase.kt
│   │   ├── ClimbEntity.kt
│   │   ├── AttemptEntity.kt
│   │   ├── ClimbDao.kt
│   │   └── AttemptDao.kt
│   └── model/                       # Domain models
│       ├── LiveClimbState.kt
│       ├── WPrimeState.kt
│       ├── PacingTarget.kt
│       ├── ClimbInfo.kt
│       ├── ClimbSegment.kt
│       ├── AthleteProfile.kt
│       └── ClimbStats.kt
├── util/
│   └── PhysicsUtils.kt             # Air density, forces, speed solver
└── ui/                              # Compose settings
    ├── screens/
    │   ├── SettingsScreen.kt
    │   └── HistoryScreen.kt
    └── theme/
        └── Theme.kt
```

## Privacy

All data stays on your Karoo device. No cloud, no account, no telemetry.

## License

MIT
