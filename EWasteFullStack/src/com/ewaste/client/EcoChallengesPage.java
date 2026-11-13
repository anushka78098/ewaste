package com.ewaste.client;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

/** Eco challenges, earned achievements and period-specific challenge rankings. */
public final class EcoChallengesPage extends Page {
    private final boolean admin;
    private final JPanel cards = new JPanel();
    private final JPanel achievements = new JPanel(new GridLayout(0, 1, 4, 4));
    private final JComboBox<String> period = new JComboBox<>(new String[]{"WEEKLY", "MONTHLY"});
    private Map<String, Object> userData;
    private final MapTable leaderboard = new MapTable(
            MapTable.num("Rank", m -> m.get("rank")), MapTable.key("Participant", "name"),
            MapTable.num("Bonus points", m -> m.get("points")),
            MapTable.num("Challenges", m -> m.get("completedChallenges")));

    public EcoChallengesPage(App app) {
        super(app, "Eco challenges");
        this.admin = app.isAdmin();
        cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
        JPanel right = new JPanel(new BorderLayout(6, 6));
        right.add(new JLabel(admin ? "Challenge leaderboards" : "Your achievements"), BorderLayout.NORTH);
        if (!admin) right.add(new JScrollPane(achievements), BorderLayout.CENTER);
        JPanel ranks = new JPanel(new BorderLayout(4, 4));
        ranks.add(period, BorderLayout.NORTH);
        ranks.add(leaderboard.scroll(), BorderLayout.CENTER);
        right.add(ranks, admin ? BorderLayout.CENTER : BorderLayout.SOUTH);
        period.addActionListener(e -> loadLeaderboard());

        JPanel content = new JPanel(new GridLayout(1, 2, 12, 0));
        content.add(new JScrollPane(cards));
        content.add(right);
        add(admin ? top("Eco challenges", Ui.primary("Create challenge...", this::createChallenge),
                        Ui.button("Refresh", this::refresh))
                : top("Eco challenges", Ui.button("Refresh", this::refresh)), BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
    }

    @Override public void refresh() {
        String path = admin ? "/api/admin/eco/challenges" : "/api/eco/challenges";
        Ui.run(this, () -> app.api.get(path), result -> {
            if (admin) renderCards(ApiClient.list(result));
            else {
                Map<String, Object> data = ApiClient.obj(result);
                userData = data;
                renderCards(ApiClient.list(data.get("challenges")));
                renderAchievements(ApiClient.list(data.get("achievements")));
                showLeaderboard(data);
            }
            if (admin) loadLeaderboard();
        });
    }

    private void renderCards(List<Map<String, Object>> challenges) {
        cards.removeAll();
        if (challenges.isEmpty()) cards.add(new JLabel("No eco challenges are available right now."));
        for (Map<String, Object> challenge : challenges) {
            JPanel card = new JPanel(new BorderLayout(8, 6));
            card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0xB7D7B9)),
                    BorderFactory.createEmptyBorder(10, 10, 10, 10)));
            String heading = Ui.s(challenge.get("title")) + "  -  " + Ui.s(challenge.get("period"))
                    + "  |  +" + challenge.get("rewardPoints") + " points";
            JLabel title = new JLabel(heading);
            title.setFont(title.getFont().deriveFont(Font.BOLD));
            JLabel detail = new JLabel("<html>" + Ui.s(challenge.get("description")) + "<br>"
                    + Ui.s(challenge.get("targetDescription")) + " | ends " + Ui.s(challenge.get("endDate"))
                    + " | " + Ui.s(challenge.get("status")) + "</html>");
            JProgressBar progress = new JProgressBar(0, 100);
            progress.setValue((int) Math.round(Ui.d(challenge.get("percent"))));
            progress.setStringPainted(true);
            progress.setString(String.format("%.0f%%  (%.1f / %.1f)", Ui.d(challenge.get("percent")),
                    Ui.d(challenge.get("progress")), Ui.d(challenge.get("targetValue"))));
            JPanel body = new JPanel(new BorderLayout(4, 4));
            body.add(title, BorderLayout.NORTH);
            body.add(detail, BorderLayout.CENTER);
            body.add(progress, BorderLayout.SOUTH);
            card.add(body, BorderLayout.CENTER);
            if (Boolean.TRUE.equals(challenge.get("canEnroll"))) {
                card.add(Ui.primary("Join challenge", () -> enroll(challenge)), BorderLayout.EAST);
            } else if (admin && "ACTIVE".equals(challenge.get("status"))) {
                card.add(Ui.button("End", () -> endChallenge(challenge)), BorderLayout.EAST);
            } else if (admin) {
                card.add(new JLabel(challenge.get("enrollmentCount") + " enrolled / "
                        + challenge.get("completionCount") + " completed"), BorderLayout.EAST);
            } else if (Boolean.TRUE.equals(challenge.get("enrolled"))) {
                card.add(new JLabel("Enrolled"), BorderLayout.EAST);
            }
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 125));
            cards.add(card);
            cards.add(Box.createVerticalStrut(8));
        }
        cards.revalidate();
        cards.repaint();
    }

    private void renderAchievements(List<Map<String, Object>> earned) {
        achievements.removeAll();
        if (earned.isEmpty()) achievements.add(new JLabel("No achievements earned yet."));
        for (Map<String, Object> achievement : earned) {
            JLabel badge = new JLabel("★  " + Ui.s(achievement.get("label")));
            badge.setToolTipText(Ui.s(achievement.get("description")));
            achievements.add(badge);
        }
        achievements.revalidate();
        achievements.repaint();
    }

    private void showLeaderboard(Map<String, Object> data) {
        Object key = "MONTHLY".equals(period.getSelectedItem()) ? "monthlyLeaderboard" : "weeklyLeaderboard";
        leaderboard.setRows(ApiClient.list(data.get(key)));
    }

    private void loadLeaderboard() {
        if (admin) {
            Ui.run(this, () -> ApiClient.list(app.api.get("/api/eco/leaderboard?period=" + period.getSelectedItem())),
                    leaderboard::setRows);
        } else if (userData != null) showLeaderboard(userData);
    }

    private void enroll(Map<String, Object> challenge) {
        Ui.run(this, () -> app.api.post("/api/eco/challenges/" + challenge.get("id") + "/enroll", ApiClient.map()),
                result -> refresh());
    }

    private void createChallenge() {
        JTextField title = new JTextField(22);
        JTextField description = new JTextField(22);
        JComboBox<String> type = new JComboBox<>(new String[]{"DONATE_KG", "DONATE_REUSABLE", "VERIFIED_RECYCLING", "COLLECTION_DRIVE"});
        JComboBox<String> cadence = new JComboBox<>(new String[]{"WEEKLY", "MONTHLY"});
        JTextField target = new JTextField("5", 22);
        JTextField points = new JTextField("100", 22);
        Ui.Form form = new Ui.Form().add("Title *", title).add("Description", description)
                .add("Challenge type", type).add("Cadence", cadence).add("Target *", target).add("Bonus points *", points);
        if (JOptionPane.showConfirmDialog(this, form, "Create eco challenge", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try {
            Map<String, Object> body = ApiClient.map("title", title.getText().trim(), "description", description.getText().trim(),
                    "type", type.getSelectedItem(), "period", cadence.getSelectedItem(),
                    "targetValue", Double.parseDouble(target.getText().trim()),
                    "rewardPoints", Integer.parseInt(points.getText().trim()));
            Ui.run(this, () -> app.api.post("/api/admin/eco/challenges", body), result -> refresh());
        } catch (NumberFormatException e) {
            Ui.info(this, "Target and bonus points must be valid numbers.");
        }
    }

    private void endChallenge(Map<String, Object> challenge) {
        if (!Ui.confirm(this, "End \"" + challenge.get("title") + "\" now?")) return;
        Ui.run(this, () -> app.api.post("/api/admin/eco/challenges/" + challenge.get("id") + "/end", ApiClient.map()),
                result -> refresh());
    }

}
