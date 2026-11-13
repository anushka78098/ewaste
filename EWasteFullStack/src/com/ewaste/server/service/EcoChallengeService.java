package com.ewaste.server.service;

import com.ewaste.model.*;
import com.ewaste.server.repo.Repository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Challenge progress and rewards are derived only from persisted, verified marketplace records. */
public final class EcoChallengeService {
    private final Repository repo;
    private final NotificationService notes;

    public EcoChallengeService(Repository repo, NotificationService notes) {
        this.repo = repo;
        this.notes = notes;
    }

    public EcoChallenge create(User actor, String title, String description, EcoChallengeType type,
                               EcoChallengePeriod period, double target, int rewardPoints, LocalDate today) {
        if (actor == null || !actor.isAdmin()) throw new IllegalArgumentException("Admin access required.");
        if (title == null || title.trim().isEmpty()) throw new IllegalArgumentException("A challenge title is required.");
        if (title.trim().length() > 100) throw new IllegalArgumentException("Challenge title cannot exceed 100 characters.");
        if (description != null && description.length() > 1000)
            throw new IllegalArgumentException("Challenge description cannot exceed 1000 characters.");
        if (type == null || period == null || today == null) throw new IllegalArgumentException("Challenge type, period and date are required.");
        if (!Double.isFinite(target) || target <= 0 || target > 100_000)
            throw new IllegalArgumentException("Challenge target must be a finite amount above zero and at most 100000.");
        if (type != EcoChallengeType.DONATE_KG && target != Math.rint(target))
            throw new IllegalArgumentException("This challenge target must be a whole number.");
        if (rewardPoints <= 0 || rewardPoints > 1_000_000)
            throw new IllegalArgumentException("Reward points must be between 1 and 1000000.");

        EcoChallenge challenge = new EcoChallenge();
        challenge.title = title.trim();
        challenge.description = description == null ? "" : description.trim();
        challenge.type = type;
        challenge.period = period;
        challenge.targetValue = target;
        challenge.rewardPoints = rewardPoints;
        challenge.startDate = today;
        challenge.endDate = period == EcoChallengePeriod.WEEKLY ? today.plusDays(6)
                : today.with(TemporalAdjusters.lastDayOfMonth());
        repo.addEcoChallenge(challenge);
        notes.announce("New " + period.name().toLowerCase(Locale.ROOT) + " eco challenge: " + challenge.title
                + " - " + targetDescription(challenge) + " by " + challenge.endDate + ".");
        return challenge;
    }

    public void end(User actor, int challengeId) {
        if (actor == null || !actor.isAdmin()) throw new IllegalArgumentException("Admin access required.");
        EcoChallenge challenge = requireChallenge(challengeId);
        challenge.active = false;
        repo.saveEcoChallenge(challenge);
    }

    public ChallengeEnrollment enroll(User user, int challengeId, LocalDate today) {
        if (user == null || !user.active) throw new IllegalArgumentException("An active account is required.");
        EcoChallenge challenge = requireChallenge(challengeId);
        if (!challenge.active || today.isBefore(challenge.startDate) || today.isAfter(challenge.endDate))
            throw new IllegalArgumentException("This challenge is not currently open for enrollment.");
        ChallengeEnrollment existing = repo.challengeEnrollment(challengeId, user.id);
        if (existing != null) return existing;
        ChallengeEnrollment enrollment = new ChallengeEnrollment();
        enrollment.challengeId = challenge.id;
        enrollment.userId = user.id;
        enrollment.enrolledAt = LocalDateTime.now();
        repo.addChallengeEnrollment(enrollment);
        notes.push(user.id, "You enrolled in the eco challenge \"" + challenge.title + "\".");
        refreshForUser(user.id, today);
        return repo.challengeEnrollment(challengeId, user.id);
    }

