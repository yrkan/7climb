# 7Climb

Real-time climb intelligence for Hammerhead Karoo.

[![License](https://img.shields.io/badge/License-MIT-0d1117?style=flat-square&logo=opensourceinitiative&logoColor=white)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Karoo%202%2F3-0d1117?style=flat-square&logo=android&logoColor=white)](https://www.hammerhead.io/)
[![Downloads](https://img.shields.io/github/downloads/yrkan/7climb/total?style=flat-square&color=0d1117&logo=github&logoColor=white)](https://github.com/yrkan/7climb/releases)
[![Release](https://img.shields.io/github/v/release/yrkan/7climb?style=flat-square&color=0d1117&logo=github&logoColor=white)](https://github.com/yrkan/7climb/releases/latest)
[![Website](https://img.shields.io/badge/Web-7climb.com-0d1117?style=flat-square&logo=google-chrome&logoColor=00E676)](https://7climb.com)

W' Balance tracking (Skiba differential model), physics-based pacing, automatic climb detection, PR comparison, tactical insights, and 9 specialized data fields — all updating at 1Hz on your Karoo screen.

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

9 data fields, each available in 6 layout sizes (Small, Small Wide, Medium, Medium Wide, Large, Narrow):

| Field | What it shows |
|-------|---------------|
| **W' Balance** | Anaerobic capacity gauge: percentage, kJ remaining, status (FRESH/GOOD/WORKING/DEPLETING/CRITICAL/EMPTY), time to empty or full, depletion rate |
| **Pacing Target** | Target watts for current gradient with advice (PUSH/EASE/STEADY/PERFECT), delta from target, acceptable power range, actual power |
| **Grade** | Current gradient color-coded (green < 4%, amber 4-8%, orange 8-12%, red 12-18%, violet > 18%), altitude, current power, speed |
| **Climb Overview** | Compound view: W' gauge + target power + climb progress + ETA + tactical insight — the main field for climbing |
| **Summit ETA** | Estimated time to summit based on current speed, distance remaining, elevation remaining |
| **Climb Progress** | Progress percentage through the climb, distance remaining, elevation remaining, average grade |
| **Climb Profile** | Gradient-colored elevation profile with current position marker, climb name, total length, elevation, average and max grade |
| **Next Segment** | Preview of the upcoming 100m gradient segment with tactical insight (steep ahead, recovery zone, etc.) |
| **Compact Climb** | Minimal: grade + W'% for small data field slots, progress bar, distance/elevation remaining |

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
| CP | 0.95 × FTP | Critical Power estimate |
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

| Setting | Default | Description |
|---------|---------|-------------|
| FTP | — | Functional Threshold Power (watts). **Required.** |
| Weight | — | Rider weight (kg). **Required.** |
| W'max | 20,000 J | Anaerobic work capacity. Increase if you're a sprinter, decrease for endurance. |
| CdA | 0.321 m² | Coefficient of drag × frontal area. Hoods position default. |
| Crr | 0.005 | Rolling resistance coefficient. Road tire default. |
| Bike weight | 8 kg | Added to rider weight for physics calculations. |
| Pacing mode | STEADY | STEADY / RACE / SURVIVAL |
| Alerts enabled | Yes | Master toggle for all alerts |
| Alert sound | No | Beep patterns on alert |
| Alert cooldown | 30 s | Minimum time between repeated alerts |

**Tip:** CP (Critical Power) is automatically calculated as 95% of your FTP. If you know your actual CP from testing, set your FTP slightly higher than CP to compensate.

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
│   ├── PRComparisonEngine.kt        # PR tracking + live delta
│   ├── TacticalAnalyzer.kt          # Segment-aware tactical insights
│   ├── AlertManager.kt              # InRideAlert + sound + vibration
│   ├── RideStateMonitor.kt          # Ride lifecycle (recording/paused/idle)
│   └── CheckpointManager.kt         # Crash recovery checkpoints
├── datatypes/                       # 9 Karoo data fields
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
│   │   └── CompactClimbGlance.kt
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
│       └── AthleteProfile.kt
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
