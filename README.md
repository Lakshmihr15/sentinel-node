# SentinelNode

**Distributed worker monitor and remote task orchestrator** — built in Java + Swing for CS6103.

Authors: Lakshmi Hukunda Raju (lh4140) · Harshith Kori Raj (hk4488)

SentinelNode is a two-app system: a **manager** that accepts persistent TCP connections from
**workers**, ships them tasks, monitors live telemetry, exchanges messages, and persists everything to
SQLite. Manager and worker each have their own Swing UI with a distinct theme so they read clearly
side-by-side in a demo.

## Features

- **Manager dashboard** (slate / cyan theme) — login, live worker table with filter/search,
  per-worker live charts (CPU / memory / process CPU), status pills, status bar.
- **Worker GUI** (graphite / emerald theme) — large telemetry readouts, current task panel with
  progress bar, inbox for manager notes with reply box.
- **Notes / messaging** — manager → worker (broadcast, by tag, or by id) and worker → manager.
  Notes persist; pending notes flush automatically when a worker reconnects.
- **Resource management** — first-class panel with sub-tabs:
  - **Workers** – provision worker accounts, auto-generated tokens, copy join command, revoke / restore.
  - **Sessions** – live sessions, kick, ban (with reason), unban.
  - **Templates** – named task templates (CALC / SEARCH / SLEEP / HASH / PRINT) used by the dispatch panel.
  - **Tags** – tag workers; dispatch tasks or notes to all members of a tag.
  - **Quotas** – review quota top-up requests from workers and grant credits.
- **Resource quotas** — every worker starts with a configurable credit budget (default 10). Each task
  costs credits proportional to its payload (e.g. `CALC 5_000_000` → 5 credits). When a task would
  exceed the budget the worker rejects it with a "resources exhausted" error and emits a
  `QUOTA_REQUEST` to the manager; the manager grants from the **Resources → Quotas** sub-tab.
- **Analytics** – filtered queries over the SQLite event log + one-click CSV export.
- **Polish** – toast notifications, ⌘/Ctrl-1..5 to jump between tabs, themed login / register / about
  dialogs (no JOptionPane abuse), keyboard accelerators, status bar with live clock and DB size.

## Advanced topics used (5)

The CS6103 final-project rubric asks for ≥ 3 advanced topics. SentinelNode demonstrates **five**.

### 1. Distributed systems & networking — TCP sockets, custom protocol
- `ServerSocket` accept loop in
  [`ManagerController.java`](src/main/java/com/finalproject/manager/ManagerController.java)
  spawns one `WorkerSession` per connection. Each worker holds a long-lived TCP socket via
  `Socket` in
  [`WorkerClient.java`](src/main/java/com/finalproject/worker/WorkerClient.java).
- A custom line-delimited text protocol (`TYPE|key=value;key=value`) lives in
  [`MessageCodec.java`](src/main/java/com/finalproject/net/MessageCodec.java);
  values are URL-encoded so notes can carry spaces/quotes/newlines safely. 14 distinct message
  types are listed in
  [`MessageTypes.java`](src/main/java/com/finalproject/net/MessageTypes.java).
- Heartbeats: `PING/PONG` every 15 s; sessions that miss the window for 30 s are auto-evicted
  (scheduled via `ScheduledExecutorService` in `ManagerController`).
- Workers can self-register over the wire (`REGISTER` / `REGISTER_OK` / `REGISTER_FAILED`) — see
  [`WorkerRegistrar.java`](src/main/java/com/finalproject/worker/WorkerRegistrar.java).

### 2. Concurrency & transactional atomicity
- `Executors.newCachedThreadPool` runs the manager's accept loop, every worker session, and each
  worker's metric loop + task executor in parallel.
- **Atomic task assignment**: `AtomicBoolean taskLock.compareAndSet(false, true)` in
  [`WorkerClient.java`](src/main/java/com/finalproject/worker/WorkerClient.java) +
  `synchronized` block on the session in
  [`WorkerRegistry.sendTask(...)`](src/main/java/com/finalproject/manager/WorkerRegistry.java)
  — guarantees no double-dispatch even if two manager UI threads call `sendTask` concurrently.
- `ConcurrentHashMap` for live session/snapshot maps; `CopyOnWriteArrayList` for note/quota
  listener fan-out; `volatile` for inter-thread visibility flags. All UI updates marshalled
  through `SwingUtilities.invokeLater` so the EDT is never blocked by network I/O.
