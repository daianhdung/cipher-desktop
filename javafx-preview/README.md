# Cipher Desktop — JavaFX frontend preview

A **frontend-only, mock-first** Java 21 / JavaFX 21.0.13 desktop prototype.
The Chat, Encryption Lab and Server Monitoring screens share one design system,
service interfaces and in-memory demo state.

**No TCP connection, real authentication, AES/RSA operation, file-content transfer,
or database operation occurs.** The existing Swing/TCP sources are preserved and are not dependencies of the frontend build.

## Default application in NetBeans

The repository root now also opens this JavaFX frontend by default. Open the root
`pom.xml` project in NetBeans with JDK 21 and press **F6 / Run Project**. Close and
reopen an old Ant project once to refresh its project type. Close any previously opened `javafx-preview` project too. The root `pom.xml` is
the only Maven project. Source Packages are under `src/main/java`, Test Packages
under `src/test/java`, and FXML/CSS under `src/main/resources`. This directory now
contains documentation and demo packaging scripts, not a second project.

## Launch Demo Mode

Run source commands from the repository root.

Requirements for building: JDK 21, Maven 3.9+, Python 3.9+, and internet for the
first JavaFX dependency download. Run on a desktop, not a headless server.

```bash
cd javafx-preview
python3 scripts/demo.py run
```

Enter any valid display name, then choose a conversation. The supplied name is a
local label, not a real account. Sign out from the bottom of the navigation rail.

Open the simulated monitoring window independently:

```bash
python3 scripts/demo.py run --monitor
```

Optional automatic mock sign-in:

```bash
python3 scripts/demo.py run --username="Alex Morgan"
```

Nothing needs to run in another terminal. **Do not start a TCP Server for this preview.**
Separate application processes have separate in-memory demo worlds.

## Run without the IDE or Maven

```bash
python3 scripts/demo.py build
./target/demo/run-demo.sh
# Independent monitor:
./target/demo/run-demo.sh --monitor
```

On Windows, run `target\demo\run-demo.cmd` (add `--monitor` if desired).
The complete `target/demo/` folder includes the application JAR, the four necessary
JavaFX modules and launchers. It only requires a matching Java 21 runtime to run.
Copy the **entire folder**. The JavaFX native libraries are platform-specific:
this generated bundle is macOS Apple Silicon; build locally on Windows/macOS Intel/Linux
for that OS and CPU. Source-level cross-platform support is provided, but only
macOS Apple Silicon has been run and visually verified here.

`target/cipher-demo-<platform>-<architecture>.zip` is the same bundle in a ZIP.
This is a portable Java app, not a native installer or a bundled JRE.

## Explore the prototype

### Chat

- Select teammates, search names or last-message previews, and view online/offline state.
- Type a message: **Enter** sends; **Shift+Enter** inserts a new line.
- A local message appears immediately, changes Sending → Delivered → Read, then a
  scripted incoming reply arrives. Typing and unread indicators are simulated.
- The header reply icon generates an incoming message. The `…` menu can make the
  next message fail, or insert a sample attachment. Offline recipients demonstrate
  failure; the message’s Retry action becomes useful once they are online.
- Attach a local file using the paperclip. Only its **name and size** are used;
  file contents are never read. Progress is simulated independently of file size.
- Attachment `…` actions show details, cancel or retry. Complete is explicitly a
  demo state; no save/open/integrity success is fabricated.
- The detail panel collapses below 1190px window width. The info button then shows
  a compact details dialog. The sidebar stays scrollable at 1024×700.

### Encryption Lab

- Simulate RSA public-key exchange before running the encryption walkthrough.
- Enter plaintext and a demo key. The key field is illustrative input only.
- Follow eight highlighted steps, then inspect local Unicode letter counts in
  the chart and table. Copy the illustrative payload or reveal the original input.
- **The `DEMO:` payload is Base64 demo encoding, not AES ciphertext.** The nonce and
  fingerprint are fictional demo strings. No real key is generated or validated.
- Reset/rotation controls reset or replay the simulated workflow. This is not a
  demonstration of actual cryptographic security or a live Server response.

### Server Monitoring

- Metrics, sessions, charts, transfers and logs are all explicitly simulated.
- Automatic traffic updates every two seconds. Pause stops automatic traffic;
  manual scenario actions and explicit transfer/reply animations still work.
- Run a scenario to generate an incoming message, a file transfer, or presence change.
- Switch charts between traffic, messages and connections; toggle traffic series,
  hover points, filter online sessions, filter/search logs, or clear the event log.
- Counters derive from the same shared event state: connections match online rows,
  active transfers match transfer statuses, and cumulative bytes match session totals.

## Verification and screenshots

```bash
python3 scripts/demo.py verify
```

This loads **the packaged JAR’s** FXML/CSS using the real JavaFX toolkit, drives UI
controls and mock services, checks state invariants and captures screenshots at
1280×800 and 1024×700. It does not run any legacy backend tests or network services.
Results and remaining manual checks: [VERIFICATION.md](VERIFICATION.md).

- [Chat](docs/screenshots/chat.png)
- [Encryption Lab — full content](docs/screenshots/encryption-lab.png)
- [Server Monitoring — full content](docs/screenshots/server-monitoring.png)
- [Username login](docs/screenshots/login.png)
- [Minimum-width chat](docs/screenshots/chat-minimum.png)

The supplied attachment contained the written design brief, but no reference image
files. Visuals follow its palette, layout and interaction requirements; pixel-for-pixel
matching against the three referenced images was not possible.

[Architecture](ARCHITECTURE.md) · [Components and design tokens](COMPONENTS.md)
