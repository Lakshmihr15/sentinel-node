package com.finalproject.ui.manager;

import com.finalproject.manager.ManagerController;
import com.finalproject.manager.templates.TaskTemplate;
import com.finalproject.model.TaskType;
import com.finalproject.model.WorkerSnapshot;
import com.finalproject.ui.theme.Theme;
import com.finalproject.ui.theme.UIFactory;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.function.Consumer;

public class TaskDispatchPanel extends JPanel {
    private final ManagerController controller;
    private final Theme theme;
    private final JComboBox<TemplateOption> templateBox;
    private final JComboBox<TaskType> taskTypeBox;
    private final JTextField payloadField;
    private final JComboBox<String> targetBox;
    private final JButton sendButton;
    private final JLabel hintLabel;
    private Consumer<String> toastSink = message -> {};
    private WorkerSnapshot selectedWorker;

    public TaskDispatchPanel(ManagerController controller, Theme theme) {
        super(new BorderLayout());
        this.controller = controller;
        this.theme = theme;
        this.templateBox = new JComboBox<>();
        this.taskTypeBox = new JComboBox<>(TaskType.values());
        this.payloadField = UIFactory.textField(theme, 16);
        this.targetBox = new JComboBox<>();
        this.sendButton = UIFactory.primaryButton(theme, "Dispatch task");
        this.hintLabel = new JLabel(" ");

        styleCombos();
        loadTemplates();
        refreshTags();
        buildUi();

        templateBox.addActionListener(event -> applyTemplate());
        sendButton.addActionListener(event -> dispatch());

        sendButton.setEnabled(false);
        hintLabel.setText("Select a worker or pick a tag, then click Dispatch.");
    }

    public void onToast(Consumer<String> toastSink) {
        this.toastSink = toastSink == null ? message -> {} : toastSink;
    }

    public void setSelectedWorker(WorkerSnapshot snapshot) {
        this.selectedWorker = snapshot;
        updateState();
    }

    public void refreshTags() {
        List<String> tags = controller.tags().allTags().stream().toList();
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement("(selected worker)");
        for (String tag : tags) {
            model.addElement("tag:" + tag);
        }
        targetBox.setModel(model);
    }

    public void loadTemplates() {
        List<TaskTemplate> templates = controller.templates().list();
        DefaultComboBoxModel<TemplateOption> model = new DefaultComboBoxModel<>();
        model.addElement(new TemplateOption(null));
        for (TaskTemplate template : templates) {
            model.addElement(new TemplateOption(template));
        }
        templateBox.setModel(model);
    }

    private void styleCombos() {
        for (JComboBox<?> box : new JComboBox<?>[]{templateBox, taskTypeBox, targetBox}) {
            box.setBackground(theme.surfaceMuted());
            box.setForeground(theme.text());
        }
    }

    private void buildUi() {
        setBackground(theme.surface());
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 8, 4, 8);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;

        addRow(form, constraints, 0, "Template", templateBox);
        addRow(form, constraints, 1, "Type",     taskTypeBox);
        addRow(form, constraints, 2, "Payload",  payloadField);
        addRow(form, constraints, 3, "Target",   targetBox);

        constraints.gridx = 0; constraints.gridy = 4; constraints.gridwidth = 2;
        hintLabel.setFont(theme.baseFont());
        hintLabel.setForeground(theme.textMuted());
        form.add(hintLabel, constraints);

        constraints.gridy = 5; constraints.fill = GridBagConstraints.NONE; constraints.anchor = GridBagConstraints.LINE_END;
        form.add(sendButton, constraints);

        add(form, BorderLayout.CENTER);
    }

    private void addRow(JPanel panel, GridBagConstraints c, int row, String labelText, Component editor) {
        JLabel label = new JLabel(labelText);
        label.setForeground(theme.textMuted());
        label.setFont(theme.baseFont().deriveFont(Font.BOLD, 11f));
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        c.gridx = 0; c.gridy = row; c.gridwidth = 1;
        c.weightx = 0.0;
        panel.add(label, c);
        c.gridx = 1;
        c.weightx = 1.0;
        panel.add(editor, c);
    }

    private void applyTemplate() {
        TemplateOption selection = (TemplateOption) templateBox.getSelectedItem();
        if (selection == null || selection.template == null) return;
        try {
            taskTypeBox.setSelectedItem(TaskType.fromString(selection.template.taskType()));
        } catch (Exception ignored) {
        }
        payloadField.setText(selection.template.payload());
    }

    private void updateState() {
        Object target = targetBox.getSelectedItem();
        boolean tagTarget = target instanceof String text && text.startsWith("tag:");
        if (selectedWorker != null && !tagTarget) {
            sendButton.setEnabled(selectedWorker.connected() && !selectedWorker.busy());
            hintLabel.setText("Will dispatch to " + selectedWorker.username() + ".");
        } else if (tagTarget) {
            sendButton.setEnabled(true);
            hintLabel.setText("Will broadcast to all idle workers in " + target + ".");
        } else {
            sendButton.setEnabled(false);
            hintLabel.setText("Pick a worker or tag.");
        }
    }

    private void dispatch() {
        TaskType taskType = (TaskType) taskTypeBox.getSelectedItem();
        if (taskType == null) return;
        String payload = payloadField.getText().trim();
        Object target = targetBox.getSelectedItem();
        boolean tagTarget = target instanceof String text && text.startsWith("tag:");
        if (tagTarget) {
            String tag = ((String) target).substring(4);
            List<String> dispatched = controller.dispatchToTag(tag, taskType, payload);
            toastSink.accept("Dispatched " + taskType + " to " + dispatched.size() + " workers in #" + tag);
        } else if (selectedWorker != null) {
            boolean sent = controller.sendTask(selectedWorker.workerId(), taskType, payload);
            toastSink.accept(sent
                ? "Dispatched " + taskType + " to " + selectedWorker.username()
                : "Worker is busy or offline.");
        }
        SwingUtilities.invokeLater(this::updateState);
    }

    private record TemplateOption(TaskTemplate template) {
        @Override
        public String toString() {
            if (template == null) return "(no template)";
            return template.describe();
        }
    }
}