- **Quota / resource management**: each worker tracks credits in an `AtomicInteger`; the
  cost-vs-credits check in `WorkerClient.receiveTask(...)` is the canonical
  reserve-or-reject-then-refund-on-failure flow that the rubric calls "transactional atomicity".

### 3. Persistence (JDBC + SQLite)
- `org.xerial:sqlite-jdbc:3.46.0.0` (declared in [`pom.xml`](pom.xml)).
- Two databases — `workforce.db` (auth) and `sentinelnode.db` (events / metrics / notes /
  templates / tags / bans / quota_requests). Schema + additive migrations in
  [`AppDatabase.java`](src/main/java/com/finalproject/db/AppDatabase.java) and
  [`DatabaseManager.java`](src/main/java/com/finalproject/db/DatabaseManager.java).
- All writes go through `PreparedStatement` (no string concatenation → injection-safe).
- Telemetry export: one-click CSV from
  [`AnalyticsPanel.java`](src/main/java/com/finalproject/ui/manager/AnalyticsPanel.java).
- Pending notes survive disconnect — `NotesService.pendingFor(workerId)` flushes on reconnect.
- Quota requests are persisted with the originating task type/payload so the manager can
  **auto-replay** the rejected task when it grants credits.

### 4. GUI & custom graphics
- Two distinct Swing apps with separate themes — slate/cyan
  [`ManagerTheme.java`](src/main/java/com/finalproject/ui/theme/ManagerTheme.java)
  and graphite/emerald
  [`WorkerTheme.java`](src/main/java/com/finalproject/ui/theme/WorkerTheme.java) —
  driven by a `Theme` interface and a `UIFactory` so the two apps share styling primitives
  while reading visibly different.
- Custom `Graphics2D` live charts in
  [`MetricChartPanel.java`](src/main/java/com/finalproject/ui/MetricChartPanel.java):
  three stacked panels (CPU%, Memory%, Proc CPU ms) with antialiased lines, soft fill under
  each curve, latest-value dot, auto-scaling Y axis, tick labels, 2 Hz repaint.
- Per-cell renderers in the worker table
  ([`WorkerTablePanel.java`](src/main/java/com/finalproject/ui/manager/WorkerTablePanel.java))
  for live progress bars, status pills, percent formatting; toast overlay on the layered pane
  ([`ToastOverlay.java`](src/main/java/com/finalproject/ui/manager/ToastOverlay.java)).
- Manager dashboard decomposed into 12 focused panel classes (Login, Register, Setup, Notes,
  Analytics, Resources with five sub-tabs, etc.) under
  [`ui/manager/`](src/main/java/com/finalproject/ui/manager/).

### 5. Security & authentication
- **PBKDF2-HMAC-SHA256** in
  [`PasswordService.java`](src/main/java/com/finalproject/auth/PasswordService.java) —
  120 000 iterations, 256-bit derived key, 16-byte per-password salt from `SecureRandom`.
- **Server-side pepper** read from `$APP_PEPPER` — leaked DB alone can't crack passwords.
- Versioned hash format `v1$<iterations>$<salt>$<hash>` allows future rotation without
  invalidating existing accounts.
- **Constant-time hash comparison** to defeat timing attacks.
- **Bearer-token auth for workers** — `SecureRandom` 18-byte token, ~144 bits of entropy
  (`AuthService.generateToken`); workers send the token in `HELLO`, manager validates against
  the user store and the ban list.
- **Soft delete via `revoked_at`** instead of `DELETE` — preserves audit trail.
- **Ban list** at the HELLO check — banned users are rejected with `AUTH_FAILED` before
  registry registration.
- Quotas + the auto-replay-on-grant flow demonstrate **resource control** layered on top of
  authentication.

## Architecture (high level)

```
src/main/java/com/finalproject/
  app/                  # Main entry point + AppConfig
  auth/                 # password hashing, login, token management
  db/                   # SQLite schema + queries (events, metrics, notes, templates, tags, bans)
  manager/              # ManagerController, WorkerRegistry, WorkerSession + services
    bans/   tags/   templates/
  net/                  # Message + MessageCodec + MessageTypes constants
  notes/                # NotesService + Note record
  repository/           # UserRepository (auth)
  ui/
    theme/              # Theme, ManagerTheme, WorkerTheme, UIFactory, UIInsets
    manager/            # all manager panels (Dashboard, Notes, Resources, Analytics, …)
    worker/             # WorkerFrame + worker panels
    MetricChartPanel    # shared chart, themed for both apps
  worker/               # WorkerClient + WorkerEvent listeners + telemetry sampling
```

## Wire protocol

