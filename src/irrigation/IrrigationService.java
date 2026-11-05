package irrigation;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 보고서의 관수 기능을 처리한다.
 * 화면과 파일 형식은 다른 클래스에 맡기고, 이 클래스에서는 관수 규칙을 읽으면 된다.
 * Zone과 Schedule은 생성 후 값이 바뀌지 않으므로, 임시 목록을 만들어 안전하게 수정할 수 있다.
 */
public class IrrigationService {
    private final FileStorage storage;
    private final Clock clock;
    private List<Zone> zones = new ArrayList<>();
    private List<Schedule> schedules = new ArrayList<>();

    /**
     * REQ-06: 프로그램을 시작할 때 저장한 데이터를 읽는다.
     * @param storage 파일 저장소
     * @throws IOException 읽기 실패 또는 잘못된 파일 내용
     */
    public IrrigationService(FileStorage storage) throws IOException {
        this(storage, Clock.systemDefaultZone());
    }

    /**
     * 테스트에서는 오늘 날짜를 고정하기 위해 Clock을 직접 전달한다.
     * @param storage 파일 저장소
     * @param clock 날짜와 시간을 제공하는 시계
     * @throws IOException 읽기 실패 또는 잘못된 파일 내용
     */
    public IrrigationService(FileStorage storage, Clock clock) throws IOException {
        this.storage = storage;
        this.clock = clock;
        storage.loadAll(zones, schedules);
        try {
            validateLoadedData();
        } catch (IllegalArgumentException e) {
            throw new IOException("저장 파일 내용이 올바르지 않습니다: " + e.getMessage(), e);
        }
    }

    /** @return 현재 날짜 */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** @return 파일 저장소 */
    public FileStorage getStorage() {
        return storage;
    }

    /** @return 구역명순으로 정렬한 새 목록 */
    public List<Zone> getZones() {
        List<Zone> result = new ArrayList<>(zones);
        result.sort(Comparator.comparing(Zone::getName));
        return result;
    }

    /**
     * @param zoneId 구역 ID
     * @return 해당 구역, 없으면 null
     */
    public Zone findZone(String zoneId) {
        for (Zone zone : zones) {
            if (zone.getId().equals(zoneId)) {
                return zone;
            }
        }
        return null;
    }

    /**
     * @param scheduleId 일정 ID
     * @return 해당 일정
     * @throws IllegalArgumentException 일정이 없는 경우
     */
    public Schedule findSchedule(String scheduleId) {
        for (Schedule schedule : schedules) {
            if (schedule.getId().equals(scheduleId)) {
                return schedule;
            }
        }
        throw new IllegalArgumentException("해당 일정이 없습니다.");
    }

    // F-01 / REQ-01 / UC-01
    /**
     * 입력 검사 → 구역 생성 → 일정 생성 → 저장 순서로 등록한다.
     * @param name 구역명
     * @param startDate 시작일
     * @param endDate 종료일
     * @param intervalDays 반복 간격(일), 0이면 반복 없음
     * @param amountLiters 기본 1회 관수량(L)
     * @param description 선택 설명
     * @return 등록한 구역
     * @throws IOException 저장 실패
     */
    public Zone registerZone(String name, LocalDate startDate, LocalDate endDate,
                             int intervalDays, double amountLiters, String description)
            throws IOException {
        // 1. 저장 전에 입력값을 검사한다.
        validateZone(null, name, startDate, endDate, intervalDays, amountLiters);

        // 2. 원래 목록은 유지하고 저장할 목록을 따로 준비한다.
        Zone newZone = new Zone(UUID.randomUUID().toString(), name.trim(),
                startDate, endDate, intervalDays, amountLiters, cleanDescription(description));
        List<Zone> nextZones = new ArrayList<>(zones);
        List<Schedule> nextSchedules = new ArrayList<>(schedules);
        nextZones.add(newZone);

        // 3. 최초 등록은 과거 시작일도 허용하며 시작일부터 생성한다.
        generateSchedules(newZone, startDate, nextSchedules);

        // 4. 저장 성공 후에만 현재 목록을 교체한다.
        commit(nextZones, nextSchedules);
        return newZone;
    }