    /** Recalculates progress and awards earned rewards; callers invoke this after verified record changes. */
    public void refreshForUser(int userId, LocalDate today) {
        User user = repo.user(userId);
        if (user == null || !user.active) return;
        List<EWasteItem> items = new ArrayList<>();
        for (EWasteItem item : repo.items()) if (item.ownerId == userId) items.add(item);
        List<Campaign> campaigns = repo.campaigns();
        List<RecyclingBatch> batches = repo.batches();

        for (ChallengeEnrollment enrollment : repo.challengeEnrollments()) {
            if (enrollment.userId != userId) continue;
            EcoChallenge challenge = repo.ecoChallenge(enrollment.challengeId);
            if (challenge == null || enrollment.status == EcoChallengeStatus.COMPLETED) continue;

            double oldProgress = enrollment.progress;
            enrollment.progress = progress(challenge, items, campaigns, batches);
            if (challenge.active && !today.isAfter(challenge.endDate) && !enrollment.halfwayNotified
                    && oldProgress < challenge.targetValue / 2.0
                    && enrollment.progress >= challenge.targetValue / 2.0) {
                enrollment.halfwayNotified = true;
                notes.push(userId, "Milestone reached in \"" + challenge.title + "\". You are at "
                        + formatProgress(challenge, enrollment.progress) + ".");
            }
            if (enrollment.progress >= challenge.targetValue) {
                enrollment.status = EcoChallengeStatus.COMPLETED;
                if (enrollment.completedAt == null) enrollment.completedAt = LocalDateTime.now();
                awardChallengeReward(user, challenge);
                notes.push(userId, "Challenge complete: \"" + challenge.title + "\"! You earned "
                        + challenge.rewardPoints + " bonus points.");
            } else if (!challenge.active || today.isAfter(challenge.endDate)) {
                enrollment.status = EcoChallengeStatus.EXPIRED;
            }
            repo.saveChallengeEnrollment(enrollment);
        }
        awardAchievements(userId, items, batches);
    }

    public Map<String, Object> userView(User user, LocalDate today) {
        refreshForUser(user.id, today);
        Map<Integer, ChallengeEnrollment> enrollmentByChallenge = new HashMap<>();
        for (ChallengeEnrollment e : repo.challengeEnrollments())
            if (e.userId == user.id) enrollmentByChallenge.put(e.challengeId, e);

        List<Object> challengeViews = new ArrayList<>();
        for (EcoChallenge challenge : repo.ecoChallenges()) {
            ChallengeEnrollment enrollment = enrollmentByChallenge.get(challenge.id);
            boolean visible = (challenge.active && !today.isBefore(challenge.startDate) && !today.isAfter(challenge.endDate))
                    || enrollment != null;
            if (visible) challengeViews.add(challengeView(challenge, enrollment, today));
        }
        List<Object> badges = new ArrayList<>();
        for (Achievement achievement : repo.achievements()) {
            if (achievement.userId == user.id) badges.add(achievementView(achievement));
        }
        return map("challenges", challengeViews, "achievements", badges,
                "weeklyLeaderboard", leaderboard(EcoChallengePeriod.WEEKLY, today),
                "monthlyLeaderboard", leaderboard(EcoChallengePeriod.MONTHLY, today));
    }

    public List<Object> adminView(LocalDate today) {
        for (User user : repo.users()) refreshForUser(user.id, today);
        Map<Integer, int[]> counts = new HashMap<>();
        for (ChallengeEnrollment e : repo.challengeEnrollments()) {
            int[] n = counts.computeIfAbsent(e.challengeId, k -> new int[2]);
            n[0]++;
            if (e.status == EcoChallengeStatus.COMPLETED) n[1]++;
        }
        List<Object> out = new ArrayList<>();
        List<EcoChallenge> challenges = repo.ecoChallenges();
        for (int i = challenges.size() - 1; i >= 0; i--) {
            EcoChallenge challenge = challenges.get(i);
            Map<String, Object> view = challengeView(challenge, null, today);
            int[] n = counts.getOrDefault(challenge.id, new int[2]);
            view.put("enrollmentCount", n[0]);
            view.put("completionCount", n[1]);
            out.add(view);
        }
        return out;
    }

