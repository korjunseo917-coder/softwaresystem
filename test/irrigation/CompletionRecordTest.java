package irrigation;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;

/** 완료 당시 정보, 특이사항, 이전 저장 파일을 확인한다. */
public class CompletionRecordTest {
    private static int checks;
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-05T03:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        checks++;
    }

    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("completion-record-");
        try {
            checkRecords(folder.resolve("records.dat"));
            checkOldFile(folder.resolve("old.dat"));
            checkSaveFailure(folder.resolve("failure.dat"));
            System.out.println("PASS: " + checks + " completion record checks");
        } finally {
            try (var paths = Files.walk(folder)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    private static void checkRecords(Path file) throws Exception {
        IrrigationService service = new IrrigationService(new FileStorage(file), CLOCK);
        Zone zone = service.registerZone("수선화 밭", TODAY, TODAY.plusDays(20), 7, 2,
                "햇볕이 드는 곳\n배수가 잘되는 흙");
        String id = service.getSchedules(zone.getId(), false).get(0).getId();
        try {
            service.updateCompletionNotes(id, "미완료 메모");
            throw new AssertionError("pending notes accepted");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        service.completeSchedule(id);
        Schedule completed = service.findSchedule(id);
        check(completed.getCompletedIntervalDays() == 7, "completed interval");
        check(completed.getCompletedZoneDescription().equals(zone.getDescription()), "completed description");
        service.updateCompletionNotes(id, "  실제 사용량 1.5L\n오전에 비, 잎 상태 양호  ");
        String notes = "실제 사용량 1.5L\n오전에 비, 잎 상태 양호";
        check(service.findSchedule(id).getCompletionNotes().equals(notes), "multiline notes");
        check(service.findSchedule(id).getAmountLiters() == 2, "notes do not change planned amount");
        check(service.findSchedule(id).getCompletedAt().equals(completed.getCompletedAt()), "notes keep completion time");
        service.updateZone(zone.getId(), "이름 변경", TODAY, TODAY.plusDays(20), 14, 3, "바뀐 설명");
        Schedule history = service.findSchedule(id);
        check(history.getCompletedZoneName().equals("수선화 밭"), "name snapshot");
        check(history.getCompletedZoneDescription().equals(zone.getDescription()), "description snapshot");
        check(history.getCompletedIntervalDays() == 7, "interval snapshot");
        check(history.getCompletionNotes().equals(notes), "plan changes retain notes");
        service = new IrrigationService(new FileStorage(file), CLOCK);
        check(service.findSchedule(id).getCompletionNotes().equals(notes), "notes after reopen");
        check(service.findSchedule(id).getCompletedIntervalDays() == 7, "interval after reopen");
        service.updateCompletionNotes(id, "");
        check(service.findSchedule(id).getCompletionNotes().isEmpty(), "clear notes");
        service.updateCompletionNotes(id, notes);
        service.undoCompletion(id);
        Schedule pending = service.findSchedule(id);
        check(!pending.isCompleted() && pending.getCompletionNotes().isEmpty(), "undo clears notes");
        check(pending.getCompletedZoneDescription().isEmpty()
                && pending.getCompletedIntervalDays() == -1, "undo clears snapshot");
        check(pending.getScheduledDate().equals(TODAY) && pending.getAmountLiters() == 2, "undo keeps schedule");
        service.completeSchedule(id);
        check(service.findSchedule(id).getCompletedIntervalDays() == 14, "recompletion snapshots current plan");
        service.updateCompletionNotes(id, notes);
        service.deleteZone(zone.getId());
        check(service.getSchedules(zone.getId(), false).isEmpty(), "zone delete clears pending");
        check(service.findSchedule(id).getCompletionNotes().equals(notes), "zone delete retains notes");
        byte[] before = Files.readAllBytes(file);
        try {
            service.updateCompletionNotes(id, "변경");
            throw new AssertionError("deleted zone notes accepted");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        try {
            service.undoCompletion(id);
            throw new AssertionError("deleted zone undo accepted");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        check(Arrays.equals(before, Files.readAllBytes(file)), "rejected edits leave file unchanged");
        service = new IrrigationService(new FileStorage(file), CLOCK);
        check(service.findSchedule(id).getCompletedZoneDescription().equals("바뀐 설명"), "deleted snapshot reopens");
        Zone single = service.registerZone("한 번만", TODAY, TODAY, 0, 2, "");
        String singleId = service.getSchedules(single.getId(), false).get(0).getId();
        service.completeSchedule(singleId);
        check(service.findSchedule(singleId).getCompletedIntervalDays() == 0, "zero interval snapshot");
    }

    private static void checkOldFile(Path file) throws Exception {
        // 이전 버전의 저장 순서로 만든 파일. 완료 당시 설명과 주기는 들어 있지 않다.
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(file))) {
            output.writeUTF("GARDEN_IRRIGATION_1");
            output.writeInt(1);
            output.writeUTF("zone-1");
            output.writeUTF("현재 이름");
            output.writeUTF(TODAY.toString());
            output.writeUTF(TODAY.plusDays(14).toString());
            output.writeInt(14);
            output.writeDouble(3);
            output.writeUTF("현재 설명");
            output.writeInt(2);
            output.writeUTF("done-1");
            output.writeUTF("zone-1");
            output.writeUTF(TODAY.toString());
            output.writeDouble(2);
            output.writeBoolean(true);
            output.writeUTF("2026-11-05T10:00:00");
            output.writeUTF("옛 이름");
            output.writeUTF("pending-1");
            output.writeUTF("zone-1");
            output.writeUTF(TODAY.plusDays(14).toString());
            output.writeDouble(3);
            output.writeBoolean(false);
            output.writeUTF("");
            output.writeUTF("");
        }
        byte[] original = Files.readAllBytes(file);
        IrrigationService service = new IrrigationService(new FileStorage(file), CLOCK);
        check(service.findSchedule("done-1").getCompletedZoneName().equals("옛 이름"), "legacy name retained");
        check(service.findSchedule("done-1").getCompletedIntervalDays() == -1, "legacy interval not guessed");
        check(service.findSchedule("done-1").getCompletedZoneDescription().isEmpty(), "legacy description not guessed");
        check(Arrays.equals(original, Files.readAllBytes(file)), "read does not migrate file");
        service.updateCompletionNotes("done-1", "이전 기록에 메모 추가");
        Path backup = file.resolveSibling(file.getFileName() + ".v1.bak");
        check(Arrays.equals(original, Files.readAllBytes(backup)), "original file backup");
        service = new IrrigationService(new FileStorage(file), CLOCK);
        check(service.findSchedule("done-1").getCompletionNotes().equals("이전 기록에 메모 추가"), "legacy note round trip");
        check(service.findSchedule("done-1").getCompletedIntervalDays() == -1, "unknown snapshot remains unknown");
        service.completeSchedule("pending-1");
        check(service.findSchedule("pending-1").getCompletedIntervalDays() == 14, "old pending gets new snapshot");
        check(service.findSchedule("pending-1").getCompletedZoneDescription().equals("현재 설명"), "new description saved");
        check(Arrays.equals(original, Files.readAllBytes(backup)), "later saves preserve original backup");
    }

    private static void checkSaveFailure(Path file) throws Exception {
        IrrigationService service = new IrrigationService(new FileStorage(file), CLOCK);
        Zone zone = service.registerZone("테스트", TODAY, TODAY, 0, 2, "설명");
        String id = service.getSchedules(zone.getId(), false).get(0).getId();
        service.completeSchedule(id);
        service.updateCompletionNotes(id, "저장된 메모");
        byte[] original = Files.readAllBytes(file);
        IrrigationService failing = new IrrigationService(new FileStorage(file) {
            @Override
            public void saveAll(java.util.List<Zone> zones, java.util.List<Schedule> schedules)
                    throws IOException {
                throw new IOException("저장 실패 테스트");
            }
        }, CLOCK);
        try {
            failing.updateCompletionNotes(id, "저장하면 안 되는 메모");
            throw new AssertionError("save failure ignored");
        } catch (IOException expected) {
            checks++;
        }
        check(failing.findSchedule(id).getCompletionNotes().equals("저장된 메모"), "failure keeps memory");
        check(Arrays.equals(original, Files.readAllBytes(file)), "failure keeps file");
        // writeUTF의 허용 크기를 넘는 입력도 기존 파일을 훼손하지 않아야 한다.
        try {
            service.updateCompletionNotes(id, "가".repeat(30000));
            throw new AssertionError("oversized note accepted");
        } catch (IOException expected) {
            checks++;
        }
        check(service.findSchedule(id).getCompletionNotes().equals("저장된 메모"), "oversized note keeps memory");
        check(Arrays.equals(original, Files.readAllBytes(file)), "oversized note keeps file");
    }
}
