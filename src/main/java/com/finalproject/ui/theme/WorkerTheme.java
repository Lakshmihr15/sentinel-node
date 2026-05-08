package com.finalproject.ui.theme;

import java.awt.Color;
import java.awt.Font;

public final class WorkerTheme implements Theme {
    public static final Theme INSTANCE = new WorkerTheme();

    private final Color background = new Color(0x0B1F1A);
    private final Color surface = new Color(0x10302A);
    private final Color surfaceMuted = new Color(0x18443A);
    private final Color border = new Color(0x1F5A4A);
    private final Color text = new Color(0xECFDF5);
    private final Color textMuted = new Color(0x9CA3AF);
    private final Color accent = new Color(0x34D399);
    private final Color accentSoft = new Color(52, 211, 153, 70);
    private final Color success = new Color(0xA7F3D0);
    private final Color warning = new Color(0xFBBF24);
    private final Color danger = new Color(0xFCA5A5);
    private final Color info = new Color(0x67E8F9);

    private WorkerTheme() {}

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
    @Override public Font  monoFont()       { return new Font(Font.MONOSPACED, Font.PLAIN, 13); }
    @Override public String displayName()   { return "Worker"; }
}
