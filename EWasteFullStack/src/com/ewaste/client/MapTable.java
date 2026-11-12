package com.ewaste.client;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/** A read-only, sortable table showing a list of JSON objects. */
public class MapTable extends JTable {
    public static final class Col {
        final String title;
        final Function<Map<String, Object>, Object> value;
        final boolean right;
        Col(String title, Function<Map<String, Object>, Object> value, boolean right) { this.title = title; this.value = value; this.right = right; }
    }

    public static Col col(String title, Function<Map<String, Object>, Object> f) { return new Col(title, f, false); }
    public static Col num(String title, Function<Map<String, Object>, Object> f) { return new Col(title, f, true); }
    public static Col key(String title, String key) { return new Col(title, m -> Ui.s(m.get(key)), false); }

    private final Col[] cols;
    private List<Map<String, Object>> rows = new ArrayList<>();

    public MapTable(Col... cols) {
        this.cols = cols;
        setModel(new AbstractTableModel() {
            @Override public int getRowCount() { return rows.size(); }
            @Override public int getColumnCount() { return MapTable.this.cols.length; }
            @Override public String getColumnName(int c) { return MapTable.this.cols[c].title; }
            @Override public Object getValueAt(int r, int c) { return MapTable.this.cols[c].value.apply(rows.get(r)); }
        });
        setAutoCreateRowSorter(true);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setRowHeight(24);
        setFillsViewportHeight(true);
        setShowGrid(false);
        setIntercellSpacing(new java.awt.Dimension(0, 1));
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        for (int i = 0; i < cols.length; i++) if (cols[i].right) getColumnModel().getColumn(i).setCellRenderer(right);
    }

    public void setRows(List<Map<String, Object>> newRows) {
        Integer keepId = null;
        Map<String, Object> sel = selected();
        if (sel != null && sel.get("id") instanceof Number) keepId = ((Number) sel.get("id")).intValue();
        rows = new ArrayList<>(newRows);
        ((AbstractTableModel) getModel()).fireTableDataChanged();
        if (keepId != null) {
            for (int i = 0; i < rows.size(); i++) {
                Object id = rows.get(i).get("id");
                if (id instanceof Number && ((Number) id).intValue() == keepId) {
                    int v = convertRowIndexToView(i);
                    getSelectionModel().setSelectionInterval(v, v);
                    break;
                }
            }
        }
    }

    public Map<String, Object> selected() {
        int v = getSelectedRow();
        return v < 0 ? null : rows.get(convertRowIndexToModel(v));
    }

    public int rowCountData() { return rows.size(); }

    public void onSelect(Consumer<Map<String, Object>> c) {
        getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) c.accept(selected()); });
    }

    public JScrollPane scroll() { return new JScrollPane(this); }
}
