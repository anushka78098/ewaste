package com.ewaste;

import com.ewaste.model.*;
import com.ewaste.server.repo.MemoryRepository;
import com.ewaste.server.repo.Repository;
import com.ewaste.server.service.EcoChallengeService;
import com.ewaste.server.service.NotificationService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.ewaste.client.ApiClient.obj;

public class EcoChallengeTest {
    private static int passed, failed;

    private static void check(String name, boolean ok) {
        if (ok) { passed++; System.out.println("  PASS " + name); }
        else { failed++; System.out.println("  FAIL " + name); }
    }

    public static void main(String[] args) {
        Repository repo = new MemoryRepository(null);
        NotificationService notifications = new NotificationService(repo);
        EcoChallengeService service = new EcoChallengeService(repo, notifications);
        LocalDate today = LocalDate.now();
        User admin = user(repo, "admin", "Admin", Role.ADMIN);
        User alice = user(repo, "alice", "Alice", Role.USER);
        User bob = user(repo, "bob", "Bob", Role.USER);

        check("non-admin cannot create challenges", rejected(() -> service.create(alice, "Nope", "", EcoChallengeType.DONATE_KG,
                EcoChallengePeriod.WEEKLY, 1, 10, today)));

        EWasteItem collected = item(repo, alice, "Collected", ItemStatus.COLLECTED, Decision.REUSE, 2.0, today);
        EWasteItem listed = item(repo, alice, "Listed", ItemStatus.LISTED, Decision.REUSE, 1.0, today);
        EWasteItem sold = item(repo, alice, "Sold", ItemStatus.SOLD, Decision.REUSE, 1.0, today);
        EWasteItem recycled1 = item(repo, alice, "Recycled 1", ItemStatus.RECYCLED, Decision.RECYCLE, 1.0, today);
        EWasteItem recycled2 = item(repo, alice, "Recycled 2", ItemStatus.RECYCLED, Decision.RECYCLE, 1.0, today);
        RecyclingBatch batch = new RecyclingBatch();
        batch.partner = "Certified recycler";
        batch.itemIds = new ArrayList<>(Arrays.asList(recycled1.id, recycled2.id));
        batch.time = LocalDateTime.now();
        repo.addBatch(batch);
        Campaign drive = new Campaign();
        drive.name = "Local collection";
        drive.startDate = today;
        drive.endDate = today.plusDays(6);
        drive.goalKg = 10;
        repo.addCampaign(drive);

        EcoChallenge kg = service.create(admin, "Weekly weight", "", EcoChallengeType.DONATE_KG,
                EcoChallengePeriod.WEEKLY, 5, 10, today);
        EcoChallenge reusable = service.create(admin, "Monthly reuse", "", EcoChallengeType.DONATE_REUSABLE,
                EcoChallengePeriod.MONTHLY, 2, 20, today);
        EcoChallenge recycling = service.create(admin, "Verified recycling", "", EcoChallengeType.VERIFIED_RECYCLING,
                EcoChallengePeriod.WEEKLY, 2, 30, today);
        EcoChallenge participation = service.create(admin, "Join a drive", "", EcoChallengeType.COLLECTION_DRIVE,
                EcoChallengePeriod.WEEKLY, 1, 40, today);
        check("weekly and monthly windows are configured", kg.endDate.equals(today.plusDays(6))
                && reusable.endDate.equals(today.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth())));

        service.enroll(alice, kg.id, today);
        service.enroll(alice, reusable.id, today);
        service.enroll(alice, recycling.id, today);
        service.enroll(alice, participation.id, today);
        service.refreshForUser(alice.id, today);
        check("all challenge types use verified item and drive records",
                repo.challengeEnrollment(kg.id, alice.id).status == EcoChallengeStatus.COMPLETED
                        && repo.challengeEnrollment(reusable.id, alice.id).status == EcoChallengeStatus.COMPLETED
                        && repo.challengeEnrollment(recycling.id, alice.id).status == EcoChallengeStatus.COMPLETED
                        && repo.challengeEnrollment(participation.id, alice.id).status == EcoChallengeStatus.COMPLETED);
        check("completion rewards add the configured points", repo.user(alice.id).points == 100
                && repo.challengeRewards().size() == 4);
        service.enroll(alice, kg.id, today);
        service.refreshForUser(alice.id, today);
        check("re-enrollment and repeated refresh do not duplicate challenge rewards",
                repo.user(alice.id).points == 100 && countRewards(repo, kg.id, alice.id) == 1);
        check("enrollment and completion notifications are persisted",
                repo.notifications().stream().anyMatch(n -> n.userId == alice.id && n.message.contains("enrolled"))
                        && repo.notifications().stream().anyMatch(n -> n.userId == alice.id && n.message.contains("Challenge complete")));

        EcoChallenge halfway = service.create(admin, "Halfway", "", EcoChallengeType.DONATE_KG,
                EcoChallengePeriod.WEEKLY, 12, 5, today);
        service.enroll(alice, halfway.id, today);
        check("halfway progress produces a one-time milestone notification",
                repo.challengeEnrollment(halfway.id, alice.id).halfwayNotified
                        && repo.notifications().stream().anyMatch(n -> n.userId == alice.id && n.message.contains("Milestone reached")));

