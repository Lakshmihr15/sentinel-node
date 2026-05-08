package com.finalproject.app;

import com.finalproject.auth.AuthService;
import com.finalproject.auth.PasswordService;
import com.finalproject.db.AppDatabase;
import com.finalproject.db.DatabaseManager;
import com.finalproject.manager.ManagerController;
import com.finalproject.repository.UserRepository;
import com.finalproject.ui.ManagerFrame;
import com.finalproject.ui.theme.ManagerTheme;
import com.finalproject.ui.theme.UIFactory;
import com.finalproject.ui.theme.WorkerTheme;
import com.finalproject.ui.worker.WorkerFrame;
import com.finalproject.ui.worker.WorkerLoginDialog;
import com.finalproject.worker.WorkerClient;

import javax.swing.SwingUtilities;
import java.util.Locale;

public class Main {
    public static void main(String[] args) {
        AppConfig config = AppConfig.load();
        String mode = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "manager";

        switch (mode) {
            case "worker"     -> runHeadlessWorker(args, config);
            case "worker-ui"  -> runWorkerUi(args, config);
            case "server"     -> runManagerHeadless(config);
            default            -> runManagerUi(config);
        }
    }

    private static void runHeadlessWorker(String[] args, AppConfig config) {
        String workerId = args.length > 1 ? args[1] : defaultWorkerId();
        String host = args.length > 2 ? args[2] : config.managerHost();
        int port = args.length > 3 ? parsePort(args[3], config.managerPort()) : config.managerPort();
        new WorkerClient(workerId, host, port).run();
    }

    private static void runWorkerUi(String[] args, AppConfig config) {
        String workerId = args.length > 1 ? args[1] : defaultWorkerId();
        SwingUtilities.invokeLater(() -> {
            UIFactory.applyGlobalLookAndFeel(WorkerTheme.INSTANCE);
            String envUsername = System.getenv().getOrDefault("WORKER_USERNAME",
                System.getProperty("worker.username", ""));
            String envToken = System.getenv().getOrDefault("WORKER_TOKEN",
                System.getProperty("worker.token", ""));
            String username;
            String token;
            String host = config.managerHost();
            int port = config.managerPort();
            if (!envUsername.isBlank()) {
                // Pre-supplied via env (run-demo.sh path) — skip the dialog and connect.
                username = envUsername;
                token = envToken;
            } else {
                WorkerLoginDialog dialog = new WorkerLoginDialog(null, WorkerTheme.INSTANCE,
                    config, envUsername, envToken);
                dialog.setVisible(true);
                if (!dialog.confirmed()) {
                    return;
                }
                username = dialog.username();
                token = dialog.token();
                host = dialog.host();
                port = dialog.port();
            }
            WorkerClient client = new WorkerClient(workerId, host, port,
                username, token, config.metricIntervalMs());
            WorkerFrame frame = new WorkerFrame(client, config);
            frame.setVisible(true);
            new Thread(client, "worker-" + workerId).start();
        });
    }

    private static void runManagerHeadless(AppConfig config) {
        AppDatabase database = new AppDatabase(config.appJdbcUrl());
        database.initialize();
        DatabaseManager authDb = new DatabaseManager(config.authJdbcUrl());
        authDb.initializeSchema();
        AuthService authService = new AuthService(new UserRepository(authDb), new PasswordService());
        ManagerController controller = new ManagerController(config, database, authService);
        controller.startServer(config.managerPort());
        System.out.println("SentinelNode manager listening on port " + config.managerPort());
    }

    private static void runManagerUi(AppConfig config) {
        AppDatabase database = new AppDatabase(config.appJdbcUrl());
        database.initialize();
        DatabaseManager authDb = new DatabaseManager(config.authJdbcUrl());
        authDb.initializeSchema();
        AuthService authService = new AuthService(new UserRepository(authDb), new PasswordService());
        ManagerController controller = new ManagerController(config, database, authService);
        SwingUtilities.invokeLater(() -> {
            UIFactory.applyGlobalLookAndFeel(ManagerTheme.INSTANCE);
            new ManagerFrame(controller, authService).setVisible(true);
        });
    }

    private static String defaultWorkerId() {
        return "worker-" + Long.toHexString(System.nanoTime() & 0xFFFFFF);
    }

    private static int parsePort(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return fallback; }
    }
}
