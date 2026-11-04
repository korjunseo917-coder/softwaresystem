package irrigation;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 특정 날짜에 할 관수 작업이다. 완료한 작업도 이 클래스로 보관한다. */
public class Schedule {
    private final String id;
    private final String zoneId;
    private final LocalDate scheduledDate;
    private final double amountLiters;
    private final boolean completed;
    private final LocalDateTime completedAt;
    private final String completedZoneName;

    /**
     * 일정 또는 완료 기록을 만든다.
     * @param id 일정 ID
     * @param zoneId 구역 ID
     * @param scheduledDate 예정일
     * @param amountLiters 관수량(L)
     * @param completed 완료 여부
     * @param completedAt 완료 일시, 미완료는 null
     * @param completedZoneName 완료 당시 구역명, 미완료는 빈 문자열
     */
    public Schedule(String id, String zoneId, LocalDate scheduledDate, double amountLiters,
                    boolean completed, LocalDateTime completedAt, String completedZoneName) {
        this.id = id;
        this.zoneId = zoneId;
        this.scheduledDate = scheduledDate;
        this.amountLiters = amountLiters;
        this.completed = completed;
        this.completedAt = completedAt;
        this.completedZoneName = completedZoneName;
    }
    /**
     * @return 일정 ID */
    public String getId() {
        return id;
    }
    /**
     * @return 구역 ID */
    public String getZoneId() {
        return zoneId;
    }
    /**
     * @return 예정일 */
    public LocalDate getScheduledDate() {
        return scheduledDate;
    }
    /**
     * @return 설정된 관수량(L) */
    public double getAmountLiters() {
        return amountLiters;
    }
    /**
     * @return 완료 여부 */
    public boolean isCompleted() {
        return completed;
    }
    /**
     * @return 완료 일시 */
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
    /**
     * @return 완료 당시 구역명 */
    public String getCompletedZoneName() {
        return completedZoneName;
    }
    /**
     * 날짜 상태는 파일에 저장하지 않고 조회할 때 계산한다.
     * @param today 기준 날짜
     *
     * @return 완료, 기한 지남, 오늘 할 작업, 예정 중 하나
     */
    public String getDateStatus(LocalDate today) {
        if (completed) {
            return "완료";
        }
        if (scheduledDate.isBefore(today)) {
            return "기한 지남";
        }
        if (scheduledDate.equals(today)) {
            return "오늘 할 작업";
        }
        return "예정";
    }
}