    // F-02 / REQ-02 / UC-02
    /**
     * 확인받은 수정 내용을 적용한다. 기본 계획 변경 시 오늘부터의 미완료만 다시 만든다.
     * @param zoneId 구역 ID
     * @param name 구역명
     * @param startDate 시작일
     * @param endDate 종료일
     * @param intervalDays 반복 간격(일), 0이면 반복 없음
     * @param amountLiters 기본 관수량(L)
     * @param description 설명
     * @throws IOException 저장 실패
     */
    public void updateZone(String zoneId, String name, LocalDate startDate,
                           LocalDate endDate, int intervalDays, double amountLiters,
                           String description) throws IOException {
        Zone oldZone = requireZone(zoneId);
        validateZone(zoneId, name, startDate, endDate, intervalDays, amountLiters);

        Zone updatedZone = new Zone(zoneId, name.trim(), startDate, endDate,
                intervalDays, amountLiters, cleanDescription(description));
        List<Zone> nextZones = new ArrayList<>();
        for (Zone zone : zones) {
            if (zone.getId().equals(zoneId)) {
                nextZones.add(updatedZone);
            } else {
                nextZones.add(zone);
            }
        }

        boolean planChanged = !oldZone.getStartDate().equals(startDate)
                || !oldZone.getEndDate().equals(endDate)
                || oldZone.getIntervalDays() != intervalDays
                || Double.compare(oldZone.getAmountLiters(), amountLiters) != 0;

        List<Schedule> nextSchedules = new ArrayList<>();
        LocalDate today = today();
        for (Schedule schedule : schedules) {
            boolean sameZone = schedule.getZoneId().equals(zoneId);
            boolean pending = !schedule.isCompleted();
            boolean todayOrLater = !schedule.getScheduledDate().isBefore(today);

            // 다시 만들 대상만 빼고 나머지는 그대로 보존한다.
            if (planChanged && sameZone && pending && todayOrLater) {
                continue;
            }
            nextSchedules.add(schedule);
        }

        // 이름·설명만 변경한 경우에는 기존 회차를 다시 만들지 않는다.
        if (planChanged) {
            generateSchedules(updatedZone, today, nextSchedules);
        }
        commit(nextZones, nextSchedules);
    }

    /**
     * 구역과 그 구역의 미완료 일정만 삭제한다. 완료 기록은 남긴다.
     * @param zoneId 삭제할 구역 ID
     * @throws IOException 저장 실패
     */
    public void deleteZone(String zoneId) throws IOException {
        requireZone(zoneId);
        List<Zone> nextZones = new ArrayList<>();
        for (Zone zone : zones) {
            if (!zone.getId().equals(zoneId)) {
                nextZones.add(zone);
            }
        }

        List<Schedule> nextSchedules = new ArrayList<>();
        for (Schedule schedule : schedules) {
            boolean sameZone = schedule.getZoneId().equals(zoneId);
            if (sameZone && !schedule.isCompleted()) {
                continue;
            }
            nextSchedules.add(schedule);
        }
        commit(nextZones, nextSchedules);
    }

    // F-03 / REQ-03 / UC-03
    /**
     * 특정 미완료 회차의 날짜와 관수량만 변경한다.
     * @param scheduleId 일정 ID
     * @param scheduledDate 변경할 날짜
     * @param amountLiters 변경할 관수량(L)
     * @throws IOException 저장 실패
     */
    public void updateSchedule(String scheduleId, LocalDate scheduledDate,
                               double amountLiters) throws IOException {
        Schedule oldSchedule = requirePending(scheduleId);
        Zone zone = requireZone(oldSchedule.getZoneId());
        validateAmount(amountLiters);
        if (scheduledDate == null) {
            throw new IllegalArgumentException("예정일을 입력해 주세요.");
        }

        // 과거에 보존된 회차의 관수량만 고칠 때는 날짜 범위를 다시 검사하지 않는다.
        boolean dateChanged = !oldSchedule.getScheduledDate().equals(scheduledDate);
        if (dateChanged) {
            if (scheduledDate.isBefore(zone.getStartDate())
                    || scheduledDate.isAfter(zone.getEndDate())) {
                throw new IllegalArgumentException("예정일은 구역의 계획 기간 안에서 정해 주세요.");
            }
        }

        for (Schedule schedule : schedules) {
            if (schedule.getId().equals(scheduleId)) {
                continue; // 수정 중인 자기 자신은 중복이 아니다.
            }
            boolean sameZone = schedule.getZoneId().equals(zone.getId());
            boolean sameDate = schedule.getScheduledDate().equals(scheduledDate);
            if (sameZone && sameDate) {
                throw new IllegalArgumentException("해당 날짜에 같은 구역의 다른 일정이 있습니다.");
            }
        }

        Schedule updatedSchedule = new Schedule(scheduleId, zone.getId(), scheduledDate,
                amountLiters, false, null, "");
        replaceSchedule(updatedSchedule);
    }

