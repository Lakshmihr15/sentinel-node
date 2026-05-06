package com.finalproject.app;

import com.finalproject.db.AppDatabase;
import com.finalproject.manager.ManagerController;
import com.finalproject.ui.ManagerFrame;
import com.finalproject.worker.WorkerClient;

import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        if (args.length > 0 && "worker".equalsIgnoreCase(args[0])) {
            String workerId = args.length > 1 ? args[1] : "worker-1";
            String host = args.length > 2 ? args[2] : "127.0.0.1";
            int port = args.length > 3 ? Integer.parseInt(args[3]) : 6000;
            new WorkerClient(workerId, host, port).run();
            return;
        }

        AppDatabase database = AppDatabase.defaultDatabase();
        database.initialize();

        // Initialize workforce auth database and service and pass to controller
        com.finalproject.db.DatabaseManager workforceDb = new com.finalproject.db.DatabaseManager("jdbc:sqlite:workforce.db");
        workforceDb.initializeSchema();
        com.finalproject.auth.AuthService authService = new com.finalproject.auth.AuthService(new com.finalproject.repository.UserRepository(workforceDb), new com.finalproject.auth.PasswordService());

        ManagerController controller = new ManagerController(database, authService);

        if (args.length > 0 && "server".equalsIgnoreCase(args[0])) {
            controller.startServer(6000);
            System.out.println("SentinelNode manager server listening on port 6000.");
            return;
        }

        SwingUtilities.invokeLater(() -> new com.finalproject.ui.ManagerFrame(controller, authService).setVisible(true));
    }
}
