# English Java type names

Java classes and interfaces use descriptive English names in PascalCase. File names,
constructors, imports, references, test commands and related documentation were
updated together.

| Previous name | Current name | Purpose |
|---|---|---|
| `KetNoiClient` | `ClientConnection` | Client connection and protocol operations |
| `KetQuaDemChuCai` | `LetterFrequency` | Letter counts and result formatting |
| `GoiTin` | `PacketIO` | Protocol packet input/output |
| `KiemTraCommon` | `CommonSelfTest` | Shared utility self-tests |
| `ChupAnh` | `ScreenshotCapture` | Screenshot capture utility |
| `PhienClient` | `ClientSession` | Server-side client session |
| `BoNghe` | `NetworkEventHandler` | Swing client network event handler |
| `NguoiNghe` | `ConnectionListener` | Connection event listener interface |

This cleanup changes type names only. Method and field names, Vietnamese UI text,
protocol behavior and existing backend functionality are preserved. The JavaFX
preview already uses English type names and remains an independent mock-first module.

## Validation

Run from the repository root:

```bash
python3 scripts/test.py
cd javafx-preview
python3 scripts/demo.py verify
```

The existing regression suite passed after the rename on 2026-11-01:
`ALL AUTOMATED NETWORK TESTS OK`. It covers shared utilities, multi-user integration,
fault handling, independent JVM clients, and legacy connection/file-transfer flows.
These tests use temporary local servers and data.

The JavaFX verifier independently checks packaged resources, navigation, mock
interactions and monitoring consistency. It also passed on 2026-11-01 with
`JAVAFX PREVIEW VERIFICATION OK` (31 checks). Historical preservation hashes in the
JavaFX report describe the earlier frontend-only checkpoint, before this authorized
naming cleanup.
