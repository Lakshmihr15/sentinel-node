package com.finalproject.ui.manager.resources;

import com.finalproject.manager.ManagerController;
import com.finalproject.model.User;
import com.finalproject.ui.theme.Theme;
import com.finalproject.ui.theme.UIFactory;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import java.util.function.Consumer;

public class TagsTab extends JPanel {
    private final ManagerController controller;
    private final Theme theme;
    private final JComboBox<String> userBox = new JComboBox<>();
    private final DefaultListModel<String> tagsListModel = new DefaultListModel<>();
    private final JList<String> tagsList = new JList<>(tagsListModel);
    private final JTextField newTag;
    private Consumer<String> toastSink = message -> {};

    public TagsTab(ManagerController controller, Theme theme) {
        super(new BorderLayout(0, 8));
        this.controller = controller;
        this.theme = theme;
        this.newTag = UIFactory.textField(theme, 12);
        setBackground(theme.surface());

        userBox.setBackground(theme.surfaceMuted());
        userBox.setForeground(theme.text());
        userBox.addActionListener(event -> reloadTagsForSelection());

        tagsList.setBackground(theme.surfaceMuted());
        tagsList.setForeground(theme.text());

        JButton addTag = UIFactory.primaryButton(theme, "Add tag");
        JButton removeTag = UIFactory.dangerButton(theme, "Remove tag");
        addTag.addActionListener(event -> addSelectedTag());
        removeTag.addActionListener(event -> removeSelectedTag());

        JPanel top = new JPanel(new GridLayout(0, 1, 6, 6));
        top.setBackground(theme.surface());
        top.add(UIFactory.formLabel(theme, "Worker:"));
        top.add(userBox);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        bottom.setBackground(theme.surface());
        bottom.add(UIFactory.formLabel(theme, "New tag:"));
        bottom.add(newTag);
        bottom.add(addTag);
        bottom.add(removeTag);

        add(top, BorderLayout.NORTH);
        add(UIFactory.scroll(theme, tagsList), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    public void setToastSink(Consumer<String> sink) {
        this.toastSink = sink == null ? message -> {} : sink;
    }

    public void refresh() {
        String previous = (String) userBox.getSelectedItem();
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        for (User user : controller.auth().listUsers()) {
            if (user.role() == com.finalproject.model.Role.WORKER) {
                model.addElement(user.username());
            }
        }
        userBox.setModel(model);
        if (previous != null) userBox.setSelectedItem(previous);
        reloadTagsForSelection();
    }

    private void reloadTagsForSelection() {
        tagsListModel.clear();
        String selected = (String) userBox.getSelectedItem();
        if (selected == null) return;
        List<String> tags = controller.tags().tagsFor(selected);
        for (String tag : tags) {
            tagsListModel.addElement(tag);
        }
    }

    private void addSelectedTag() {
        String user = (String) userBox.getSelectedItem();
        String tag = newTag.getText().trim();
        if (user == null || tag.isEmpty()) {
            toastSink.accept("Pick a worker and type a tag.");
            return;
        }
        if (controller.tags().assign(user, tag)) {
            toastSink.accept("Tagged " + user + " with #" + tag);
            newTag.setText("");
            reloadTagsForSelection();
        } else {
            toastSink.accept("Tag already present.");
        }
    }

    private void removeSelectedTag() {
        String user = (String) userBox.getSelectedItem();
        String tag = tagsList.getSelectedValue();
        if (user == null || tag == null) return;
        int confirm = JOptionPane.showConfirmDialog(this,
            "Remove tag '" + tag + "' from " + user + "?", "Remove tag",
            JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        if (controller.tags().remove(user, tag)) {
            toastSink.accept("Removed tag #" + tag);
            reloadTagsForSelection();
        }
    }
}