    /**
     * @param scheduleId 삭제할 미완료 일정 ID
     * @throws IOException 저장 실패
     */
    public void deleteSchedule(String scheduleId) throws IOException {
        requirePending(scheduleId);
        List<Schedule> nextSchedules = new ArrayList<>();
        for (Schedule schedule : schedules) {
            if (!schedule.getId().equals(scheduleId)) {
                nextSchedules.add(schedule);
            }
        }
        commit(new ArrayList<>(zones), nextSchedules);
    }

    // F-04 / REQ-04 / UC-04
    /**
     * 구역과 완료 여부에 맞는 일정을 조회한다. 파일이나 현재 목록을 변경하지 않는다.
     * @param zoneId null이면 전체 구역
     * @param completed false이면 미완료, true이면 완료 기록
     * @return 예정일순 새 목록
     */
    public List<Schedule> getSchedules(String zoneId, boolean completed) {
        List<Schedule> result = new ArrayList<>();
        for (Schedule schedule : schedules) {
            if (schedule.isCompleted() != completed) {
                continue;
            }
            if (zoneId != null && !schedule.getZoneId().equals(zoneId)) {
                continue;
            }
            result.add(schedule);
        }
        // 같은 날짜의 일정은 ID로 순서를 고정한다.
        result.sort(Comparator.comparing(Schedule::getScheduledDate)
                .thenComparing(Schedule::getId));
        return result;
    }

    // F-05 / REQ-05 / UC-05
    /**
     * 완료 일시와 당시 구역 이름·설명·반복 간격을 기록한다.
     * @param scheduleId 완료할 일정 ID
     * @throws IOException 저장 실패
     */
    public void completeSchedule(String scheduleId) throws IOException {
        Schedule oldSchedule = requirePending(scheduleId);
        Zone zone = requireZone(oldSchedule.getZoneId());
        Schedule completedSchedule = new Schedule(scheduleId, zone.getId(),
                oldSchedule.getScheduledDate(), oldSchedule.getAmountLiters(),
                true, LocalDateTime.now(clock), zone.getName(), zone.getDescription(),
                zone.getIntervalDays(), "");
        replaceSchedule(completedSchedule);
    }

    // F-05 / REQ-05 / UC-05 대안 경로
    /**
     * 완료 기록의 특이사항만 수정한다. 완료 당시 정보와 관수량은 그대로 둔다.
     * @param scheduleId 완료한 일정 ID
     * @param notes 실제 물 사용량, 강우 여부, 식물 상태 등을 적은 선택 메모
     * @throws IOException 저장 실패
     * @throws IllegalArgumentException 미완료 일정 또는 삭제된 구역의 기록
     */
    public void updateCompletionNotes(String scheduleId, String notes) throws IOException {
        Schedule oldSchedule = findSchedule(scheduleId);
        if (!oldSchedule.isCompleted()) {
            throw new IllegalArgumentException("완료 기록을 선택해 주세요.");
        }
        if (findZone(oldSchedule.getZoneId()) == null) {
            throw new IllegalArgumentException("삭제된 구역의 완료 기록은 조회만 가능합니다.");
        }
        Schedule updatedSchedule = new Schedule(scheduleId, oldSchedule.getZoneId(),
                oldSchedule.getScheduledDate(), oldSchedule.getAmountLiters(),
                true, oldSchedule.getCompletedAt(), oldSchedule.getCompletedZoneName(),
                oldSchedule.getCompletedZoneDescription(), oldSchedule.getCompletedIntervalDays(),
                cleanDescription(notes));
        replaceSchedule(updatedSchedule);
    }

    // F-05 / REQ-05 / UC-06
    /**
     * 완료 정보와 특이사항을 비우고 미완료로 되돌린다. 예정일과 관수량은 유지한다.
     * @param scheduleId 완료 취소할 일정 ID
     * @throws IOException 저장 실패
     */
    public void undoCompletion(String scheduleId) throws IOException {
        Schedule oldSchedule = findSchedule(scheduleId);
        if (!oldSchedule.isCompleted()) {
            throw new IllegalArgumentException("완료 기록을 선택해 주세요.");
        }
        if (findZone(oldSchedule.getZoneId()) == null) {
            throw new IllegalArgumentException("삭제된 구역의 완료 기록은 조회만 가능합니다.");
        }
        Schedule pendingSchedule = new Schedule(scheduleId, oldSchedule.getZoneId(),
                oldSchedule.getScheduledDate(), oldSchedule.getAmountLiters(), false, null, "");
        replaceSchedule(pendingSchedule);
    }

