# Cookbook: adding a web surface

How to extend the Session Workbench — the browser client at `harness-core/src/main/resources/web/index.html` — with a new control, modal, or live view. The page is a single self-contained HTML file (vanilla JS + CSS, no build step), served from classpath by both the Spring MVC adapter and the framework-free `HarnessHttpServer`.

## 1. Understand the data flow

- The page reads session state over the REST API and receives **all** live updates over one SSE stream (`GET /sessions/{id}/stream`), which replays the log on connect and then follows appended events.
- Every event carries `{sequence, time, type, data}`. The renderer switches on `type`: `user/message`, `assistant/chunk`, `assistant/message`, `tool/call`, `tool/result`, `turn/start`, `step/start`, `step/end`, `turn/end`.
- Derived/aggregate data comes from dedicated endpoints: `GET /sessions/{id}/messages` (model messages) and `GET /sessions/{id}/projection` (pressure/breakdown/usage/ledger).

## 2. Pick your surface

- **New header control** — add a `.capsule` button in the header actions row (next to `Context` / `Session log`) and bind it in the IIFE.
- **New modal** — reuse the `#modal-root` shell: set title/description, put HTML into `#modal-body`, open/close with `openModal`/`closeModal`. `Escape` and the backdrop close it.
- **New live view** — extend `renderEvent`'s `switch` (or add event types end-to-end: core event constant → validator → renderer).
- **New composer affordance** — the composer row already hosts the context meter; add adjacent elements before `#meterInput`.

## 3. Style with the design tokens

Use the CSS variables declared in `:root` (`--bg`, `--bg-raised`, `--border`, `--text*`, `--accent`, `--tint-*`) rather than raw colors, so dark-theme consistency is automatic.

## 4. Test it

- **Behavioral**: the Spring integration test asserts the page is served and contains the workbench markers; add assertions for new static controls.
- **Event rendering** depends on server-driven events — cover new render paths by driving the session with the public API (append an event, assert the SSE or REST body), not by running a browser in unit tests.
- **Keep the page framework-free**: no build step, no external CDN scripts; the workbench must work offline from the classpath.

## 5. Common pitfalls

- **XSS**: escape user/tool content with `esc()` before injecting HTML; formatters that return strings used in `innerHTML` must escape.
- **SSE replay buffering**: the stream replays the whole log on connect; the client should treat any event as additive and idempotent — avoid re-rendering derived UI from replayed events only (projection endpoint is the source for aggregates).
- **Windows CI golden diffs are not applicable here** (no Playwright goldens in this project), but keep `git diff --check` clean and the file ending with exactly one newline.