package com.ewaste.fx;

import javafx.scene.chart.*;

import java.util.ArrayList;
import java.util.List;

/** Builders for the built-in JavaFX charts, all with animation off and a single colour via CHART_COLOR_1. */
public final class Charts {
    private Charts() {}

    /** CategoryAxis throws on duplicate labels, so make them unique ("Anna", "Anna (2)"). */
    static List<String> unique(List<String> labels) {
        List<String> out = new ArrayList<>();
        for (String l : labels) {
            String c = l;
            int n = 2;
            while (out.contains(c)) c = l + " (" + n++ + ")";
            out.add(c);
        }
        return out;
    }

    private static XYChart.Series<String, Number> series(List<String> labels, List<Double> values) {
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        List<String> u = unique(labels);
        for (int i = 0; i < u.size(); i++) s.getData().add(new XYChart.Data<>(u.get(i), values.get(i)));
        return s;
    }

    public static BarChart<String, Number> bar(String title, String color) {
        BarChart<String, Number> c = new BarChart<>(new CategoryAxis(), new NumberAxis());
        c.setTitle(title);
        c.setLegendVisible(false);
        c.setAnimated(false);
        c.setCategoryGap(4);
        c.setStyle("CHART_COLOR_1: " + color + ";");
        c.setMinHeight(220);
        return c;
    }

    public static void setBar(BarChart<String, Number> c, List<String> labels, List<Double> values) {
        c.getData().clear();
        c.getData().add(series(labels, values));
    }

    public static LineChart<String, Number> line(String title, String color) {
        LineChart<String, Number> c = new LineChart<>(new CategoryAxis(), new NumberAxis());
        c.setTitle(title);
        c.setLegendVisible(false);
        c.setAnimated(false);
        c.setCreateSymbols(false);
        c.setStyle("CHART_COLOR_1: " + color + ";");
        c.setMinHeight(220);
        return c;
    }

    public static void setLine(LineChart<String, Number> c, List<String> labels, List<Double> values) {
        c.getData().clear();
        c.getData().add(series(labels, values));
    }

    /** Horizontal bars: values on the x axis, category names on the y axis. */
    public static BarChart<Number, String> hbar(String title, String color) {
        BarChart<Number, String> c = new BarChart<>(new NumberAxis(), new CategoryAxis());
        c.setTitle(title);
        c.setLegendVisible(false);
        c.setAnimated(false);
        c.setStyle("CHART_COLOR_1: " + color + ";");
        c.setMinHeight(220);
        return c;
    }

    public static void setHbar(BarChart<Number, String> c, List<String> labels, List<Double> values) {
        XYChart.Series<Number, String> s = new XYChart.Series<>();
        List<String> u = unique(labels);
        for (int i = 0; i < u.size(); i++) s.getData().add(new XYChart.Data<>(values.get(i), u.get(i)));
        c.getData().clear();
        c.getData().add(s);
    }

    public static PieChart pie(String title) {
        PieChart c = new PieChart();
        c.setTitle(title);
        c.setAnimated(false);
        c.setLabelsVisible(false);
        c.setLegendVisible(true);
        c.setMinHeight(220);
        return c;
    }

    public static void setPie(PieChart c, List<String> labels, List<Double> values) {
        c.getData().clear();
        for (int i = 0; i < labels.size(); i++)
            if (values.get(i) > 0) c.getData().add(new PieChart.Data(labels.get(i) + " (" + compact(values.get(i)) + ")", values.get(i)));
    }

    static String compact(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.1f", v); }
}
