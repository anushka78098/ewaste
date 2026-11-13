package com.ewaste.client.charts;

import java.awt.Color;

/** Colour-blind-friendly categorical palette plus neutrals shared by all charts. */
public final class Palette {
    private Palette() {}

    public static final Color[] SERIES = {
        new Color(0x2563EB), new Color(0x8B5CF6), new Color(0xD29B00), new Color(0x0F9D8A),
        new Color(0xE56B3F), new Color(0x0783C4), new Color(0x6D4C41), new Color(0x78909C)
    };
    public static final Color GRID = new Color(0xE4EAF2);
    public static final Color AXIS_TEXT = new Color(0x64748B);
    public static final Color TITLE = new Color(0x1E3A5F);
    public static final Color BORDER = new Color(0xD8E1EC);
    public static final Color GOOD = new Color(0x2E7D32), BAD = new Color(0xC62828), NEUTRAL = new Color(0x78909C);

    public static Color series(int i) { return SERIES[i % SERIES.length]; }
}
