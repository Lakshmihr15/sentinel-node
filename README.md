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
  - **Templates** – named task templates (CALC / SEARCH / SLEEP) used by the dispatch panel.
  - **Tags** – tag workers; dispatch tasks or notes to all members of a tag.
- **Analytics** – filtered queries over the SQLite event log + one-click CSV export.
- **Polish** – toast notifications, ⌘/Ctrl-1..5 to jump between tabs, themed login / register / about
  dialogs (no JOptionPane abuse), keyboard accelerators, status bar with live clock and DB size.

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

## Demo video script (5 min)

The plan file [.claude/plans/jazzy-shimmying-hamster.md](.claude/plans/jazzy-shimmying-hamster.md)
contains the recording script with timing — covers login, dashboard, worker GUI, resource
provisioning, notes, tag-targeted dispatch, kick + revoke, CSV export.
