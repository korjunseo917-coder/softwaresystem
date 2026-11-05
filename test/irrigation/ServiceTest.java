package irrigation;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** 외부 테스트 라이브러리 없이 주요 규칙을 확인한다. 제출 앱 클래스에는 포함되지 않는다. */
public class ServiceTest {
    private static int checks;
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); checks++; }
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("irrigation-test-");
        Path file = dir.resolve("data.dat");
        Clock clock = Clock.fixed(Instant.parse("2026-10-26T03:00:00Z"), ZoneOffset.UTC);
        IrrigationService s = new IrrigationService(new FileStorage(file), clock);
        LocalDate start = LocalDate.of(2026, 9, 30), end = LocalDate.of(2026, 12, 30);
        Zone z = s.registerZone("코스모스", start, end, 7, 2, "설명");
        check(s.getSchedules(null, false).size() == 14, "14 weekly schedules");
        check(s.getSchedules(null, false).get(13).getScheduledDate().equals(end), "inclusive matching end");
        int rejected = 0;
        for (double value : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            try { s.registerZone("invalid", start, end, 7, value, ""); } catch (IllegalArgumentException e) { rejected++; }
        }
        check(rejected == 4, "invalid amounts");
        try { s.registerZone(" 코스모스 ", start, end, 7, 2, ""); throw new AssertionError(); } catch (IllegalArgumentException expected) { checks++; }
        try { s.registerZone("bad", end, start, 7, 2, ""); throw new AssertionError(); } catch (IllegalArgumentException expected) { checks++; }
        try { s.registerZone("bad", start, end, -1, 2, ""); throw new AssertionError(); } catch (IllegalArgumentException expected) { checks++; }
        List<Schedule> initial = s.getSchedules(z.getId(), false);
        Schedule past = initial.get(0), future = initial.get(4), done = initial.get(6);
        s.updateSchedule(future.getId(), future.getScheduledDate(), .5);
        check(s.findZone(z.getId()).getAmountLiters() == 2, "independent amount");
        try { s.updateSchedule(future.getId(), initial.get(5).getScheduledDate(), 2); throw new AssertionError(); } catch (IllegalArgumentException expected) { checks++; }
        try { s.updateSchedule(future.getId(), end.plusDays(1), 2); throw new AssertionError(); } catch (IllegalArgumentException expected) { checks++; }
        s.completeSchedule(done.getId());
        check(s.findSchedule(done.getId()).getCompletedAt() != null, "completion timestamp");
        s.updateZone(z.getId(), "새 코스모스", start, end, 14, 3, "");
        check(s.findSchedule(past.getId()).getAmountLiters() == 2, "past pending preserved");
        check(s.findSchedule(done.getId()).getCompletedZoneName().equals("코스모스"), "historical snapshot");
        check(s.findSchedule(done.getId()).getAmountLiters() == 2, "completion preserved");
        check(s.getSchedules(z.getId(), false).stream().filter(x -> !x.getScheduledDate().isBefore(s.today())).allMatch(x -> x.getAmountLiters() == 3), "future regeneration");
        check(s.getSchedules(z.getId(), false).stream().noneMatch(x -> x.getScheduledDate().equals(done.getScheduledDate())), "skip occupied completed date");
        s.undoCompletion(done.getId()); check(!s.findSchedule(done.getId()).isCompleted(), "undo");
        check(s.findSchedule(done.getId()).getCompletedAt() == null, "clear timestamp");
        s.completeSchedule(done.getId()); s.deleteZone(z.getId());
        check(s.getSchedules(null, false).isEmpty(), "delete all pending");
        check(s.getSchedules(null, true).size() == 1, "retain completed");
        try { s.undoCompletion(done.getId()); throw new AssertionError(); } catch (IllegalArgumentException expected) { checks++; }
        Zone replacement = s.registerZone("새 코스모스", start, start.plusDays(3), 7, 2, "");
        check(!replacement.getId().equals(z.getId()), "new identity");
        check(s.getSchedules(replacement.getId(), false).size() == 1, "end not forced");
        IrrigationService reopened = new IrrigationService(new FileStorage(file), clock);
        check(reopened.getZones().size() == 1 && reopened.getSchedules(null, true).size() == 1, "persistence");
        Schedule one = reopened.getSchedules(null, false).get(0);
        check(one.getDateStatus(clock.instant().atZone(ZoneOffset.UTC).toLocalDate()).equals("기한 지남"), "overdue status");
        check(one.getDateStatus(one.getScheduledDate()).equals("오늘 할 작업"), "today status");
        check(one.getDateStatus(one.getScheduledDate().minusDays(1)).equals("예정"), "future status");
        byte[] saved = Files.readAllBytes(file);
        Path backup = dir.resolveSibling(dir.getFileName() + "-backup");
        Files.move(dir, backup); Files.writeString(dir, "blocked parent");
        try { reopened.completeSchedule(one.getId()); throw new AssertionError(); } catch (java.io.IOException expected) { checks++; }
        check(!reopened.findSchedule(one.getId()).isCompleted(), "save failure preserves memory");
        check(Arrays.equals(saved, Files.readAllBytes(backup.resolve("data.dat"))), "old saved data retained");
        Path broken = backup.resolve("broken.dat"); Files.writeString(broken, "broken");
        try { new IrrigationService(new FileStorage(broken), clock); throw new AssertionError(); } catch (java.io.IOException expected) { checks++; }
        check(Files.readString(broken).equals("broken"), "bad input not overwritten");
        System.out.println("PASS: " + checks + " checks");
        Files.delete(dir);
        try (java.util.stream.Stream<Path> paths = Files.walk(backup)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
