# Frontend architecture

The root Maven project builds the JavaFX frontend from the standard
`src/main/java`, `src/main/resources` and `src/test/java` directories.
There is one Maven/NetBeans project; `javafx-preview/` holds documentation and
packaging scripts, without a nested project descriptor. It has no source or runtime
dependency on `chat.client`, `chat.server`, `common`, Swing, JDBC or SQLite.

```text
PreviewLauncher → PreviewApplication                 composition root
                     ├── MockRuntime                in-memory implementation + lifecycle
                     └── ShellController
                            ├── ChatController
                            ├── LabController
                            └── MonitorController
                                   ↓ constructor injection
                                Services
                     AuthenticationService / ChatService
                     EncryptionLabService / FileTransferService
                             MonitoringService
                                   ↓
                           mock implementations
                                   ↓
                    DemoState + DemoFixtures (shared event ledger)
```

## Project structure

| Location | Responsibility |
|---|---|
| `src/main/java/studio/cipher/model` | Observable presentation models and immutable result records |
| `.../service` | Five replaceable service interfaces and the Services container |
| `.../mock` | Fictional fixtures, scripted behavior, shared counters and animation lifecycle |
| `.../ui/controllers` | Screen state, bindings and user actions; no mock imports |
| `.../ui/components` | Reusable native JavaFX controls, bubbles, avatars, file cards and log viewer |
| `src/main/resources/studio/cipher/view` | Shell/login and three screen FXML layouts |
| `.../css/theme.css` | Shared JavaFX CSS tokens and component states |
| `src/test/java` | Real-toolkit interaction/state/visual verification |
| `scripts/demo.py` | Isolated Java 21 build, portable packaging and launch |

FXMLLoader uses a controller factory. Every screen controller receives the `Services`
container and a small `ScreenHost` navigation/toast interface. No controller creates
or casts a mock service. View navigation caches the three screens, preserving
conversation drafts, Lab results and filters while switching screens.

The interfaces are the replacement boundary for later adapters. This iteration
contains no real adapter and makes no changes to any Server/protocol/crypto/database code.

## State and lifecycle

- ObservableList and JavaFX Properties are the source of truth, not rendered labels.
- All mock state changes occur on the JavaFX Application Thread, checked by DemoState.
- Timeline tasks simulate delays. There is no worker performing I/O or cryptography.
- The same event updates message history, previews, unread counts, logs and counters.
- Active conversations are explicitly marked; a visible incoming message does not
  gain an unread badge. Hidden conversations can gain unread counts.
- Session generation prevents replies from an old sign-in from appearing after a new one.
- Transfers keep their own progress/status; cancel invalidates their scheduled run,
  retry continues the same metadata model without allocating a file buffer.
- Lab generation tokens prevent reset operations from completing an obsolete walkthrough.
- Closing the application stops all registered Timelines and toast animations.

## Monitoring accounting

A session’s `received` is client → simulated Server; `sent` is simulated Server → client.
Each routed chat/file event increments its source/recipient session counters. The
summary totals sum these same counters. File progress accounts only the increment
in simulated bytes, never recounting already-completed progress on retry.

Connections derive from `Session.person.online`. Active transfers derive from QUEUED
or TRANSFERRING states. Failures increment when a simulated delivery/transfer fails.
Messages count locally modeled chat events, including failed submission attempts;
retry changes the state of an existing message and does not create another event.

Seed chart samples divide the initial fixture byte totals into labeled demo intervals
with exact integer remainders. Live demo samples are deltas of cumulative counters,
retained to 24 intervals. Logs are bounded to 250 entries and never log plaintext
message content, entered keys or cryptographic secrets.

## Explicit scope limits

This is one local in-memory world per process. It does not connect separate clients,
persist across restarts, authenticate users, encrypt/decrypt data, transfer/save file
contents, verify hashes, or monitor a real Server. FilePicker only supplies metadata.
The Encryption Lab computes local letter counts but labels all crypto stages as
illustrations. The monitoring window is a simulated admin view, not an admin API.