    // 공통 입력 검사 / REQ-01·02: 확인창 전에 호출해도 데이터가 바뀌지 않는다.
    /**
     * 구역 입력만 검사한다. 잘못된 값은 IllegalArgumentException으로 알린다.
     * @param zoneId 등록은 null, 수정은 해당 구역 ID
     * @param name 구역명
     * @param startDate 시작일
     * @param endDate 종료일
     * @param intervalDays 0 이상 정수
     * @param amountLiters 0보다 큰 유한한 관수량
     */
    public void validateZone(String zoneId, String name, LocalDate startDate,
                             LocalDate endDate, int intervalDays, double amountLiters) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("구역명을 입력해 주세요.");
        }
        for (Zone zone : zones) {
            if (zone.getId().equals(zoneId)) {
                continue;
            }
            if (zone.getName().equals(name.trim())) {
                throw new IllegalArgumentException("같은 이름의 관리 구역이 있습니다.");
            }
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("시작일과 종료일을 입력해 주세요.");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("종료일은 시작일보다 빠를 수 없습니다.");
        }
        if (intervalDays < 0) {
            throw new IllegalArgumentException("반복 간격은 0 이상의 정수입니다. 0은 반복 없음입니다.");
        }
        validateAmount(amountLiters);
    }

    /**
     * @param schedule 표시할 일정
     * @return 미완료는 현재 구역명, 완료는 완료 당시 구역명
     */
    public String getZoneName(Schedule schedule) {
        if (schedule.isCompleted()) {
            return schedule.getCompletedZoneName();
        }
        Zone zone = findZone(schedule.getZoneId());
        if (zone == null) {
            return "삭제된 구역";
        }
        return zone.getName();
    }

    // 아래는 위 기능들이 함께 사용하는 짧은 보조 메서드이다.
    private Zone requireZone(String zoneId) {
        Zone zone = findZone(zoneId);
        if (zone == null) {
            throw new IllegalArgumentException("해당 관리 구역이 없습니다.");
        }
        return zone;
    }

    private Schedule requirePending(String scheduleId) {
        Schedule schedule = findSchedule(scheduleId);
        if (schedule.isCompleted()) {
            throw new IllegalArgumentException("미완료 일정을 선택해 주세요.");
        }
        return schedule;
    }

    private void validateAmount(double amountLiters) {
        if (!Double.isFinite(amountLiters) || amountLiters <= 0) {
            throw new IllegalArgumentException("관수량은 0보다 큰 숫자여야 합니다.");
        }
    }

    private String cleanDescription(String description) {
        if (description == null) {
            return "";
        }
        return description.trim();
    }

    // 기존 객체는 건드리지 않고 해당 ID의 객체만 새 객체로 바꾼 목록을 만든다.
    private void replaceSchedule(Schedule updatedSchedule) throws IOException {
        List<Schedule> nextSchedules = new ArrayList<>();
        for (Schedule schedule : schedules) {
            if (schedule.getId().equals(updatedSchedule.getId())) {
                nextSchedules.add(updatedSchedule);
            } else {
                nextSchedules.add(schedule);
            }
        }
        commit(new ArrayList<>(zones), nextSchedules);
    }

    // REQ-06: 순서를 바꾸면 저장 실패 때 메모리와 파일이 달라질 수 있다.
    private void commit(List<Zone> nextZones, List<Schedule> nextSchedules) throws IOException {
        storage.saveAll(nextZones, nextSchedules); // 1. 파일 저장
        zones = nextZones;                        // 2. 성공한 목록만 사용
        schedules = nextSchedules;
    }

    // REQ-01·02: from은 최초 등록에서는 시작일, 계획 수정에서는 오늘이다.
    private void generateSchedules(Zone zone, LocalDate from, List<Schedule> target) {
        LocalDate date = zone.getStartDate();
        int intervalDays = zone.getIntervalDays();

        // 반복 없음: 과거 날짜의 재생성은 막고, 가능한 경우 한 번만 추가한다.
        if (intervalDays == 0) {
            if (!date.isBefore(from) && !hasScheduleOn(target, zone.getId(), date)) {
                addGeneratedSchedule(target, zone, date);
            }
            return;
        }

        // 기본 계획의 시작일을 기준으로 from 이후 첫 반복 날짜를 찾는다.
        // 예: 시작일 1일, 간격 7일, from 10일이면 첫 생성일은 15일이다.
        if (date.isBefore(from)) {
            long elapsedDays = ChronoUnit.DAYS.between(date, from);
            long intervals = elapsedDays / intervalDays;
            if (elapsedDays % intervalDays != 0) {
                intervals++;
            }
            long daysToAdd = intervals * intervalDays;
            long planDays = ChronoUnit.DAYS.between(date, zone.getEndDate());
            if (daysToAdd > planDays) {
                return;
            }
            date = date.plusDays(daysToAdd);
        }

        // 보존한 완료 일정과 날짜가 겹치면 새 회차를 만들지 않는다.
        Set<LocalDate> occupiedDates = new HashSet<>();
        for (Schedule schedule : target) {
            if (schedule.getZoneId().equals(zone.getId())) {
                occupiedDates.add(schedule.getScheduledDate());
            }
        }
        while (!date.isAfter(zone.getEndDate())) {
            if (!occupiedDates.contains(date)) {
                addGeneratedSchedule(target, zone, date);
            }
            long remainingDays = ChronoUnit.DAYS.between(date, zone.getEndDate());
            if (remainingDays < intervalDays) {
                break; // 다음 날짜가 종료일을 넘으면 더하지 않고 종료한다.
            }
            date = date.plusDays(intervalDays);
        }
    }

    private boolean hasScheduleOn(List<Schedule> list, String zoneId, LocalDate date) {
        for (Schedule schedule : list) {
            if (schedule.getZoneId().equals(zoneId) && schedule.getScheduledDate().equals(date)) {
                return true;
            }
        }
        return false;
    }

    private void addGeneratedSchedule(List<Schedule> target, Zone zone, LocalDate date) {
        if (target.size() >= 100000) {
            throw new IllegalArgumentException("일정은 최대 100,000개입니다. 기간이나 간격을 조정해 주세요.");
        }
        Schedule schedule = new Schedule(UUID.randomUUID().toString(), zone.getId(),
                date, zone.getAmountLiters(), false, null, "");
        target.add(schedule);
    }

    // REQ-06: 파일을 읽었더라도 내용이 모순되면 시작하지 않는다.
    private void validateLoadedData() {
        Set<String> zoneIds = new HashSet<>();
        Set<String> zoneNames = new HashSet<>();
        for (Zone zone : zones) {
            if (zone.getId().isBlank() || !zoneIds.add(zone.getId())) {
                throw new IllegalArgumentException("구역 ID가 없거나 중복되었습니다.");
            }
            if (zone.getName().isBlank() || !zone.getName().equals(zone.getName().trim())
                    || !zoneNames.add(zone.getName())) {
                throw new IllegalArgumentException("구역 이름이 잘못되었거나 중복되었습니다.");
            }
            if (zone.getIntervalDays() < 0 || zone.getEndDate().isBefore(zone.getStartDate())) {
                throw new IllegalArgumentException("구역의 기간 또는 반복 간격이 잘못되었습니다.");
            }
            validateAmount(zone.getAmountLiters());
        }

        Set<String> scheduleIds = new HashSet<>();
        Set<String> zoneDates = new HashSet<>();
        for (Schedule schedule : schedules) {
            if (schedule.getId().isBlank() || schedule.getZoneId().isBlank()
                    || !scheduleIds.add(schedule.getId())) {
                throw new IllegalArgumentException("일정 ID가 없거나 중복되었습니다.");
            }
            String zoneDate = schedule.getZoneId() + ":" + schedule.getScheduledDate();
            if (!zoneDates.add(zoneDate)) {
                throw new IllegalArgumentException("같은 구역의 같은 날짜에 일정이 중복되었습니다.");
            }
            validateAmount(schedule.getAmountLiters());
            if (schedule.isCompleted()) {
                if (schedule.getCompletedAt() == null || schedule.getCompletedZoneName().isBlank()) {
                    throw new IllegalArgumentException("완료 기록에 일시 또는 구역명이 없습니다.");
                }
                if (schedule.getCompletedIntervalDays() < -1) {
                    throw new IllegalArgumentException("완료 기록의 반복 간격이 잘못되었습니다.");
                }
            } else {
                if (!zoneIds.contains(schedule.getZoneId()) || schedule.getCompletedAt() != null
                        || !schedule.getCompletedZoneName().isEmpty()
                        || !schedule.getCompletedZoneDescription().isEmpty()
                        || schedule.getCompletedIntervalDays() != -1
                        || !schedule.getCompletionNotes().isEmpty()) {
                    throw new IllegalArgumentException("미완료 일정의 구역 또는 완료 정보가 잘못되었습니다.");
                }
            }
        }
    }
}
