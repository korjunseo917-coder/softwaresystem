package irrigation;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** REQ-06: 구역과 일정을 하나의 로컬 파일에 저장하고 불러온다. */
public class FileStorage {
    private static final String HEADER = "GARDEN_IRRIGATION_1";
    private static final int MAX_RECORDS = 100000;
    private final Path file;

    /** @param file 저장 파일 경로 */
    public FileStorage(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    /** @return 실제 저장 파일 경로 */
    public Path getFile() {
        return file;
    }

    /**
     * 임시 파일에 먼저 저장한 뒤 기존 파일을 교체한다.
     * @param zones 구역 목록
     * @param schedules 일정 목록
     * @throws IOException 파일 저장이나 교체 실패
     */
    public void saveAll(List<Zone> zones, List<Schedule> schedules) throws IOException {
        if (zones.size() > MAX_RECORDS || schedules.size() > MAX_RECORDS) {
            throw new IOException("저장할 기록이 너무 많습니다 (최대 100,000개).");
        }
        Files.createDirectories(file.getParent());
        Path temporaryFile = Files.createTempFile(file.getParent(), "irrigation-", ".tmp");
        try {
            try (FileOutputStream fileOutput = new FileOutputStream(temporaryFile.toFile());
                 DataOutputStream output = new DataOutputStream(new BufferedOutputStream(fileOutput))) {
                output.writeUTF(HEADER);
                output.writeInt(zones.size());
                for (Zone zone : zones) {
                    output.writeUTF(zone.getId());
                    output.writeUTF(zone.getName());
                    output.writeUTF(zone.getStartDate().toString());
                    output.writeUTF(zone.getEndDate().toString());
                    output.writeInt(zone.getIntervalDays());
                    output.writeDouble(zone.getAmountLiters());
                    output.writeUTF(zone.getDescription());
                }
                output.writeInt(schedules.size());
                for (Schedule schedule : schedules) {
                    output.writeUTF(schedule.getId());
                    output.writeUTF(schedule.getZoneId());
                    output.writeUTF(schedule.getScheduledDate().toString());
                    output.writeDouble(schedule.getAmountLiters());
                    output.writeBoolean(schedule.isCompleted());
                    String completedAt = "";
                    if (schedule.getCompletedAt() != null) {
                        completedAt = schedule.getCompletedAt().toString();
                    }
                    output.writeUTF(completedAt);
                    output.writeUTF(schedule.getCompletedZoneName());
                }
                output.flush();
                fileOutput.getFD().sync();
            }
            Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING,
                       StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    /**
     * 저장한 순서대로 읽는다. 파일이 없으면 빈 목록으로 시작한다.
     * @param zones 구역을 받을 빈 목록
     * @param schedules 일정을 받을 빈 목록
     * @throws IOException 손상된 파일 또는 읽기 실패
     */
    public void loadAll(List<Zone> zones, List<Schedule> schedules) throws IOException {
        if (!zones.isEmpty() || !schedules.isEmpty()) {
            throw new IllegalArgumentException("불러올 목록은 비어 있어야 합니다.");
        }
        List<Zone> loadedZones = new ArrayList<>();
        List<Schedule> loadedSchedules = new ArrayList<>();
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(file)))) {
            if (!HEADER.equals(input.readUTF())) {
                throw new IOException("지원하지 않는 저장 파일입니다.");
            }
            int zoneCount = readCount(input);
            for (int i = 0; i < zoneCount; i++) {
                String id = input.readUTF();
                String name = input.readUTF();
                LocalDate startDate = LocalDate.parse(input.readUTF());
                LocalDate endDate = LocalDate.parse(input.readUTF());
                int intervalDays = input.readInt();
                double amountLiters = input.readDouble();
                String description = input.readUTF();
                loadedZones.add(new Zone(id, name, startDate, endDate,
                                         intervalDays, amountLiters, description));
            }
            int scheduleCount = readCount(input);
            for (int i = 0; i < scheduleCount; i++) {
                String id = input.readUTF();
                String zoneId = input.readUTF();
                LocalDate scheduledDate = LocalDate.parse(input.readUTF());
                double amountLiters = input.readDouble();
                boolean completed = input.readBoolean();
                String savedCompletedAt = input.readUTF();
                String completedZoneName = input.readUTF();
                LocalDateTime completedAt = null;
                if (!savedCompletedAt.isEmpty()) {
                    completedAt = LocalDateTime.parse(savedCompletedAt);
                }
                loadedSchedules.add(new Schedule(id, zoneId, scheduledDate, amountLiters,
                                                 completed, completedAt, completedZoneName));
            }
            if (input.read() != -1) {
                throw new IOException("저장 파일 뒤에 알 수 없는 데이터가 있습니다.");
            }
        } catch (NoSuchFileException e) {
            return;
        } catch (RuntimeException e) {
            throw new IOException("저장 파일의 날짜 또는 형식이 잘못되었습니다.", e);
        }
        // 모두 읽은 뒤에 전달받은 목록에 넣어, 일부만 불러오는 상황을 막는다.
        zones.addAll(loadedZones);
        schedules.addAll(loadedSchedules);
    }

    private int readCount(DataInputStream input) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > MAX_RECORDS) {
            throw new IOException("기록 개수가 잘못되었습니다.");
        }
        return count;
    }
}
