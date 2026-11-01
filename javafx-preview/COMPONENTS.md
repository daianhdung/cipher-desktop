# UI design system and reusable components

## Design tokens

The tokens are JavaFX looked-up colors in `theme.css` (not browser CSS variables).

| Token | Value | Use |
|---|---|---|
| `-canvas` | `#F7F9FC` | Workspace and scrolling content background |
| White | `#FFFFFF` | Sidebar, header, cards and incoming bubbles |
| `-accent` | `#4F6EF7` | Primary action, outgoing bubble, selection and progress |
| `-accent-soft` | `#EDF1FF` | Selected rows and quiet accent surfaces |
| `-ink` | `#1F2937` | Primary content |
| `-muted` | `#7C879C` | Secondary copy |
| `-line` | `#E9EDF4` | Dividers and card boundaries |
| `-green` | `#2FA67E` | Online/available states, not proof of security |

8px-based spacing, 8–13px corner radii and a system font keep the desktop UI coherent.
Monospaced technical fields use Monaco with the JavaFX platform fallback when absent.
No web renderer, downloaded font, image-generation asset or icon framework is required.

## Library

| Component | Purpose |
|---|---|
| `Icon` | Native SVGPath line icons; hover/focus colors inherit CSS |
| `Avatar` | Initials and property-bound online/offline indicator |
| `PersonCell` | Selected/hover conversation row, preview, unread count and avatar |
| `MessageBubble` | Incoming/outgoing alignment, timestamp, delivery or transfer state |
| `AttachmentCard` | File metadata, property-bound progress, details/cancel/retry actions |
| `MetricCard` | Icon, title, bound counter and concise interpretation |
| `LogViewer` | Search/level filters, bounded source list, timestamps and event categories |
| `Ui` helpers | Badges, tooltips, icon buttons, empty state, dialogs and action menus |
| Shell FXML | Shared rail, header, Demo Mode badge, username overlay and toast region |

Major layouts remain in FXML using standard JavaFX controls; dynamic list items
and small reusable components use Java. Styling is external CSS, not inline style strings.
FXML files can be opened as layouts in Scene Builder; injected controllers are
created by the application’s controller factory at runtime.

## Responsive behavior

The initial Scene is 1280×800; the Stage minimum is 1024×700. Chat uses a 272px
conversation panel, a flexible center and a 232px detail panel. Details hide when
the chat content width falls below 1110px (about 1190px outer width); the info action
provides a dialog at narrow widths. Chat history and conversations scroll separately.
Lab and Monitoring use vertical ScrollPane layouts. All sections remain reachable
at minimum size; neither screen depends on placing every section above the fold.
Charts use short relative-time labels to avoid rotated or overlapping time labels.

## Interaction/accessibility

Navigation and icon actions have accessible names/tooltips. Keyboard focus is visible.
Enter sends, Shift+Enter adds a newline, and empty/invalid actions are disabled or
explain the problem. Offline, sending, read, failed, cancelled, busy and empty states
are represented explicitly. Status never relies only on color.
Demo Mode remains visible across all screens. Illustrative crypto output has both
a banner and inline disclaimers. No UI claims actual file integrity or confidentiality.
