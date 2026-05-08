package com.finalproject.ui.manager;

import com.finalproject.manager.ManagerController;
import com.finalproject.manager.tags.TagService;
import com.finalproject.model.WorkerSnapshot;

import javax.swing.table.AbstractTableModel;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WorkerTableModel extends AbstractTableModel {
    public static final int COL_USER = 0;
    public static final int COL_HOST = 1;
    public static final int COL_STATUS = 2;
    public static final int COL_TASK = 3;
    public static final int COL_PROGRESS = 4;
    public static final int COL_CPU = 5;
    public static final int COL_MEMORY = 6;
    public static final int COL_TAGS = 7;
    public static final int COL_LAST_SEEN = 8;
    public static final int COL_UPTIME = 9;

    private static final String[] COLUMNS = {
        "User", "Host", "Status", "Task", "Progress", "CPU %", "Memory %", "Tags", "Last Seen", "Uptime"
    };
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ManagerController controller;
    private final TagService tagService;
    private List<WorkerSnapshot> rows = new ArrayList<>();
    private String filter = "";

    public WorkerTableModel(ManagerController controller) {
        this.controller = controller;
        this.tagService = controller.tags();
    }

    public void setFilter(String filter) {
        this.filter = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        refresh();
    }

    public void refresh() {
        List<WorkerSnapshot> all = controller.registry().snapshots();
        if (filter.isEmpty()) {
            rows = all;
        } else {
            rows = new ArrayList<>();
            for (WorkerSnapshot snapshot : all) {
                String user = snapshot.username();
                String host = snapshot.host();
                String tags = String.join(",", tagService.tagsFor(user));
                if ((user != null && user.toLowerCase(Locale.ROOT).contains(filter))
                    || (host != null && host.toLowerCase(Locale.ROOT).contains(filter))
                    || tags.toLowerCase(Locale.ROOT).contains(filter)) {
                    rows.add(snapshot);
                }
            }
        }
        fireTableDataChanged();
    }

    public WorkerSnapshot snapshotAt(int row) {
        return rows.get(row);
    }

    public String workerIdAt(int row) {
        return rows.get(row).workerId();
    }

    @Override public int getRowCount()    { return rows.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int column) { return COLUMNS[column]; }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return switch (columnIndex) {
            case COL_PROGRESS, COL_CPU, COL_MEMORY -> Number.class;
            default -> String.class;
        };
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        WorkerSnapshot snapshot = rows.get(rowIndex);
        return switch (columnIndex) {
            case COL_USER -> snapshot.username();
            case COL_HOST -> snapshot.host();
            case COL_STATUS -> statusOf(snapshot);
            case COL_TASK -> {
                if (snapshot.busy()) yield snapshot.taskType();
                yield snapshot.connected() ? "—" : "offline";
            }
            case COL_PROGRESS -> snapshot.progress();
            case COL_CPU -> snapshot.cpu();
            case COL_MEMORY -> snapshot.memory();
            case COL_TAGS -> String.join(", ", tagService.tagsFor(snapshot.username()));
            case COL_LAST_SEEN -> TIME_FORMAT.format(snapshot.lastSeen().atZone(java.time.ZoneId.systemDefault()));
            case COL_UPTIME -> {
                long seconds = snapshot.uptimeSeconds();
                if (seconds < 0) yield "—";
                long h = seconds / 3600;
                long m = (seconds % 3600) / 60;
                long s = seconds % 60;
                yield h > 0
                    ? String.format("%dh %02dm", h, m)
                    : String.format("%dm %02ds", m, s);
            }
            default -> "";
        };
    }

    public static String statusOf(WorkerSnapshot snapshot) {
        if (!snapshot.connected()) return "OFFLINE";
        if (snapshot.busy())      return "BUSY";
        return "ONLINE";
    }
}
