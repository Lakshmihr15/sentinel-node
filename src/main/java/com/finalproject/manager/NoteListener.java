package com.finalproject.manager;

import com.finalproject.notes.Note;

@FunctionalInterface
public interface NoteListener {
    void onNoteEvent(Note note, String kind);
}