    public List<Object> leaderboard(EcoChallengePeriod period, LocalDate today) {
        if (period == null || today == null) throw new IllegalArgumentException("Leaderboard period and date are required.");
        LocalDate from = period == EcoChallengePeriod.WEEKLY
                ? today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : today.withDayOfMonth(1);
        Map<Integer, int[]> totals = new HashMap<>();
        for (ChallengeReward reward : repo.challengeRewards()) {
            LocalDate date = reward.awardedAt.toLocalDate();
            if (date.isBefore(from) || date.isAfter(today)) continue;
            int[] values = totals.computeIfAbsent(reward.userId, k -> new int[2]);
            values[0] += reward.points;
            values[1]++;
        }
        List<User> users = new ArrayList<>();
        for (Integer userId : totals.keySet()) {
            User user = repo.user(userId);
            if (user != null && user.active) users.add(user);
        }
        users.sort((a, b) -> {
            int[] av = totals.get(a.id), bv = totals.get(b.id);
            int byPoints = Integer.compare(bv[0], av[0]);
            if (byPoints != 0) return byPoints;
            int byChallenges = Integer.compare(bv[1], av[1]);
            if (byChallenges != 0) return byChallenges;
            int byName = a.fullName.compareToIgnoreCase(b.fullName);
            return byName != 0 ? byName : Integer.compare(a.id, b.id);
        });
        List<Object> out = new ArrayList<>();
        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);
            int[] values = totals.get(user.id);
            out.add(map("rank", i + 1, "name", user.fullName, "points", values[0], "completedChallenges", values[1]));
        }
        return out;
    }

    private void awardChallengeReward(User user, EcoChallenge challenge) {
        for (ChallengeReward reward : repo.challengeRewards())
            if (reward.challengeId == challenge.id && reward.userId == user.id) return;
        ChallengeReward reward = new ChallengeReward();
        reward.challengeId = challenge.id;
        reward.userId = user.id;
        reward.points = challenge.rewardPoints;
        reward.awardedAt = LocalDateTime.now();
        repo.addChallengeReward(reward);
        user.points = Math.addExact(user.points, challenge.rewardPoints);
        repo.saveUser(user);
    }

    private void awardAchievements(int userId, List<EWasteItem> items, List<RecyclingBatch> batches) {
        double receivedKg = 0;
        int recycled = 0;
        boolean sold = false;
        Set<Integer> verifiedRecycledItems = new HashSet<>();
        for (RecyclingBatch batch : batches)
            for (int itemId : batch.itemIds) verifiedRecycledItems.add(itemId);
        for (EWasteItem item : items) {
            if (Rules.received(item)) receivedKg += item.weightKg;
            if (item.status == ItemStatus.RECYCLED && verifiedRecycledItems.contains(item.id)) recycled++;
            if (item.status == ItemStatus.SOLD) sold = true;
        }
        if (recycled > 0) achievement(userId, "FIRST_RECYCLER", "First Recycler", "Complete your first verified recycling activity.");
        if (receivedKg >= 100) achievement(userId, "EWASTE_CHAMPION", "E-Waste Champion", "Hand in at least 100 kg of verified e-waste.");
        if (sold) achievement(userId, "REUSE_ADVOCATE", "Reuse Advocate", "Help a donated electronic find a new owner.");
        if (recycled >= 5) achievement(userId, "GREEN_GUARDIAN", "Green Guardian", "Complete five verified recycling activities.");
        if (receivedKg >= 25 && recycled > 0 && sold)
            achievement(userId, "CIRCULAR_ECONOMY_HERO", "Circular Economy Hero", "Contribute at least 25 kg through both reuse and recycling.");
    }

    private void achievement(int userId, String key, String label, String description) {
        for (Achievement existing : repo.achievements())
            if (existing.userId == userId && existing.key.equals(key)) return;
        Achievement achievement = new Achievement();
        achievement.userId = userId;
        achievement.key = key;
        achievement.label = label;
        achievement.description = description;
        repo.addAchievement(achievement);
        notes.push(userId, "Achievement unlocked: " + label + "!");
    }

    private double progress(EcoChallenge challenge, List<EWasteItem> items,
                            List<Campaign> campaigns, List<RecyclingBatch> batches) {
        Set<Integer> counted = new HashSet<>();
        switch (challenge.type) {
            case DONATE_KG:
                double kg = 0;
                for (EWasteItem item : items) {
                    if (inWindow(item, challenge) && Rules.received(item)) kg += item.weightKg;
                }
                return Math.round(kg * 100.0) / 100.0;
            case DONATE_REUSABLE:
                for (EWasteItem item : items) {
                    if (inWindow(item, challenge) && !item.category.hazardous
                            && (item.status == ItemStatus.LISTED || item.status == ItemStatus.SOLD)
                            && (item.decision == Decision.REUSE || item.decision == Decision.REPAIR_REUSE))
                        counted.add(item.id);
                }
                return counted.size();
            case VERIFIED_RECYCLING:
                for (RecyclingBatch batch : batches) {
                    if (inWindow(batch.time.toLocalDate(), challenge)) {
                        for (int itemId : batch.itemIds)
                            for (EWasteItem item : items)
                                if (item.id == itemId && item.status == ItemStatus.RECYCLED) counted.add(item.id);
                    }
                }
                return counted.size();
            case COLLECTION_DRIVE:
                Set<Integer> participatingDrives = new HashSet<>();
                for (Campaign campaign : campaigns) {
                    for (EWasteItem item : items) {
                        LocalDate submitted = item.createdAt.toLocalDate();
                        if (inWindow(item, challenge) && Rules.received(item)
                                && !submitted.isBefore(campaign.startDate) && !submitted.isAfter(campaign.endDate))
                            participatingDrives.add(campaign.id);
                    }
                }
                return participatingDrives.size();
            default:
                throw new IllegalStateException("Unsupported eco challenge type: " + challenge.type);
        }
    }

    private Map<String, Object> challengeView(EcoChallenge challenge, ChallengeEnrollment enrollment, LocalDate today) {
        EcoChallengeStatus status = enrollment == null
                ? (challenge.active && !today.isBefore(challenge.startDate) && !today.isAfter(challenge.endDate)
                    ? EcoChallengeStatus.ACTIVE : EcoChallengeStatus.EXPIRED)
                : enrollment.status;
        double progress = enrollment == null ? 0 : enrollment.progress;
        double percent = Math.min(100.0, progress / challenge.targetValue * 100.0);
        return map("id", challenge.id, "title", challenge.title, "description", challenge.description,
                "type", challenge.type.name(), "period", challenge.period.name(), "targetValue", challenge.targetValue,
                "targetDescription", targetDescription(challenge), "rewardPoints", challenge.rewardPoints,
                "startDate", challenge.startDate.toString(), "endDate", challenge.endDate.toString(),
                "status", status.name(), "progress", progress, "percent", percent,
                "enrolled", enrollment != null, "canEnroll", enrollment == null && status == EcoChallengeStatus.ACTIVE,
                "daysLeft", status == EcoChallengeStatus.ACTIVE ? Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(today, challenge.endDate)) : 0L);
    }

    private static Map<String, Object> achievementView(Achievement achievement) {
        return map("key", achievement.key, "label", achievement.label,
                "description", achievement.description, "earnedAt", achievement.earnedAt.toString());
    }

    private static boolean inWindow(EWasteItem item, EcoChallenge challenge) {
        return inWindow(item.createdAt.toLocalDate(), challenge);
    }

    private static boolean inWindow(LocalDate date, EcoChallenge challenge) {
        return !date.isBefore(challenge.startDate) && !date.isAfter(challenge.endDate);
    }

    private static String targetDescription(EcoChallenge challenge) {
        String amount = challenge.targetValue == Math.rint(challenge.targetValue)
                ? String.valueOf((long) challenge.targetValue) : String.format(Locale.ROOT, "%.1f", challenge.targetValue);
        switch (challenge.type) {
            case DONATE_KG: return amount + " kg of received e-waste";
            case DONATE_REUSABLE: return amount + " reusable electronics listed";
            case VERIFIED_RECYCLING: return amount + " verified recycling activities";
            case COLLECTION_DRIVE: return amount + " collection drive(s)";
            default: throw new IllegalStateException("Unsupported eco challenge type: " + challenge.type);
        }
    }

    private static String formatProgress(EcoChallenge challenge, double progress) {
        return challenge.type == EcoChallengeType.DONATE_KG
                ? String.format(Locale.ROOT, "%.1f kg of %.1f kg", progress, challenge.targetValue)
                : String.format(Locale.ROOT, "%.0f of %.0f", progress, challenge.targetValue);
    }

    private EcoChallenge requireChallenge(int id) {
        EcoChallenge challenge = repo.ecoChallenge(id);
        if (challenge == null) throw new IllegalArgumentException("No eco challenge with id " + id + ".");
        return challenge;
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) out.put((String) kv[i], kv[i + 1]);
        return out;
    }
}
