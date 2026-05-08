package com.finalproject.ui.manager.resources;

import com.finalproject.manager.ManagerController;
import com.finalproject.manager.templates.TaskTemplate;
import com.finalproject.model.User;
import com.finalproject.ui.theme.Theme;
import com.finalproject.ui.theme.UIFactory;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class TemplatesTab extends JPanel {
    private final ManagerController controller;
    private final Theme theme;
    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private User currentUser;
    private Consumer<String> toastSink = message -> {};

    public TemplatesTab(ManagerController controller, Theme theme) {
        super(new BorderLayout(0, 8));
        this.controller = controller;
        this.theme = theme;
        setBackground(theme.surface());

        table.setBackground(theme.surface());
        table.setForeground(theme.text());
        table.setSelectionBackground(theme.accentSoft());
        table.setRowHeight(26);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JButton add = UIFactory.primaryButton(theme, "Add template");
        JButton edit = UIFactory.secondaryButton(theme, "Edit");
        JButton delete = UIFactory.dangerButton(theme, "Delete");

        add.addActionListener(event -> addTemplate());
        edit.addActionListener(event -> editSelected());
        delete.addActionListener(event -> deleteSelected());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setBackground(theme.surface());
        actions.add(add);
        actions.add(edit);
        actions.add(delete);

        add(UIFactory.scroll(theme, table), BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);
    }

    public void setCurrentUser(User user) { this.currentUser = user; }
    public void setToastSink(Consumer<String> sink) {
        this.toastSink = sink == null ? message -> {} : sink;
    }

    public void refresh() {
        model.reload();
    }

    private void addTemplate() {
        JTextField name = UIFactory.textField(theme, 16);
        JComboBox<String> type = new JComboBox<>(new String[]{"CALC", "SEARCH", "SLEEP"});
        type.setBackground(theme.surfaceMuted());
        type.setForeground(theme.text());
        JTextField payload = UIFactory.textField(theme, 16);
        JPanel form = new JPanel(new GridLayout(0, 1, 6, 6));
        form.setBackground(theme.surface());
        form.add(UIFactory.formLabel(theme, "Name:"));
        form.add(name);
        form.add(UIFactory.formLabel(theme, "Task type:"));
        form.add(type);
        form.add(UIFactory.formLabel(theme, "Payload:"));
        form.add(payload);
        int result = JOptionPane.showConfirmDialog(this, form, "Add template",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        String createdBy = currentUser == null ? "manager" : currentUser.username();
        boolean created = controller.templates().create(
            name.getText().trim(),
            (String) type.getSelectedItem(),
            payload.getText().trim(),
            createdBy);
        if (!created) {
            toastSink.accept("Could not create template (name taken?).");
            return;
        }
        toastSink.accept("Template created.");
        refresh();
    }

    private void editSelected() {
        Optional<TaskTemplate> selection = selected();
        if (selection.isEmpty()) return;
        TaskTemplate template = selection.get();
        JTextField name = UIFactory.textField(theme, 16); name.setText(template.name());
        JComboBox<String> type = new JComboBox<>(new String[]{"CALC", "SEARCH", "SLEEP"});
        type.setBackground(theme.surfaceMuted());
        type.setForeground(theme.text());
        type.setSelectedItem(template.taskType());
        JTextField payload = UIFactory.textField(theme, 16); payload.setText(template.payload());
        JPanel form = new JPanel(new GridLayout(0, 1, 6, 6));
        form.setBackground(theme.surface());
        form.add(UIFactory.formLabel(theme, "Name:"));      form.add(name);
        form.add(UIFactory.formLabel(theme, "Task type:")); form.add(type);
        form.add(UIFactory.formLabel(theme, "Payload:"));   form.add(payload);
        int result = JOptionPane.showConfirmDialog(this, form, "Edit template",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        if (controller.templates().update(template.id(), name.getText().trim(),
            (String) type.getSelectedItem(), payload.getText().trim())) {
            toastSink.accept("Template updated.");
            refresh();
        }
    }

    private void deleteSelected() {
        Optional<TaskTemplate> selection = selected();
        if (selection.isEmpty()) return;
        int confirm = JOptionPane.showConfirmDialog(this,
            "Delete template '" + selection.get().name() + "'?", "Delete",
            JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        if (controller.templates().delete(selection.get().id())) {
            toastSink.accept("Template deleted.");
            refresh();
        }
    }

    private Optional<TaskTemplate> selected() {
        int row = table.getSelectedRow();
        if (row < 0) return Optional.empty();
        return Optional.ofNullable(model.rows.get(row));
    }

    private class Model extends AbstractTableModel {
        private final String[] columns = {"Name", "Type", "Payload", "Created by"};
        private List<TaskTemplate> rows = List.of();

        void reload() {
            rows = controller.templates().list();
            fireTableDataChanged();
        }

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            TaskTemplate template = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> template.name();
                case 1 -> template.taskType();
                case 2 -> template.payload();
                case 3 -> template.createdBy();
                default -> "";
            };
        }
    }
}
