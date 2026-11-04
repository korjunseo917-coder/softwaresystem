package irrigation;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 사용자 입력과 화면 표시를 담당한다. 관수 규칙은 IrrigationService에 요청한다.
 * 읽는 순서: 화면 만들기 → UC별 버튼 처리 → 조회 결과 표시 → 프로그램 시작.
 * @serial exclude
 */
public class MainFrame extends JFrame {
    private static final long serialVersionUID = 1L;
    private final IrrigationService service;

    // 구역 관리 화면의 입력값
    private final DefaultListModel<Zone> zoneModel = new DefaultListModel<>();
    private final JList<Zone> zoneList = new JList<>(zoneModel);
    private final JTextField name = new JTextField();
    private final JTextField start = new JTextField();
    private final JTextField end = new JTextField();
    private final JTextField interval = new JTextField();
    private final JTextField amount = new JTextField();
    private final JTextArea description = new JTextArea(4, 20);
    private final JButton deleteZoneButton = new JButton("구역 삭제");
    private String editingZoneId; // null이면 새 구역을 입력하는 중이다.

    // 조회 필터: 화면의 이름과 실제 구역 ID를 같은 순서로 관리한다.
    private final JComboBox<String> filter = new JComboBox<>();
    private final JComboBox<String> historyFilter = new JComboBox<>();
    private final List<String> filterIds = new ArrayList<>();
    private final List<String> historyIds = new ArrayList<>();
    private boolean refreshing; // 필터를 다시 채울 때 발생하는 선택 이벤트는 무시한다.

    // 일정 조회 화면
    private final JTable pending = createTable("예정일", "구역", "관수량(L)", "상태");
    private final JPanel calendar = new JPanel(new GridLayout(6, 7, 3, 3));
    private final CardLayout cards = new CardLayout();
    private final JPanel views = new JPanel(cards);
    private final JLabel monthLabel = new JLabel();
    private final JLabel count = new JLabel();
    private final JLabel selectionLabel = new JLabel("일정을 선택해 주세요.");
    private final JButton editButton = new JButton("선택 일정 수정");
    private final JButton deleteButton = new JButton("선택 일정 삭제");
    private final JButton completeButton = new JButton("선택 작업 완료");
    private final JButton previousMonth = new JButton("이전 달");
    private final JButton nextMonth = new JButton("다음 달");
    private final JButton thisMonth = new JButton("이번 달");
    private List<Schedule> pendingRows = new ArrayList<>();
    private YearMonth month;
    private boolean calendarView = true;
    private String selectedScheduleId;
    private JButton selectedCalendarButton;

    // 완료 기록 화면
    private final JTable history = createTable("예정일", "완료 당시 구역명", "관수량(L)", "완료 일시", "구역 상태");
    private final JLabel historyCount = new JLabel();
    private final JButton undo = new JButton("선택한 작업 완료 취소");
    private List<Schedule> historyRows = new ArrayList<>();

    private final JLabel notice = new JLabel("구역 관리에서 구역을 등록해 주세요.");
    private javax.swing.Timer dayTimer;
    private LocalDate shownDate;

    /**
     * @param service 관수 기능을 처리할 서비스
     */
    public MainFrame(IrrigationService service) {
        super("구역별 정원 관수 스케줄러");
        this.service = service;
        month = YearMonth.from(service.today());
        shownDate = service.today();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1180, 820);
        setMinimumSize(new Dimension(1050, 720));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("관수 일정", schedulePanel());
        tabs.addTab("구역 관리", zonePanel());
        tabs.addTab("완료 기록", historyPanel());
        add(tabs, BorderLayout.CENTER);

        JPanel footer = new JPanel(new GridLayout(2, 1));
        footer.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        footer.add(notice);
        footer.add(new JLabel("자동 저장: " + service.getStorage().getFile()));
        add(footer, BorderLayout.SOUTH);

        refreshAll();
        newZone();
        setLocationRelativeTo(null);

