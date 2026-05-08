package com.finalproject.ui.theme;

import java.awt.Color;
import java.awt.Font;

public interface Theme {
    Color background();
    Color surface();
    Color surfaceMuted();
    Color border();
    Color text();
    Color textMuted();
    Color accent();
    Color accentSoft();
    Color success();
    Color warning();
    Color danger();
    Color info();
    Font baseFont();
    Font headingFont();
    Font monoFont();
    String displayName();
}
