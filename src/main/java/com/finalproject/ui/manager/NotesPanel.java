package com.finalproject.ui.manager;

import com.finalproject.manager.ManagerController;
import com.finalproject.manager.NoteListener;
import com.finalproject.model.User;
import com.finalproject.model.WorkerSnapshot;
import com.finalproject.notes.Note;
import com.finalproject.ui.theme.Theme;
import com.finalproject.ui.theme.UIFactory;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

public class NotesPanel extends JPanel implements NoteListener {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ManagerController controller;
    private final Theme theme;
    private final JComboBox<RecipientOption> recipientBox = new JComboBox<>();
    private final JTextArea bodyArea = new JTextArea(3, 24);
    private final JTextPane timeline = new JTextPane();
    private final JLabel statusLabel = new JLabel(" ");
    private User currentUser;
    private Consumer<String> toastSink = message -> {};

    public NotesPanel(ManagerController controller, Theme theme) {
        super(new BorderLayout(0, 8));
        this.controller = controller;
        this.theme = theme;
        setBackground(theme.surface());

        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        bodyArea.setBackground(theme.surfaceMuted());
        bodyArea.setForeground(theme.text());
        bodyArea.setCaretColor(theme.text());
        bodyArea.setFont(theme.baseFont());

        timeline.setEditable(false);
        timeline.setBackground(theme.surface());
        timeline.setBorder(new javax.swing.border.EmptyBorder(8, 8, 8, 8));

        recipientBox.setBackground(theme.surfaceMuted());
        recipientBox.setForeground(theme.text());

        statusLabel.setForeground(theme.textMuted());
        statusLabel.setFont(theme.baseFont());

        JButton sendButton = UIFactory.primaryButton(theme, "Send note");
        sendButton.addActionListener(event -> send());

        JPanel header = new JPanel(new BorderLayout(8, 4));
        header.setOpaque(false);
        JLabel toLabel = UIFactory.sectionLabel(theme, "To");
        header.add(toLabel, BorderLayout.WEST);
        header.add(recipientBox, BorderLayout.CENTER);

        JPanel composer = new JPanel(new BorderLayout(0, 6));
        composer.setOpaque(false);
        composer.add(header, BorderLayout.NORTH);
        composer.add(UIFactory.scroll(theme, bodyArea), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(statusLabel);
        actions.add(sendButton);
        composer.add(actions, BorderLayout.SOUTH);

        JPanel composerCard = UIFactory.card(theme, "Compose", composer);
        composerCard.setPreferredSize(new Dimension(420, 220));

        JPanel timelineCard = UIFactory.card(theme, "Timeline", UIFactory.scroll(theme, timeline));

        add(composerCard, BorderLayout.NORTH);
        add(timelineCard, BorderLayout.CENTER);

        controller.addNoteListener(this);
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public void setToastSink(Consumer<String> sink) {
        this.toastSink = sink == null ? message -> {} : sink;
    }

    public void refreshRecipients() {
        DefaultComboBoxModel<RecipientOption> model = new DefaultComboBoxModel<>();
        model.addElement(RecipientOption.broadcast());
        for (String tag : controller.tags().allTags()) {
            model.addElement(RecipientOption.tag(tag));
        }
        for (WorkerSnapshot snapshot : controller.registry().snapshots()) {
            model.addElement(RecipientOption.worker(snapshot));
        }
        recipientBox.setModel(model);
    }

    public void refreshTimeline() {
        StyledDocument document = timeline.getStyledDocument();
        try {
            document.remove(0, document.getLength());
            List<Note> notes = controller.notes().recent(60);
            for (int index = notes.size() - 1; index >= 0; index--) {
                appendNote(document, notes.get(index));
            }
        } catch (javax.swing.text.BadLocationException ignored) {
        }
    }

    private void appendNote(StyledDocument document, Note note) throws javax.swing.text.BadLocationException {
        SimpleAttributeSet meta = new SimpleAttributeSet();
        StyleConstants.setForeground(meta, theme.textMuted());
        StyleConstants.setFontFamily(meta, "SansSerif");
        StyleConstants.setFontSize(meta, 11);
        StyleConstants.setBold(meta, true);

        SimpleAttributeSet body = new SimpleAttributeSet();
        StyleConstants.setForeground(body, theme.text());
        StyleConstants.setFontFamily(body, "SansSerif");
        StyleConstants.setFontSize(body, 13);

        SimpleAttributeSet ack = new SimpleAttributeSet();
        StyleConstants.setForeground(ack, note.isAcked() ? theme.success() : theme.warning());
        StyleConstants.setFontFamily(ack, "SansSerif");
        StyleConstants.setFontSize(ack, 11);

        String header = String.format("%s  %s → %s",
            TIME.format(note.timestamp().atZone(java.time.ZoneId.systemDefault())),
            safe(note.senderUsername()),
            describeRecipient(note));
        document.insertString(document.getLength(), header + "\n", meta);
        document.insertString(document.getLength(), safe(note.body()) + "\n", body);
        String state = note.isAcked() ? "✓ acknowledged"
            : (note.isDelivered() ? "✓ delivered" : "○ pending");
        document.insertString(document.getLength(), state + "\n\n", ack);
    }

    private String describeRecipient(Note note) {
        if (note.recipientWorkerId() != null && !note.recipientWorkerId().isBlank()) {
            return note.recipientWorkerId();
        }
        if (note.recipientTag() != null && !note.recipientTag().isBlank()) {
            return "#" + note.recipientTag();
        }
        return "all workers";
    }

    private void send() {
        String body = bodyArea.getText().trim();
        if (body.isEmpty()) {
            statusLabel.setText("Note body cannot be empty.");
            return;
        }
        RecipientOption recipient = (RecipientOption) recipientBox.getSelectedItem();
        if (recipient == null) {
            statusLabel.setText("Pick a recipient.");
            return;
        }
        String fromUser = currentUser == null ? "manager" : currentUser.username();
        long id = controller.sendNote(fromUser, recipient.workerId(), recipient.tag(), body);
        if (id < 0) {
            statusLabel.setText("Failed to send note.");
            return;
        }
        bodyArea.setText("");
        statusLabel.setText("Sent.");
        toastSink.accept("Note sent to " + recipient);
        refreshTimeline();
    }

    @Override
    public void onNoteEvent(Note note, String kind) {
        SwingUtilities.invokeLater(() -> {
            refreshTimeline();
            if ("RECEIVED".equals(kind)) {
                toastSink.accept("Note from " + note.senderUsername() + ": " + truncate(note.body(), 40));
            }
        });
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private record RecipientOption(String label, String workerId, String tag) {
        static RecipientOption broadcast() {
            return new RecipientOption("All connected workers", null, null);
        }
        static RecipientOption tag(String tag) {
            return new RecipientOption("#" + tag + "  (tag)", null, tag);
        }
        static RecipientOption worker(WorkerSnapshot snapshot) {
            String label = snapshot.username() + "  (" + snapshot.workerId() + ")";
            return new RecipientOption(label, snapshot.workerId(), null);
        }
        @Override public String toString() { return label; }
    }
}
