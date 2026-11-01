# Verification — 2026-11-01

## Actual environment

- macOS Apple Silicon (`arm64` / Java `aarch64`).
- Oracle Java 21.0.7; OpenJFX 21.0.13.
- Maven 3.9.11, Python 3.9.
- Native JavaFX toolkit, FXML, CSS and real Stage/Scene rendering; no embedded browser.

## Commands actually run

```bash
python3 scripts/demo.py verify
```

Result: `JAVAFX PREVIEW VERIFICATION OK`. The actual output is retained at
[docs/verification/javafx-verification.log](docs/verification/javafx-verification.log).
The verifier loads application classes **and FXML/CSS from the packaged JAR**,
with test-driver classes kept separate. This verifies packaged resources rather
than merely loading loose source resources.

### Passed automated checks

- Seed traffic reconciles with cumulative fixture byte counters.
- Mock login and empty-composer disabled state.
- Shift+Enter newline, Enter send, immediate outgoing message, delivery transitions
  and scripted incoming reply.
- Active conversation unread behavior; search empty state; offline delivery failure
  and retry after presence change.
- Attachment metadata/progress, cancellation, retry and completed state.
- All screen FXML layouts and navigation.
- Lab disabled before exchange, simulated RSA workflow, `DEMO:` payload/nonce labels,
  local Unicode letter totals, chart/table population and original-input reveal.
- Monitoring pause/resume controls and actual automatic timer pause.
- Connections versus session rows; sent/received bytes versus session totals;
  active-transfer count versus transfer states.
- Traffic/messages/connections chart modes, online-session filtering, log search
  empty state and severity filtering.
- Log clear and retention of at most 250 entries.
- Sign out and stopping scheduled demo jobs on close.
- Real screenshots at 1280×800 and 1024×700; the detail panel collapses at narrow width.
- No FXML, CSS, native-library loading errors or asynchronous JavaFX exceptions
  appeared in the passing verification output.

### Packaged entry point

The generated `target/demo/run-demo.sh --monitor` was started as a separate process.
It remained running after native startup, with no FXML/CSS/native-library loading
errors or exceptions in its captured output, and was then closed. This exercised
the actual standalone PreviewLauncher / PreviewApplication entry point.

### Boundary/preservation checks

During the completed mock-only JavaFX iteration, all **36** captured source,
configuration and dependency files remained byte-for-byte unchanged. The historical baseline
is retained at [docs/verification/preserved-files.sha256](docs/verification/preserved-files.sha256).
No existing Swing/TCP code was refactored during that UI iteration.

A subsequent user-requested naming cleanup renamed Vietnamese Java types to English,
updated references, and ran the existing regression suite. See [CLASS_NAMING.md](../docs/CLASS_NAMING.md).
The historical hashes describe the earlier UI-only checkpoint, not the post-rename files.

The preview source has no imports of the legacy packages, `java.net`, `javax.crypto`,
`java.security`, or `java.sql`. Controllers/components have no mock-implementation
imports. Dependencies are only JavaFX base/graphics/controls/fxml. No legacy Server,
crypto test, JDBC operation or backend integration test was started in this iteration.

## Visual evidence

Screenshots were captured by JavaFX node snapshots; no screen-recording permission
or post-processing was needed. Full-content Lab/Monitoring images include the
vertically scrollable sections, not a claim that all content fits above the fold.

| Screen | Evidence |
|---|---|
| Username login | [login.png](docs/screenshots/login.png) |
| Chat, initial | [chat.png](docs/screenshots/chat.png) |
| Chat interactions | [chat-interactions.png](docs/screenshots/chat-interactions.png) |
| Encryption Lab, complete content | [encryption-lab.png](docs/screenshots/encryption-lab.png) |
| Server Monitoring, complete content | [server-monitoring.png](docs/screenshots/server-monitoring.png) |
| Chat at minimum width | [chat-minimum.png](docs/screenshots/chat-minimum.png) |
| Lab at minimum width | [lab-minimum.png](docs/screenshots/lab-minimum.png) |
| Monitor at minimum width | [monitor-minimum.png](docs/screenshots/monitor-minimum.png) |

Visual review caught and corrected a truncated sidebar promo, horizontal conversation
scrollbar, overlapping traffic traces, crowded session header, toast height, narrow-chart
labels and selected-conversation unread behavior.

## Still manual / not claimed as passed

- Native OS FileChooser selection/cancel via mouse, and clipboard copy/paste in another app.
- Complete keyboard-only and screen-reader audit.
- Windows/Linux rendering, native-library loading, font substitution and display scaling.
- Pixel matching to the three original reference screenshots (only the written brief
  was supplied; no reference image files were attached).

There is deliberately no LAN, encryption, database or real file-delivery acceptance
result. Those are outside this frontend-only iteration. Restart clears demo state.