        EcoChallenge bobChallenge = service.create(admin, "Bob's target", "", EcoChallengeType.DONATE_KG,
                EcoChallengePeriod.WEEKLY, 0.5, 150, today);
        service.enroll(bob, bobChallenge.id, today);
        item(repo, bob, "Bob contribution", ItemStatus.COLLECTED, Decision.RECYCLE, 1, today);
        service.refreshForUser(bob.id, today);
        List<Object> rankings = service.leaderboard(EcoChallengePeriod.WEEKLY, today);
        java.util.Map<String, Object> leader = obj(rankings.get(0));
        java.util.Map<String, Object> monthlyLeader = obj(service.leaderboard(EcoChallengePeriod.MONTHLY, today).get(0));
        check("leaderboard sorts by challenge bonus points", "Bob".equals(leader.get("name"))
                && ((Number) leader.get("points")).intValue() == 150
                && ((Number) leader.get("rank")).intValue() == 1
                && "Bob".equals(monthlyLeader.get("name")));

        EcoChallenge expired = new EcoChallenge();
        expired.title = "Expired";
        expired.type = EcoChallengeType.DONATE_KG;
        expired.period = EcoChallengePeriod.WEEKLY;
        expired.targetValue = 100;
        expired.rewardPoints = 75;
        expired.startDate = today.minusDays(8);
        expired.endDate = today.minusDays(2);
        repo.addEcoChallenge(expired);
        ChallengeEnrollment oldEnrollment = new ChallengeEnrollment();
        oldEnrollment.challengeId = expired.id;
        oldEnrollment.userId = bob.id;
        oldEnrollment.enrolledAt = expired.startDate.atStartOfDay();
        repo.addChallengeEnrollment(oldEnrollment);
        service.refreshForUser(bob.id, today);
        check("uncompleted past-deadline challenges expire without rewards",
                repo.challengeEnrollment(expired.id, bob.id).status == EcoChallengeStatus.EXPIRED
                        && countRewards(repo, expired.id, bob.id) == 0);

        EWasteItem recycled3 = item(repo, alice, "Recycled 3", ItemStatus.RECYCLED, Decision.RECYCLE, 5, today);
        EWasteItem recycled4 = item(repo, alice, "Recycled 4", ItemStatus.RECYCLED, Decision.RECYCLE, 5, today);
        EWasteItem recycled5 = item(repo, alice, "Recycled 5", ItemStatus.RECYCLED, Decision.RECYCLE, 5, today);
        EWasteItem bigRecycle = item(repo, alice, "Recycled 6", ItemStatus.RECYCLED, Decision.RECYCLE, 100, today);
        batch = new RecyclingBatch();
        batch.partner = "Certified recycler";
        batch.itemIds = new ArrayList<>(Arrays.asList(recycled3.id, recycled4.id, recycled5.id, bigRecycle.id));
        batch.time = LocalDateTime.now();
        repo.addBatch(batch);
        service.refreshForUser(alice.id, today);
        int achievementCount = repo.achievements().size();
        service.refreshForUser(alice.id, today);
        List<String> keys = new ArrayList<>();
        for (Achievement achievement : repo.achievements()) if (achievement.userId == alice.id) keys.add(achievement.key);
        check("verified lifecycle records unlock each unique achievement",
                keys.containsAll(Arrays.asList("FIRST_RECYCLER", "EWASTE_CHAMPION", "REUSE_ADVOCATE",
                        "GREEN_GUARDIAN", "CIRCULAR_ECONOMY_HERO"))
                        && repo.achievements().size() == achievementCount);

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        repo.close();
        System.exit(failed == 0 ? 0 : 1);
    }

    private static User user(Repository repo, String username, String fullName, Role role) {
        User user = new User();
        user.username = username;
        user.salt = "salt";
        user.passwordHash = "hash";
        user.fullName = fullName;
        user.role = role;
        return repo.addUser(user);
    }

    private static EWasteItem item(Repository repo, User owner, String name, ItemStatus status,
                                   Decision decision, double weight, LocalDate created) {
        EWasteItem item = new EWasteItem();
        item.ownerId = owner.id;
        item.name = name;
        item.category = Category.LAPTOP;
        item.ageYears = 1;
        item.weightKg = weight;
        item.condition = Condition.WORKING;
        item.status = status;
        item.decision = decision;
        item.createdAt = created.atTime(12, 0);
        return repo.addItem(item);
    }

    private static int countRewards(Repository repo, int challengeId, int userId) {
        int count = 0;
        for (ChallengeReward reward : repo.challengeRewards())
            if (reward.challengeId == challengeId && reward.userId == userId) count++;
        return count;
    }

    private static boolean rejected(Runnable action) {
        try { action.run(); return false; }
        catch (IllegalArgumentException e) { return true; }
    }
}
