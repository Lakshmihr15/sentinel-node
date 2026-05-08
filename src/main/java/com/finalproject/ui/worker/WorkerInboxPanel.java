package com.finalproject.ui.worker;

import com.finalproject.ui.theme.Theme;
import com.finalproject.ui.theme.UIFactory;
import com.finalproject.worker.WorkerClient;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

public class WorkerInboxPanel extends JPanel {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Theme theme;
    private final WorkerClient worker;
    private final DefaultListModel<NoteItem> model = new DefaultListModel<>();
    private final JList<NoteItem> list = new JList<>(model);
    private final JTextArea reply = new JTextArea(3, 24);
    private final JLabel countLabel;

    public WorkerInboxPanel(Theme theme, WorkerClient worker) {
        super(new BorderLayout(0, 8));
        this.theme = theme;
        this.worker = worker;
        setOpaque(false);

        list.setBackground(theme.surfaceMuted());
        list.setForeground(theme.text());
        list.setCellRenderer(new NoteRenderer());
        list.setFixedCellHeight(64);

        reply.setLineWrap(true);
        reply.setWrapStyleWord(true);
        reply.setBackground(theme.surfaceMuted());
        reply.setForeground(theme.text());
        reply.setCaretColor(theme.text());
        reply.setFont(theme.baseFont());
        reply.setBorder(new EmptyBorder(6, 8, 6, 8));

        countLabel = new JLabel("0 notes");
        countLabel.setForeground(theme.textMuted());
        countLabel.setFont(theme.baseFont().deriveFont(Font.BOLD, 11f));

        JButton send = UIFactory.primaryButton(theme, "Reply");
        JButton clear = UIFactory.secondaryButton(theme, "Clear inbox");
        send.addActionListener(event -> sendReply());
        clear.addActionListener(event -> { model.clear(); refreshCountLabel(); });

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(UIFactory.sectionLabel(theme, "Inbox from manager"), BorderLayout.WEST);
        header.add(countLabel, BorderLayout.EAST);

        JPanel composer = new JPanel(new BorderLayout(0, 6));
        composer.setOpaque(false);
        composer.add(UIFactory.scroll(theme, reply), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(clear);
        actions.add(send);
        composer.add(actions, BorderLayout.SOUTH);
        composer.setPreferredSize(new Dimension(280, 130));

        add(header, BorderLayout.NORTH);
        add(UIFactory.scroll(theme, list), BorderLayout.CENTER);
        add(composer, BorderLayout.SOUTH);
    }

    public void onNoteReceived(long id, String fromUser, String body, String tsString) {
        Instant timestamp = parseInstant(tsString);
        model.add(0, new NoteItem(id, fromUser, body, timestamp));
        refreshCountLabel();
    }

    private void refreshCountLabel() {
        countLabel.setText(model.size() + (model.size() == 1 ? " note" : " notes"));
    }

    private void sendReply() {
        String body = reply.getText().trim();
        if (body.isEmpty()) return;
        worker.replyNote(body);
        reply.setText("");
    }

    private static Instant parseInstant(String value) {
        try { return value == null || value.isBlank() ? Instant.now() : Instant.parse(value); }
        catch (Exception e) { return Instant.now(); }
    }

    private record NoteItem(long id, String fromUser, String body, Instant timestamp) {}

    private class NoteRenderer extends JPanel implements ListCellRenderer<NoteItem> {
        private final JLabel meta = new JLabel();
        private final JLabel body = new JLabel();

        NoteRenderer() {
            super(new BorderLayout());
            setBorder(new EmptyBorder(8, 12, 8, 12));
            meta.setFont(theme.baseFont().deriveFont(Font.BOLD, 11f));
            body.setFont(theme.baseFont().deriveFont(Font.PLAIN, 13f));
            add(meta, BorderLayout.NORTH);
            add(body, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends NoteItem> list, NoteItem value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            setBackground(isSelected ? theme.accentSoft() : theme.surfaceMuted());
            meta.setForeground(theme.textMuted());
            body.setForeground(theme.text());
            String from = value.fromUser() == null || value.fromUser().isBlank() ? "manager" : value.fromUser();
            meta.setText(from + " — " + TIME.format(value.timestamp().atZone(java.time.ZoneId.systemDefault())));
            body.setText("<html><body style='width:240px'>" + escape(value.body()) + "</body></html>");
            return this;
        }

        private String escape(String value) {
            if (value == null) return "";
            return value.replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