Plain text over TCP, line-delimited:
```
TYPE|key1=value1;key2=value2;…
```
Values are URL-encoded so notes can carry spaces/quotes/newlines safely.

| Type            | Direction | Notes                                                |
|-----------------|-----------|------------------------------------------------------|
| HELLO           | W → M     | username + token (manager rejects banned users)      |
| METRIC          | W → M     | every `metric.interval.ms`                           |
| TASK            | M → W     | with payload                                         |
| TASK_ACCEPTED   | W → M     |                                                       |
| TASK_PROGRESS   | W → M     | 0–100                                                 |
| TASK_DONE       | W → M     |                                                       |
| TASK_FAILED     | W → M     |                                                       |
| PING / PONG     | M ⇄ W     | heartbeat                                            |
| NOTE            | both      | id + body; receiver replies NOTE_ACK                 |
| NOTE_ACK        | both      | acks delivery                                        |
| KICK            | M → W     | manager-driven disconnect                             |
| AUTH_FAILED     | M → W     | banned / revoked accounts                            |
| QUOTA_REQUEST   | W → M     | worker has insufficient credits for a dispatched task |
| QUOTA_GRANT     | M → W     | manager tops up worker credits                        |

## Quick start (1-machine demo)

```bash
export APP_PEPPER='local-dev-pepper'
./run-demo.sh
```

This starts the manager (GUI) and two worker GUIs (`alice`, `bob`).

To stop everything: `./run-demo.sh --kill`. To run workers headless: `./run-demo.sh --headless`.

## Manual run

```bash
export APP_PEPPER='local-dev-pepper'

# Manager UI
mvn -q exec:java

# Manager headless server only
mvn -q exec:java -Dexec.args='server'

# Worker GUI (prompts for credentials + manager host/port)
mvn -q exec:java -Dexec.args='worker-ui worker-3'

# Worker headless (legacy mode)
WORKER_USERNAME=alice mvn -q exec:java -Dexec.args='worker worker-3 127.0.0.1 6000'
```

## Configuration

Defaults can be overridden via `~/.sentinelnode/config.properties`, system properties, or
environment variables (env wins → system → file → default):

| Property               | Env var               | Default                    |
|------------------------|-----------------------|----------------------------|
| `manager.host`         | `SENTINEL_HOST`       | `127.0.0.1`                |
| `manager.port`         | `SENTINEL_PORT`       | `6000`                     |
| `app.db.url`           | `SENTINEL_APP_DB`     | `jdbc:sqlite:sentinelnode.db` |
| `auth.db.url`          | `SENTINEL_AUTH_DB`    | `jdbc:sqlite:workforce.db` |
| `metric.interval.ms`   | `SENTINEL_METRIC_MS`  | `1000`                     |
| `heartbeat.seconds`    | `SENTINEL_HEARTBEAT_S`| `15`                       |
| `stale.threshold.ms`   | `SENTINEL_STALE_MS`   | `30000`                    |
| —                      | `WORKER_CREDITS`      | `10` (worker starting budget) |

## Provisioning workers from the manager

1. Sign in as a manager.
2. Open **Resources → Workers → Add worker**.
3. Pick a username + initial password — a token is auto-generated and a one-line `WORKER_USERNAME=…
   WORKER_TOKEN=… mvn -q exec:java -Dexec.args='worker-ui'` join command is copied to your
   clipboard. Paste it into a new terminal to bring up that worker's GUI.

## Tests

```bash
export APP_PEPPER='set-a-strong-secret-pepper'
mvn test
```

**84 tests pass** across 13 test classes covering: `AuthServiceTest`, `PasswordServiceTest`,
`MessageCodecTest`, `WorkerRegistryTest`, `WorkerSnapshotTest`, `WorkerTaskRunnerTest`,
`AppConfigTest`, `NotesServiceTest`, `TemplateServiceTest`, `TagServiceTest`, `BanServiceTest`,
`QuotaServiceTest`, `TaskCostTest`.

## Requirements

- **Java 17** (any 17+ JDK works — Temurin, OpenJDK, Oracle, Amazon Corretto).
- **Maven 3.6+**.
- macOS / Linux / Windows. Demo script `run-demo.sh` is bash; on Windows use the manual
  commands instead.

## Submission

- **GitHub:** https://github.com/Lakshmihr15/sentinel-node
- **One-line run:** `export APP_PEPPER='local-dev-pepper' && ./run-demo.sh`
- **One-line tests:** `export APP_PEPPER='local-dev-pepper' && mvn test`

No external dataset, no API keys, no Docker required. Everything is in-tree; the SQLite files
are created on first run.