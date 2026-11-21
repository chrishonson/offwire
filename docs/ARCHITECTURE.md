# Project Architecture: MVI (Model-View-Intent)

This project follows a strict MVI architecture with a Reactive Store.

## Core Components

### 1. State (Model)
- **Definition**: An immutable data class representing the single source of truth for the screen.
- **Naming**: `[ScreenName]State` (e.g., `ExampleCardState`).
- **Usage**: Must be a `data class`. All properties must be immutable (`val`).

### 2. Intent
- **Definition**: A sealed interface representing all possible user actions or events.
- **Naming**: `[ScreenName]Intent` (e.g., `ExampleCardIntent`).
- **Usage**: Use `data object` for no-arg intents and `data class` for intents with parameters.

### 3. Store (ViewModel)
- **Definition**: A `ViewModel` that holds the `StateFlow` and processes `Intents`.
- **Naming**: `[ScreenName]Store` (e.g., `ExampleCardStore`).
- **Responsibilities**:
    - Expose `val state: StateFlow<State>`.
    - Expose `fun dispatch(intent: Intent)`.
    - Handle business logic and state updates using `_state.update { ... }`.

### 4. UI (View)
- **Definition**: Jetpack Compose functions.
- **Container**: The "Screen" composable (e.g., `ExampleCardScreen`) holds the `Store`, collects state, and dispatches intents.
- **Component**: The "Content" composable (e.g., `ExampleCard`) must be **Pure**. It takes raw data (Strings, Ints, Booleans) as arguments, NOT the State object or the Store.

## Testing Guidelines
- **Unit Tests**: Test the `Store` using `kotlinx-coroutines-test`. Verify that dispatching an `Intent` results in the correct `State` transition.
- **UI Tests**: Use `createAndroidComposeRule` to test the integration of the UI.

## Modules

The project is split into `:core` and two application modules. `:core` contains the voice loop, speech engines, LiteRT engine, models, notes, MVI stores and contracts, and `AppConfig`, which holds behaviour flags only (`clonedVoice`, `renameAssistant`, `keepScreenOnInForeground`) and no look. The `limits/` package in `:core` defines `FeatureConfig`, `LimitsStore`, acknowledgement persistence, and disclaimer content. The `devicecheck/` package defines the first-launch device capability contract, configurable tested-device and resource thresholds, completion persistence, and `DeviceCheckStore`; each app module owns its matching wizard UI. `:app-console` is the LCARS console, voice only, and the product. `:app-classic` is the Warm Evening app with typed input, and it hosts iOS. A look lives in exactly one app module, so there is no shared UI module, and `:core` has no Compose.

Both apps keep the screen awake in the foreground via `KeepAwakePolicy` whenever `AppConfig.keepScreenOnInForeground` is set or the live phase is not `LivePhase.IDLE`.

### Resource monitoring (`resource`)

`:core` owns the resource-meter contract, sampling lifecycle, and pure fraction/block normalization (`ResourceFractions`: `cpuDevicePercent` and `cpuFraction` scaled by `cpuCoreCount()`, `memoryFraction`, `resourceBlockCount`, `formatCpuValue`, `formatMemoryValue`). `ResourceMonitor` is expect/actual: Android derives process CPU usage from `/proc/self/stat`, reads process PSS through `Debug.getPss()`, and reads available memory through `ActivityManager.MemoryInfo`; iOS currently returns a sample with unavailable values. `ChatStore` is the only owner of the sampler and exposes only the latest `ResourceSample` in `ChatState.resources`.

| Contract | Rule |
|---|---|
| Opt-in | `SettingsStore.showResourceMeter` defaults off and is changed only by `ChatIntent.ToggleResourceMeter`. When off, the sampling job stops and `ChatState.resources` is null. |
| Active phases | While the meter is enabled, sampling runs every 500 ms during an active turn (live `THINKING` or `SPEAKING`, `isGenerating`, or `synthesizer.isSpeaking`), continuing for a 2-second tail after the turn. |
| Idle state | While the meter is enabled and no turn is active, sampling continues every 2000 ms and `ChatState.resources` stays non-null so the meter remains visible; idle samples do not affect turn peaks. |
| Turn summary | Android logs one raw CPU/PSS peak line per sampled turn under the `GemmaVoice` tag, while the UI CPU label and bar show device percentage (`cpuPercent / cores`, clamped to 0..100%). |
| UI ownership | Each app owns its meter rendering and settings screen treatment; UI code never samples resources. |

### Network (`net`)
`:core` provides `NetworkClient` (HTTP handling) and `NetworkActivity` (in-flight tracking and logging).
All network activity is appended as JSON lines to `<filesDir>/network-log.jsonl`.
`NetworkActivity` exposes reactive flows (`isActive`, `inFlight`) consumed by MVI stores and UI indicators.
By project architecture rule, no code outside `NetworkClient` may open a network connection.

### Notes and encryption (`notes`, `security`)
`:core` holds the notes the model saves through its one tool, `save_note`:
- **`NotesRepository`** keeps the notes, oldest first, and `NotesStore` shows them newest first. The engine lists the newest 40 in the system instruction so the model can answer from them.
- **At-rest encryption**: on Android, `EncryptedFileNotesRepository` writes the notes as one AES-256-GCM encrypted file (`VaultCipher`) with a non-exportable Android KeyStore key (`KeystoreVaultCipher`).
