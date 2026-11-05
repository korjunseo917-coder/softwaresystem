package irrigation;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.time.*;
import javax.imageio.ImageIO;

/** 테스트용 파일로 화면 생성, 완료, 완료 취소와 화면 갱신을 확인한다. */
public class GuiSmokeTest {
    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private static JButton findButton(Container c, String label) {
        for (Component x : c.getComponents()) {
            if (x instanceof JButton && ((JButton)x).getText().equals(label)) return (JButton)x;
            if (x instanceof Container) { JButton b = findButton((Container)x, label); if (b != null) return b; }
        }
        return null;
    }
    private static JOptionPane findPane(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JOptionPane) return (JOptionPane)component;
            if (component instanceof Container) {
                JOptionPane result = findPane((Container)component);
                if (result != null) return result;
            }
        }
        return null;
    }
    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            Font font = Font.createFonts(Path.of(args[1]).toFile())[0].deriveFont(13f);
            for (Object key : UIManager.getDefaults().keySet().toArray())
                if (UIManager.get(key) instanceof javax.swing.plaf.FontUIResource)
                    UIManager.put(key, new javax.swing.plaf.FontUIResource(font));
        }
        Path data = Files.createTempDirectory("irrigation-gui-").resolve("test.dat");
        IrrigationService s = new IrrigationService(new FileStorage(data), Clock.fixed(Instant.parse("2026-10-26T03:00:00Z"), ZoneOffset.UTC));
        s.registerZone("코스모스 텃밭", LocalDate.of(2026,10,19), LocalDate.of(2026,12,28),7,2,"햇볕이 잘 드는 구역");
        s.registerZone("수국 화단", LocalDate.of(2026,10,23), LocalDate.of(2026,12,30),3,3.5,"");
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame(s);
            try {
                frame.setVisible(true);
                if (((JButton)field(frame, "completeButton")).isEnabled()) throw new AssertionError("initial action disabled");
                if (((JButton)field(frame, "undo")).isEnabled()) throw new AssertionError("empty history disabled");
                findButton(frame, "새 구역").doClick();
                ((JTextField)field(frame, "name")).setText("한 번만 관수");
                ((JTextField)field(frame, "interval")).setText("0");
                findButton(frame, "저장").doClick();
                Zone single = null;
                for (Zone zone : s.getZones()) {
                    if (zone.getName().equals("한 번만 관수")) single = zone;
                }
                if (single == null || s.getSchedules(single.getId(), false).size() != 1) throw new AssertionError("zero UI register");
                ((JTextField)field(frame, "name")).setText("저장하지 않은 이름");
                findButton(frame, "취소").doClick();
                if (!((JTextField)field(frame, "name")).getText().equals("한 번만 관수")) throw new AssertionError("cancel restores saved input");
                ((JTextField)field(frame, "start")).setText("잘못된 날짜");
                final boolean[] sawError = {false};
                javax.swing.Timer closeError = new javax.swing.Timer(150, event -> {
                    for (Window window : Window.getWindows()) {
                        if (window instanceof JDialog && window.isVisible()) {
                            JOptionPane pane = findPane((Container) window);
                            if (pane != null) {
                                sawError[0] = pane.getMessageType() == JOptionPane.ERROR_MESSAGE;
                                pane.setValue(JOptionPane.OK_OPTION);
                            }
                        }
                    }
                });
                closeError.start();
                findButton(frame, "저장").doClick();
                closeError.stop();
                if (!sawError[0]) throw new AssertionError("invalid date handled before confirmation");
                findButton(frame, "취소").doClick();
                if (args.length > 0) {
                    BufferedImage img = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = img.createGraphics(); frame.paint(g); g.dispose(); ImageIO.write(img, "png", Path.of(args[0]).toFile());
                }
                findButton(frame, "목록").doClick();
                JTable list = (JTable)field(frame, "pending"); int before = list.getRowCount();
                list.setRowSelectionInterval(0, 0); findButton(frame, "선택 작업 완료").doClick();
                if (list.getRowCount() != before - 1 || s.getSchedules(null, true).size() != 1) throw new AssertionError("complete UI");
                JTable history = (JTable)field(frame, "history"); history.setRowSelectionInterval(0, 0);
                findButton(frame, "선택한 작업 완료 취소").doClick();
                if (list.getRowCount() != before || !s.getSchedules(null, true).isEmpty()) throw new AssertionError("undo UI");
                JComboBox<?> filter = (JComboBox<?>)field(frame, "filter"); filter.setSelectedIndex(1);
                if (list.getRowCount() >= before) throw new AssertionError("filter UI");
                findButton(frame, "달력").doClick();
                for (int i = 0; i < 6; i++) findButton(frame, "다음 달").doClick();
                if (!((JLabel)field(frame, "count")).getText().contains("없습니다")) throw new AssertionError("empty selected month");
                System.out.println("PASS: GUI launch, disabled actions, zero registration, cancel, invalid date, completion, undo, filter, empty month");
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { frame.dispose(); }
        });
        Files.deleteIfExists(data); Files.delete(data.getParent());
    }
}
