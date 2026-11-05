package irrigation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/** 반복 없음과 수정 범위 등 이번 수정에서 달라진 규칙을 확인한다. */
public class RevisionTest {
    private static int checks;

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        checks++;
    }

    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("irrigation-revision-");
        Path file = folder.resolve("test.dat");
        Clock clock = Clock.fixed(Instant.parse("2026-10-26T03:00:00Z"), ZoneOffset.UTC);
        IrrigationService service = new IrrigationService(new FileStorage(file), clock);
        LocalDate today = service.today();
        Zone single = service.registerZone("한 번만", today, today.plusDays(30), 0, 2, "");
        List<Schedule> rows = service.getSchedules(single.getId(), false);
        check(rows.size() == 1 && rows.get(0).getScheduledDate().equals(today), "zero creates one");
        String singleId = rows.get(0).getId();
        service.updateSchedule(singleId, today.plusDays(2), 0.5);
        service.updateZone(single.getId(), "이름 변경", today, today.plusDays(30), 0, 2, "설명만 수정");
        check(service.findSchedule(singleId).getAmountLiters() == 0.5, "rename keeps exception");
        check(service.findSchedule(singleId).getScheduledDate().equals(today.plusDays(2)), "rename keeps moved date");
        IrrigationService reopened = new IrrigationService(new FileStorage(file), clock);
        check(reopened.findZone(single.getId()).getIntervalDays() == 0, "zero round trip");
        byte[] before = Files.readAllBytes(file);
        service.validateZone(single.getId(), "검사만", today, today.plusDays(30), 0, 2);
        check(java.util.Arrays.equals(before, Files.readAllBytes(file)), "validation does not save");
        check(service.findZone(single.getId()).getName().equals("이름 변경"), "validation does not change memory");

        Zone repeat = service.registerZone("반복", today.minusDays(10), today.plusDays(30), 7, 3, "");
        Schedule past = service.getSchedules(repeat.getId(), false).get(0);
        service.updateZone(repeat.getId(), "반복", today.minusDays(10), today.plusDays(30), 0, 3, "");
        check(service.getSchedules(repeat.getId(), false).size() == 2, "past single start creates no future");
        check(service.findSchedule(past.getId()) != null, "past pending preserved");
        service.updateZone(repeat.getId(), "반복", today, today.plusDays(30), 7, 3, "");
        service.updateSchedule(past.getId(), past.getScheduledDate(), 1);
        check(service.findSchedule(past.getId()).getAmountLiters() == 1, "historical amount can change outside new range");
        service.updateZone(repeat.getId(), "반복", today.minusDays(10), today.plusDays(30), 7, 4, "");
        Schedule firstFuture = null;
        for (Schedule row : service.getSchedules(repeat.getId(), false)) {
            if (!row.getScheduledDate().isBefore(today)) {
                firstFuture = row;
                break;
            }
        }
        check(firstFuture.getScheduledDate().equals(today.plusDays(4)), "regeneration anchored to start");
        service.completeSchedule(firstFuture.getId());
        Schedule another = service.getSchedules(repeat.getId(), false).get(0);
        try {
            service.updateSchedule(another.getId(), firstFuture.getScheduledDate(), 2);
            throw new AssertionError("completed date must block move");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        service.updateZone(repeat.getId(), "반복", today.minusDays(10), today.plusDays(30), 7, 5, "");
        for (Schedule row : service.getSchedules(repeat.getId(), false)) {
            check(!row.getScheduledDate().equals(firstFuture.getScheduledDate()), "no duplicate of completed date");
        }
        check(service.findSchedule(firstFuture.getId()).getAmountLiters() == 4, "completed amount stays");
        service.deleteSchedule(singleId);
        check(service.getSchedules(single.getId(), false).isEmpty(), "individual delete");
        Files.delete(file);
        Files.delete(folder);
        System.out.println("PASS: " + checks + " revision checks");
    }
}