        // 창을 켜 둔 채 날짜가 바뀌면 날짜 상태를 다시 표시한다.
        dayTimer = new javax.swing.Timer(30000, event -> {
            if (!shownDate.equals(service.today())) {
                shownDate = service.today();
                refreshSchedules();
            }
        });
        dayTimer.start();
    }

    // 1. 화면 만들기: 여기서는 배치와 버튼 연결만 읽으면 된다.
    private static JTable createTable(String... headings) {
        JTable table = new JTable(new DefaultTableModel(headings, 0));
        table.setDefaultEditor(Object.class, null); // 표에서 직접 데이터를 고치지 않는다.
        table.setRowHeight(29);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        return table;
    }

    private JPanel schedulePanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton calendarButton = new JButton("달력");
        JButton listButton = new JButton("목록");
        top.add(new JLabel("구역"));
        top.add(filter);
        top.add(calendarButton);
        top.add(listButton);
        top.add(previousMonth);
        top.add(monthLabel);
        top.add(nextMonth);
        top.add(thisMonth);
        panel.add(top, BorderLayout.NORTH);

        JPanel calendarPanel = new JPanel(new BorderLayout(3, 3));
        JPanel weekdays = new JPanel(new GridLayout(1, 7));
        for (String weekday : new String[]{"일", "월", "화", "수", "목", "금", "토"}) {
            weekdays.add(new JLabel(weekday, SwingConstants.CENTER));
        }
        calendarPanel.add(weekdays, BorderLayout.NORTH);
        calendarPanel.add(calendar, BorderLayout.CENTER);
        views.add(calendarPanel, "calendar");
        views.add(new JScrollPane(pending), "list");
        panel.add(views, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(editButton);
        actions.add(deleteButton);
        actions.add(completeButton);
        actions.add(count);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(selectionLabel, BorderLayout.NORTH);
        bottom.add(actions, BorderLayout.SOUTH);
        panel.add(bottom, BorderLayout.SOUTH);

        // event -> 메서드()는 이 버튼을 누르면 해당 메서드를 실행한다는 뜻이다.
        calendarButton.addActionListener(event -> changeView(true));
        listButton.addActionListener(event -> changeView(false));
        previousMonth.addActionListener(event -> moveMonth(-1));
        nextMonth.addActionListener(event -> moveMonth(1));
        thisMonth.addActionListener(event -> {
            month = YearMonth.from(service.today());
            refreshCalendar();
        });
        editButton.addActionListener(event -> editSchedule());
        deleteButton.addActionListener(event -> deleteSchedule());
        completeButton.addActionListener(event -> complete());
        filter.addActionListener(event -> {
            if (!refreshing) {
                refreshSchedules();
            }
        });
        pending.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting() || calendarView) {
                return;
            }
            int row = pending.getSelectedRow();
            if (row >= 0 && row < pendingRows.size()) {
                selectSchedule(pendingRows.get(row));
            } else {
                clearScheduleSelection();
            }
        });
        return panel;
    }

    private JPanel zonePanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        JScrollPane zoneScroll = new JScrollPane(zoneList);
        zoneScroll.setPreferredSize(new Dimension(240, 300));
        panel.add(zoneScroll, BorderLayout.WEST);

        JPanel right = new JPanel(new BorderLayout(10, 10));
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("구역 이름"));
        form.add(name);
        form.add(new JLabel("시작일 (YYYY-MM-DD)"));
        form.add(start);
        form.add(new JLabel("종료일 (YYYY-MM-DD)"));
        form.add(end);
        form.add(new JLabel("반복 간격 (일, 0은 반복 없음)"));
        form.add(interval);
        form.add(new JLabel("기본 1회 관수량 (L)"));
        form.add(amount);
        right.add(form, BorderLayout.NORTH);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        JScrollPane descriptionScroll = new JScrollPane(description);
        descriptionScroll.setBorder(BorderFactory.createTitledBorder("설명 (선택)"));
        right.add(descriptionScroll, BorderLayout.CENTER);

        JButton newButton = new JButton("새 구역");
        JButton saveButton = new JButton("저장");
        JButton cancelButton = new JButton("취소");
        JPanel actions = new JPanel();
        actions.add(newButton);
        actions.add(saveButton);
        actions.add(cancelButton);
        actions.add(deleteZoneButton);
        right.add(actions, BorderLayout.SOUTH);
        panel.add(right, BorderLayout.CENTER);

        newButton.addActionListener(event -> newZone());
        saveButton.addActionListener(event -> saveZone());
        cancelButton.addActionListener(event -> cancelZoneInput());
        deleteZoneButton.addActionListener(event -> deleteZone());
        zoneList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        zoneList.addListSelectionListener(event -> {
            if (!refreshing && !event.getValueIsAdjusting()) {
                Zone selectedZone = zoneList.getSelectedValue();
                if (selectedZone != null) {
                    fillZone(selectedZone);
                }
            }
        });
        return panel;
    }

    private JPanel historyPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("구역"));
        top.add(historyFilter);
        top.add(new JLabel("삭제된 구역은 기록 조회만 가능합니다."));
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(history), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.add(undo);
        bottom.add(historyCount);
        panel.add(bottom, BorderLayout.SOUTH);

        undo.setEnabled(false);
        undo.addActionListener(event -> undo());
        historyFilter.addActionListener(event -> {
            if (!refreshing) {
                refreshHistory();
            }
        });
        history.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) {
                return;
            }
            undo.setEnabled(false);
            int row = history.getSelectedRow();
            if (row >= 0 && row < historyRows.size()) {
                Schedule schedule = historyRows.get(row);
                if (service.findZone(schedule.getZoneId()) != null) {
                    undo.setEnabled(true);
                }
            }
        });
        return panel;
    }

    // 2. UC별 사용자 작업
    // UC-01: 새 구역 입력. 저장 버튼을 누르기 전에는 서비스 데이터가 바뀌지 않는다.
    private void newZone() {
        zoneList.clearSelection();
        editingZoneId = null;
        name.setText("");
        start.setText(service.today().toString());
        end.setText(service.today().plusMonths(3).toString());
        interval.setText("7");
        amount.setText("2");
        description.setText("");
        deleteZoneButton.setEnabled(false);
    }

    private void fillZone(Zone zone) {
        editingZoneId = zone.getId();
        name.setText(zone.getName());
        start.setText(zone.getStartDate().toString());
        end.setText(zone.getEndDate().toString());
        interval.setText(String.valueOf(zone.getIntervalDays()));
        amount.setText(String.valueOf(zone.getAmountLiters()));
        description.setText(zone.getDescription());
        deleteZoneButton.setEnabled(true);
    }

    // UC-01·02의 취소: 편집 중인 값은 버리고 저장된 값이나 새 입력 기본값으로 돌아간다.
    private void cancelZoneInput() {
        if (editingZoneId == null) {
            newZone();
        } else {
            fillZone(service.findZone(editingZoneId));
        }
        notice.setText("입력을 취소했습니다. 저장된 데이터는 바뀌지 않았습니다.");
    }

    // F-01·02 / UC-01·02: 입력 읽기 → 검사 → (수정은 확인) → 서비스 호출 → 화면 갱신
    private void saveZone() {
        try {
            String zoneName = name.getText().trim();
            LocalDate startDate = LocalDate.parse(start.getText().trim());
            LocalDate endDate = LocalDate.parse(end.getText().trim());
            int intervalDays = Integer.parseInt(interval.getText().trim());
            double amountLiters = Double.parseDouble(amount.getText().trim());
            String zoneDescription = description.getText();

            // 확인창보다 먼저 검사한다. 이 호출은 아무것도 저장하지 않는다.
            service.validateZone(editingZoneId, zoneName, startDate, endDate,
                    intervalDays, amountLiters);

            String savedZoneId;
            if (editingZoneId == null) {
                Zone savedZone = service.registerZone(zoneName, startDate, endDate,
                        intervalDays, amountLiters, zoneDescription);
                savedZoneId = savedZone.getId();
            } else {
                Zone oldZone = service.findZone(editingZoneId);
                boolean planChanged = !oldZone.getStartDate().equals(startDate)
                        || !oldZone.getEndDate().equals(endDate)
                        || oldZone.getIntervalDays() != intervalDays
                        || Double.compare(oldZone.getAmountLiters(), amountLiters) != 0;
                String message = "구역 정보를 수정할까요?";
                if (planChanged) {
                    message = "오늘 이후(오늘 포함)의 미완료 일정을 다시 만듭니다.\n"
                            + "그 기간의 개별 수정·삭제도 초기화됩니다.\n"
                            + "지난 미완료 일정과 완료 기록은 유지됩니다.";
                    if (intervalDays == 0 && startDate.isBefore(service.today())) {
                        message += "\n반복 없음이며 시작일이 과거이므로 새 일정은 생성하지 않습니다.";
                    }
                    message += "\n계속할까요?";
                }
                if (!confirm(message)) {
                    return;
                }
                service.updateZone(editingZoneId, zoneName, startDate, endDate,
                        intervalDays, amountLiters, zoneDescription);
                savedZoneId = editingZoneId;
            }

            // 서비스가 정상 종료한 경우에만 저장 성공으로 표시한다.
            editingZoneId = savedZoneId;
            refreshAll();
            fillZone(service.findZone(savedZoneId));
            notice.setText("구역과 일정을 저장했습니다.");
        } catch (DateTimeParseException | IllegalArgumentException | IOException e) {
            showError(e);
        }
    }

    // F-02 / UC-02 삭제 대안 경로
    private void deleteZone() {
        if (editingZoneId == null) {
            return;
        }
        if (!confirm("이 구역과 모든 미완료 일정을 삭제할까요?\n"
                + "완료 기록은 남지만 완료 취소는 할 수 없습니다.")) {
            return;
        }
        try {
            service.deleteZone(editingZoneId);
            newZone();
            refreshAll();
            notice.setText("구역을 삭제했습니다. 완료 기록은 보존했습니다.");
        } catch (DateTimeParseException | IllegalArgumentException | IOException e) {
            showError(e);
        }
    }

    // F-03 / UC-03 수정 경로
    private void editSchedule() {
        Schedule schedule = service.findSchedule(selectedScheduleId);
        JTextField dateInput = new JTextField(schedule.getScheduledDate().toString());
        JTextField amountInput = new JTextField(String.valueOf(schedule.getAmountLiters()));
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("예정일 (YYYY-MM-DD)"));
        form.add(dateInput);
        form.add(new JLabel("관수량 (L)"));
        form.add(amountInput);

        // 잘못 입력하면 입력값을 유지한 채 다시 고칠 수 있게 한다.
        while (true) {
            int choice = JOptionPane.showConfirmDialog(this, form,
                    "선택 회차 수정", JOptionPane.OK_CANCEL_OPTION);
            if (choice != JOptionPane.OK_OPTION) {
                return;
            }
            try {
                LocalDate scheduledDate = LocalDate.parse(dateInput.getText().trim());
                double amountLiters = Double.parseDouble(amountInput.getText().trim());
                service.updateSchedule(schedule.getId(), scheduledDate, amountLiters);
                refreshAll();
                notice.setText("선택한 회차만 수정했습니다.");
                return;
            } catch (DateTimeParseException | IllegalArgumentException | IOException e) {
                showError(e);
            }
        }
    }

    // F-03 / UC-03 삭제 대안 경로
    private void deleteSchedule() {
        Schedule schedule = service.findSchedule(selectedScheduleId);
        if (!confirm(schedule.getScheduledDate() + " / " + service.getZoneName(schedule)
                + " 일정을 삭제할까요?")) {
            return;
        }
        try {
            service.deleteSchedule(schedule.getId());
            refreshAll();
            notice.setText("선택한 회차만 삭제했습니다.");
        } catch (DateTimeParseException | IllegalArgumentException | IOException e) {
            showError(e);
        }
    }

    // F-05 / UC-05
    private void complete() {
        try {
            service.completeSchedule(selectedScheduleId);
            refreshAll();
            notice.setText("완료했습니다. 잘못 눌렀다면 완료 기록에서 취소할 수 있습니다.");
        } catch (DateTimeParseException | IllegalArgumentException | IOException e) {
            showError(e);
        }
    }

    // F-05 / UC-06
    private void undo() {
        int row = history.getSelectedRow();
        if (row < 0) {
            return;
        }
        try {
            service.undoCompletion(historyRows.get(row).getId());
            refreshAll();
            notice.setText("완료를 취소하고 미완료 일정으로 되돌렸습니다.");
        } catch (DateTimeParseException | IllegalArgumentException | IOException e) {
            showError(e);
        }
    }

    // 3. REQ-04 / UC-04: 조회 결과를 화면에 표시하기
    private String chosen(JComboBox<String> box, List<String> ids) {
        int index = box.getSelectedIndex();
        if (index < 0 || index >= ids.size()) {
            return null;
        }
        return ids.get(index);
    }

    private void refreshAll() {
        String selectedZone = chosen(filter, filterIds);
        String selectedHistoryZone = chosen(historyFilter, historyIds);
        refreshing = true;

        zoneModel.clear();
        filter.removeAllItems();
        filterIds.clear();
        filter.addItem("전체 구역");
        filterIds.add(null);
        for (Zone zone : service.getZones()) {
            zoneModel.addElement(zone);
            filter.addItem(zone.getName());
            filterIds.add(zone.getId());
            if (zone.getId().equals(editingZoneId)) {
                zoneList.setSelectedValue(zone, true);
            }
        }
        filter.setSelectedIndex(Math.max(0, filterIds.indexOf(selectedZone)));

        historyFilter.removeAllItems();
        historyIds.clear();
        historyFilter.addItem("전체 구역");
        historyIds.add(null);
        for (Zone zone : service.getZones()) {
            historyFilter.addItem(zone.getName());
            historyIds.add(zone.getId());
        }
        // 삭제된 구역도 완료 기록 필터에 한 번씩 추가한다.
        for (Schedule schedule : service.getSchedules(null, true)) {
            String zoneId = schedule.getZoneId();
            if (!historyIds.contains(zoneId)) {
                String shortId = zoneId.substring(0, Math.min(8, zoneId.length()));
                historyFilter.addItem(schedule.getCompletedZoneName() + " (삭제됨, " + shortId + ")");
                historyIds.add(zoneId);
            }
        }
        historyFilter.setSelectedIndex(Math.max(0, historyIds.indexOf(selectedHistoryZone)));
        refreshing = false;
        refreshSchedules();
        refreshHistory();
    }

    private void refreshSchedules() {
        clearScheduleSelection();
        pending.clearSelection();
        pendingRows = service.getSchedules(chosen(filter, filterIds), false);
        DefaultTableModel model = (DefaultTableModel) pending.getModel();
        model.setRowCount(0);
        for (Schedule schedule : pendingRows) {
            String status = schedule.getDateStatus(service.today());
            String statusHtml = "<html><font color='" + statusColor(status) + "'>"
                    + status + "</font></html>";
            model.addRow(new Object[]{schedule.getScheduledDate(), service.getZoneName(schedule),
                    schedule.getAmountLiters(), statusHtml});
        }
        refreshCalendar();
    }

    private String statusColor(String status) {
        if (status.equals("기한 지남")) {
            return "#AA1919";
        }
        if (status.equals("오늘 할 작업")) {
            return "#006441";
        }
        return "#444444";
    }

    private void refreshCalendar() {
        clearScheduleSelection();
        calendar.removeAll();
        monthLabel.setText(month.toString());
        LocalDate firstDate = month.atDay(1);
        int emptyCells = firstDate.getDayOfWeek().getValue() % 7; // 일요일이면 0칸
        int monthCount = 0;

        for (int cellIndex = 0; cellIndex < 42; cellIndex++) {
            int day = cellIndex - emptyCells + 1;
            JPanel cell = new JPanel(new BorderLayout());
            cell.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
            if (day >= 1 && day <= month.lengthOfMonth()) {
                LocalDate date = month.atDay(day);
                String dayLabel = " " + day;
                if (date.equals(service.today())) {
                    dayLabel += " (오늘)";
                }
                cell.add(new JLabel(dayLabel), BorderLayout.NORTH);
                JPanel items = new JPanel();
                items.setLayout(new BoxLayout(items, BoxLayout.Y_AXIS));
                for (Schedule schedule : pendingRows) {
                    if (schedule.getScheduledDate().equals(date)) {
                        items.add(calendarEntry(schedule));
                        monthCount++;
                    }
                }
                cell.add(new JScrollPane(items), BorderLayout.CENTER);
            }
            calendar.add(cell);
        }
        if (calendarView) {
            if (monthCount == 0) {
                count.setText("선택한 구역·월에 미완료 일정이 없습니다.");
            } else {
                count.setText("선택한 달의 미완료 " + monthCount + "개");
            }
        } else {
            if (pendingRows.isEmpty()) {
                count.setText("선택한 구역에 미완료 일정이 없습니다.");
            } else {
                count.setText("미완료 " + pendingRows.size() + "개");
            }
        }
        calendar.revalidate();
        calendar.repaint();
    }

    private JButton calendarEntry(Schedule schedule) {
        String zoneName = service.getZoneName(schedule);
        String shortName = zoneName;
        if (shortName.length() > 7) {
            shortName = shortName.substring(0, 7) + "…";
        }
        String status = schedule.getDateStatus(service.today());
        JButton entry = new JButton(shortName + " " + schedule.getAmountLiters() + "L");
        entry.setForeground(Color.decode(statusColor(status)));
        entry.setMargin(new Insets(2, 3, 2, 3));
        entry.setToolTipText(zoneName + " / " + schedule.getScheduledDate() + " / " + status);
        entry.setAlignmentX(Component.LEFT_ALIGNMENT);
        entry.putClientProperty("scheduleId", schedule.getId());
        entry.addActionListener(event -> {
            if (selectedCalendarButton != null) {
                selectedCalendarButton.setBorder(UIManager.getBorder("Button.border"));
            }
            selectedCalendarButton = entry;
            entry.setBorder(BorderFactory.createLineBorder(new Color(0, 100, 65), 2));
            selectSchedule(schedule);
        });
        return entry;
    }

    private void selectSchedule(Schedule schedule) {
        selectedScheduleId = schedule.getId();
        editButton.setEnabled(true);
        deleteButton.setEnabled(true);
        completeButton.setEnabled(true);
        selectionLabel.setText("선택: " + schedule.getScheduledDate() + " / "
                + service.getZoneName(schedule) + " / " + schedule.getAmountLiters() + "L / "
                + schedule.getDateStatus(service.today()));
    }

    private void clearScheduleSelection() {
        selectedScheduleId = null;
        if (selectedCalendarButton != null) {
            selectedCalendarButton.setBorder(UIManager.getBorder("Button.border"));
            selectedCalendarButton = null;
        }
        editButton.setEnabled(false);
        deleteButton.setEnabled(false);
        completeButton.setEnabled(false);
        selectionLabel.setText("일정을 선택해 주세요.");
    }

    private void changeView(boolean showCalendar) {
        calendarView = showCalendar;
        if (showCalendar) {
            cards.show(views, "calendar");
        } else {
            cards.show(views, "list");
        }
        previousMonth.setEnabled(showCalendar);
        nextMonth.setEnabled(showCalendar);
        thisMonth.setEnabled(showCalendar);
        pending.clearSelection();
        refreshCalendar();
    }

    private void moveMonth(int direction) {
        month = month.plusMonths(direction);
        refreshCalendar();
    }

    private void refreshHistory() {
        history.clearSelection();
        undo.setEnabled(false);
        historyRows = service.getSchedules(chosen(historyFilter, historyIds), true);
        DefaultTableModel model = (DefaultTableModel) history.getModel();
        model.setRowCount(0);
        for (Schedule schedule : historyRows) {
            String zoneState = "관리 중";
            if (service.findZone(schedule.getZoneId()) == null) {
                zoneState = "삭제됨";
            }
            String completedAt = schedule.getCompletedAt().withNano(0).toString().replace('T', ' ');
            model.addRow(new Object[]{schedule.getScheduledDate(), schedule.getCompletedZoneName(),
                    schedule.getAmountLiters(), completedAt, zoneState});
        }
        if (historyRows.isEmpty()) {
            historyCount.setText("선택한 구역의 완료 기록이 없습니다.");
        } else {
            historyCount.setText("완료 기록 " + historyRows.size() + "개");
        }
    }

    // 4. 공통 확인·오류 안내
    private boolean confirm(String message) {
        int choice = JOptionPane.showConfirmDialog(this, message,
                "확인", JOptionPane.OK_CANCEL_OPTION);
        return choice == JOptionPane.OK_OPTION;
    }

    private void showError(Exception error) {
        String message = error.getMessage();
        if (error instanceof DateTimeParseException) {
            message = "날짜를 YYYY-MM-DD 형식으로 입력해 주세요.";
        } else if (error instanceof NumberFormatException) {
            message = "반복 간격은 정수, 관수량은 숫자로 입력해 주세요.";
        } else if (error instanceof IOException) {
            message = "파일에 저장하지 못했습니다. 기존 데이터는 유지됩니다.\n" + error.getMessage();
        }
        JOptionPane.showMessageDialog(this, message, "처리하지 못했습니다", JOptionPane.ERROR_MESSAGE);
    }

    /** 창이 닫히면 날짜 확인 타이머를 중지한다. */
    @Override
    public void dispose() {
        if (dayTimer != null) {
            dayTimer.stop();
        }
        super.dispose();
    }

    /**
     * 시작 순서: 파일 위치 → 저장소 → 서비스(불러오기) → 화면.
     * @param args 선택 인수: --data 저장파일경로
     */
    public static void main(String[] args) {
        if (args.length != 0 && (args.length != 2 || !args[0].equals("--data"))) {
            System.err.println("사용법: java -jar app.jar [--data 저장파일경로]");
            return;
        }
        Path file;
        if (args.length == 2) {
            file = Path.of(args[1]);
        } else {
            file = Path.of(System.getProperty("user.home"), ".garden-irrigation", "irrigation.dat");
        }
        // Swing 화면을 전용 이벤트 처리 스레드에서 연다.
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                FileStorage storage = new FileStorage(file);
                IrrigationService service = new IrrigationService(storage);
                MainFrame frame = new MainFrame(service);
                frame.setVisible(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null,
                        "프로그램을 열지 못했습니다. 기존 파일은 덮어쓰지 않습니다.\n" + e.getMessage(),
                        "시작 오류", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
