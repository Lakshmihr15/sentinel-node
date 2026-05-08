package com.finalproject.ui.manager.resources;

import com.finalproject.manager.ManagerController;
import com.finalproject.model.User;
import com.finalproject.ui.theme.Theme;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.util.function.Consumer;

public class ResourcePanel extends JPanel {
    private final WorkerAccountsTab accountsTab;
    private final SessionsTab sessionsTab;
    private final TemplatesTab templatesTab;
    private final TagsTab tagsTab;

    public ResourcePanel(ManagerController controller, Theme theme) {
        super(new BorderLayout());
        setBackground(theme.surface());
        accountsTab = new WorkerAccountsTab(controller, theme);
        sessionsTab = new SessionsTab(controller, theme);
        templatesTab = new TemplatesTab(controller, theme);
        tagsTab = new TagsTab(controller, theme);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(theme.surface());
        tabs.setForeground(theme.text());
        tabs.addTab("Workers",   accountsTab);
        tabs.addTab("Sessions",  sessionsTab);
        tabs.addTab("Templates", templatesTab);
        tabs.addTab("Tags",      tagsTab);
        add(tabs, BorderLayout.CENTER);
    }

    public void setCurrentUser(User user) {
        accountsTab.setCurrentUser(user);
        sessionsTab.setCurrentUser(user);
        templatesTab.setCurrentUser(user);
    }

    public void setToastSink(Consumer<String> sink) {
        accountsTab.setToastSink(sink);
        sessionsTab.setToastSink(sink);
        templatesTab.setToastSink(sink);
        tagsTab.setToastSink(sink);
    }

    public void refresh() {
        accountsTab.refresh();
        sessionsTab.refresh();
        templatesTab.refresh();
        tagsTab.refresh();
    }
}
