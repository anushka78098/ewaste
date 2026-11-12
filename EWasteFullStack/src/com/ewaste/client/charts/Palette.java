package com.ewaste.client.charts;

import java.awt.Color;

/** Colour-blind-friendly categorical palette plus neutrals shared by all charts. */
public final class Palette {
    private Palette() {}

    public static final Color[] SERIES = {
        new Color(0x2E7D32), new Color(0x1976D2), new Color(0xF9A825), new Color(0xD84315),
        new Color(0x6A1B9A), new Color(0x00838F), new Color(0x6D4C41), new Color(0x78909C)
    };
    public static final Color GRID = new Color(0xE3E8EB);
    public static final Color AXIS_TEXT = new Color(0x546E7A);
    public static final Color TITLE = new Color(0x1B5E20);
    public static final Color BORDER = new Color(0xCFD8DC);
    public static final Color GOOD = new Color(0x2E7D32), BAD = new Color(0xC62828), NEUTRAL = new Color(0x78909C);

    public static Color series(int i) { return SERIES[i % SERIES.length]; }
}
