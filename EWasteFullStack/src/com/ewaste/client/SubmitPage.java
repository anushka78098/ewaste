package com.ewaste.client;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class SubmitPage extends Page {
    private final JTextField name = new JTextField(24), brand = new JTextField(24);
    private final JComboBox<Ui.Opt> category, condition;
    private final JSpinner age = new JSpinner(new SpinnerNumberModel(2, 0, 40, 1));
    private final JSpinner weight = new JSpinner(new SpinnerNumberModel(0.5, 0.01, 500.0, 0.1));
    private final JTextArea description = new JTextArea(4, 24);
    private final JLabel hint = new JLabel(" ");

    public SubmitPage(App app) {
        super(app, "Submit item");
        category = Ui.combo(app.categories(), null);
        condition = Ui.combo(app.conditions(), null);
        category.addActionListener(e -> updateHint());
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        hint.setForeground(Ui.GREY_TEXT);

        Ui.Form f = new Ui.Form();
        f.add("Item name *", name).add("Brand", brand).add("Category *", category).add("Age (years) *", age)
         .add("Weight (kg) *", weight).add("Condition *", condition).add("Description", new JScrollPane(description));
        JPanel box = new JPanel(new BorderLayout(8, 8));
        box.setMaximumSize(new Dimension(520, 400));
        box.add(f, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.add(hint, BorderLayout.NORTH);
        south.add(Ui.buttonRow(Ui.primary("Submit for review", this::submit)), BorderLayout.SOUTH);
        box.add(south, BorderLayout.SOUTH);

        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.LEFT));
        wrap.add(box);
        add(top("Hand in an electronic item"), BorderLayout.NORTH);
        add(wrap, BorderLayout.CENTER);
        add(new JLabel("<html><i>The admin reviews every item, confirms whether it will be reused, repaired or recycled, and schedules a pickup. "
                + "You earn reward points once it is collected.</i></html>"), BorderLayout.SOUTH);
        updateHint();
    }

    private void updateHint() {
        String sel = Ui.sel(category);
        for (Map<String, Object> c : app.categories()) {
            if (Ui.s(c.get("name")).equals(sel)) {
                hint.setText("<html>Reuse age limit: " + c.get("maxReuseAge") + " yrs  |  " + c.get("pointsPerKg") + " points per kg"
                        + (Boolean.TRUE.equals(c.get("hazardous")) ? "  |  <b>Hazardous - keep dry, do not puncture</b>" : "") + "</html>");
            }
        }
    }

    private void submit() {
        if (name.getText().trim().isEmpty()) { Ui.info(this, "Please enter the item name."); return; }
        Map<String, Object> body = ApiClient.map("name", name.getText().trim(), "brand", brand.getText().trim(),
                "category", Ui.sel(category), "ageYears", ((Number) age.getValue()).intValue(),
                "weightKg", ((Number) weight.getValue()).doubleValue(), "condition", Ui.sel(condition),
                "description", description.getText().trim());
        Ui.run(this, () -> ApiClient.obj(app.api.post("/api/items", body)), (Map<String, Object> it) -> {
            Ui.showDecisionReport(this, "Submitted! Item #" + it.get("id") + " is awaiting admin review.",
                    ApiClient.obj(it.get("decisionReport")));
            name.setText(""); brand.setText(""); description.setText("");
            app.refreshHeader.run();
        });
    }

    @Override public void refresh() { }
}
