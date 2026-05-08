package com.finalproject.ui.theme;

import java.awt.Color;
import java.awt.Font;

public final class ManagerTheme implements Theme {
    public static final Theme INSTANCE = new ManagerTheme();

    private final Color background = new Color(0x0F172A);
    private final Color surface = new Color(0x111827);
    private final Color surfaceMuted = new Color(0x1F2937);
    private final Color border = new Color(0x243244);
    private final Color text = new Color(0xE5E7EB);
    private final Color textMuted = new Color(0x94A3B8);
    private final Color accent = new Color(0x38BDF8);
    private final Color accentSoft = new Color(56, 189, 248, 70);
    private final Color success = new Color(0x34D399);
    private final Color warning = new Color(0xF59E0B);
    private final Color danger = new Color(0xF87171);
    private final Color info = new Color(0xA78BFA);

    private ManagerTheme() {}

    @Override public Color background()     { return background; }
    @Override public Color surface()        { return surface; }
    @Override public Color surfaceMuted()   { return surfaceMuted; }
    @Override public Color border()         { return border; }
    @Override public Color text()           { return text; }
    @Override public Color textMuted()      { return textMuted; }
    @Override public Color accent()         { return accent; }
    @Override public Color accentSoft()     { return accentSoft; }
    @Override public Color success()        { return success; }
    @Override public Color warning()        { return warning; }
    @Override public Color danger()         { return danger; }
    @Override public Color info()           { return info; }
    @Override public Font  baseFont()       { return new Font("SansSerif", Font.PLAIN, 12); }
    @Override public Font  headingFont()    { return new Font("SansSerif", Font.BOLD,  14); }
    @Override public Font  monoFont()       { return new Font(Font.MONOSPACED, Font.PLAIN, 12); }
    @Override public String displayName()   { return "Manager"; }
}
