package com.ewaste.common;

/** Change SYMBOL to use your local currency in every screen and report. */
public final class Money {
    public static final String SYMBOL = "$";
    private Money() {}
    public static String fmt(double v) { return String.format("%s%.2f", SYMBOL, v); }
}
