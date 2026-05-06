# SentinelNode — Distributed System Monitor & Remote Task Orchestrator

Authors: Lakshmi Hukunda Raju (lh4140) | Harshith Kori Raj (hk4488)

Short description

SentinelNode implements a Manager server and Worker clients. The Manager accepts persistent TCP connections from Workers, collects telemetry, persists metrics and events to SQLite, and provides a Swing GUI for monitoring, analytics, and task dispatch.

Prerequisites

- Java 17 (JDK)
- Maven
- (Optional) sqlite3 CLI for inspecting DB files

Quick start (demo)

1. Set the application pepper (used for password hashing):

```bash
export APP_PEPPER='local-dev-pepper'
```

2. Start the Manager (GUI):

```bash
mvn exec:java
```

3. In the Manager GUI create users via Account -> User Management (create a `MANAGER` account for dashboard access and worker accounts).

4. (Optional) Provision a token for a worker (so the worker authenticates automatically). Example using sqlite3:

```bash
TOKEN="demo-token-123"
sqlite3 workforce.db "UPDATE users SET token='$TOKEN' WHERE username='worker1';"
```

5. Start worker processes in separate terminals. Provide username and/or token via environment variables or system properties:

```bash
# Worker using username (display name):
export WORKER_USERNAME=alice
mvn exec:java -Dexec.args='worker worker-1 127.0.0.1 6000'

# Worker authenticating via pre-provisioned token:
export WORKER_TOKEN=demo-token-123
mvn exec:java -Dexec.args='worker worker-2 127.0.0.1 6000'
```

Run tests and build

```bash
export APP_PEPPER="set-a-strong-secret-pepper"
mvn test
```

Databases

- `sentinelnode.db` — stores worker events, metrics, and task events.
- `workforce.db` — stores user accounts (salted+peppered password hashes, role, optional token).

How analytics are produced

- Each Worker periodically samples telemetry and sends `METRIC` messages to the Manager. Fields include CPU, memory, JVM heap used, thread count, process CPU time, current task type/id, and progress.
- ManagerController updates an in-memory `WorkerSnapshot` and persists the metric into `sentinelnode.db` via `AppDatabase.logMetric(...)`.
- The Swing UI (`MetricChartPanel`) reads `WorkerSnapshot.history()` and renders charts.

Why metrics may look identical on one laptop

- By default host-level metrics look the same for multiple processes on the same machine. To show distinct metrics per worker on a single host, the project samples per-process/JVM metrics (heap used, thread count, process CPU time) — these will differ between worker JVMs even on the same laptop.

Advanced topics used (pick at least 3)

1. Distributed Systems & Networking
	- Custom text message protocol (`Message`/`MessageCodec`) exchanged over TCP sockets.
	- Persistent worker sessions and message handlers for remote orchestration.
	- Files: `manager/WorkerSession.java`, `worker/WorkerClient.java`, `net/MessageCodec.java`.

2. Concurrency & Synchronization
	- `ExecutorService` for concurrent sessions and metric loops.
	- `AtomicBoolean` for single-task execution lock; synchronized snapshots for safe UI reads.
	- Files: `WorkerClient.java`, `WorkerRegistry.java`, `model/WorkerSnapshot.java`.

3. Security & Authentication
	- PBKDF2WithHmacSHA256 password hashing with per-password salts and an application-level pepper (`APP_PEPPER`).
	- Pre-provisioned token authentication for workers; Manager validates tokens against `workforce.db`.
	- Files: `auth/PasswordService.java`, `auth/AuthService.java`, `repository/UserRepository.java`.

4. Persistence & Observability
	- SQLite stores telemetry and events to enable analytics and replay.
	- Files: `db/AppDatabase.java`, `ui/MetricChartPanel.java`.

5. GUI & Visualization
	- Swing-based dashboard with worker table, charts, dispatch UI, and management dialogs.
	- Files: `ui/ManagerFrame.java`, `ui/MetricChartPanel.java`.

Demo video

- A short video showing Manager login, creating users, starting workers with different usernames or tokens, viewing distinct telemetry, and dispatching tasks is highly recommended. Record using any screen capture tool and attach when submitting.

Group submission guidelines

- If you worked in a group, put all members' names and netIDs at the top of this README. Have one person submit the project; other members must submit a text confirmation on Brightspace stating who submitted (format: "Name netID submitted on behalf of group").

Runnable checklist

- `mvn test` — run unit tests
- `mvn exec:java` — launch Manager GUI
- `mvn exec:java -Dexec.args='worker <id> <host> <port>'` — launch worker
- Set `APP_PEPPER` before creating users or registering

Optional next steps I can add for convenience

- `run-demo.sh` script to launch Manager and two workers automatically (background processes)
- Dockerfile and docker-compose for launching Manager + multiple Workers in containers (recommended for isolated metrics)

Where to find code (high level)

- `src/main/java/com/finalproject/worker` — Worker client, telemetry, and metrics
- `src/main/java/com/finalproject/manager` — Manager controller, registry, and session handling
- `src/main/java/com/finalproject/ui` — Swing dashboard and chart visualization
- `src/main/java/com/finalproject/db` — SQLite access and schema
- `src/main/java/com/finalproject/auth` — Authentication services and password hashing

If you want, I will now add `run-demo.sh` and a `Dockerfile`/`docker-compose.yml`. Which would you like first?

---

Authors: Lakshmi Hukunda Raju (lh4140) and Harshith Kori Raj (hk4488).
