package irrigation;

import java.time.LocalDate;

/** 관리 구역 하나와 기본 관수 계획을 저장한다. 값은 생성 후 바꾸지 않는다. */
public class Zone {
    private final String id;
    private final String name;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final int intervalDays;
    private final double amountLiters;
    private final String description;

    /**
     * 구역 정보를 만든다. 입력 검사는 서비스에서 한다.
     * @param id 구역 ID
     * @param name 구역명
     * @param startDate 시작일
     * @param endDate 종료일
     * @param intervalDays 반복 간격(일), 0이면 반복 없음
     * @param amountLiters 기본 1회 관수량(L)
     * @param description 설명
     */
    public Zone(String id, String name, LocalDate startDate, LocalDate endDate,
                int intervalDays, double amountLiters, String description) {
        this.id = id;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.intervalDays = intervalDays;
        this.amountLiters = amountLiters;
        this.description = description;
    }
    /**
     * @return 구역 ID */
    public String getId() {
        return id;
    }
    /**
     * @return 구역명 */
    public String getName() {
        return name;
    }
    /**
     * @return 시작일 */
    public LocalDate getStartDate() {
        return startDate;
    }
    /**
     * @return 종료일 */
    public LocalDate getEndDate() {
        return endDate;
    }
    /**
     * @return 반복 간격(일), 0이면 반복 없음 */
    public int getIntervalDays() {
        return intervalDays;
    }
    /**
     * @return 기본 관수량(L) */
    public double getAmountLiters() {
        return amountLiters;
    }
    /**
     * @return 설명 */
    public String getDescription() {
        return description;
    }
    /**
     * @return 화면에서 표시할 이름 */
    @Override public String toString() {
        return name;
    }
}
