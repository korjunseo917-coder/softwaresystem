package irrigation;

import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.imageio.ImageIO;
import javax.swing.*;

/** 완료 기록 화면에서 메모 저장·취소와 삭제된 기록 조회를 확인한다. */
public class CompletionGuiTest {
    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static <T> T find(Container container, Class<T> type) {
        for (Component item : container.getComponents()) {
            if (type.isInstance(item)) return type.cast(item);
            if (item instanceof Container child) {
                T found = find(child, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JTextArea notes(Container container) {
        for (Component item : container.getComponents()) {
            if (item instanceof JTextArea area && area.getRows() == 6) return area;
            if (item instanceof Container child) {
                JTextArea found = notes(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void capture(Window window, Path file) throws Exception {
        BufferedImage image = new BufferedImage(window.getWidth(), window.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        window.paint(graphics);
        graphics.dispose();
        ImageIO.write(image, "png", file.toFile());
    }

    private static void dialogAction(JButton button, String text, boolean save, boolean editable,
                                     Path screenshot) {
        final Throwable[] failure = {null};
        Timer timer = new Timer(300, event -> {
            for (Window window : Window.getWindows()) {
                if (!(window instanceof JDialog) || !window.isVisible()) continue;
                try {
                    JTextArea area = notes(window);
                    if (area == null || area.isEditable() != editable) throw new AssertionError("notes field state");
                    if (text != null) area.setText(text);
                    if (screenshot != null) capture(window, screenshot);
                    JOptionPane pane = find(window, JOptionPane.class);
                    pane.setValue(editable ? (save ? "저장" : "취소") : JOptionPane.OK_OPTION);
                } catch (Throwable error) {
                    failure[0] = error;
                    window.dispose();
                }
            }
        });
        timer.setRepeats(false);
        timer.start();
        button.doClick();
        timer.stop();
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        if (args.length > 1) {
            Font font = Font.createFonts(Path.of(args[1]).toFile())[0].deriveFont(14f);
            for (Object key : UIManager.getDefaults().keySet().toArray()) {
                if (UIManager.get(key) instanceof javax.swing.plaf.FontUIResource) {
                    UIManager.put(key, new javax.swing.plaf.FontUIResource(font));
                }
            }
        }
        Path data = Files.createTempDirectory("completion-gui-").resolve("test.dat");
        IrrigationService service = new IrrigationService(new FileStorage(data),
                Clock.fixed(Instant.parse("2026-11-05T03:00:00Z"), ZoneOffset.UTC));
        Zone zone = service.registerZone("수선화 밭", service.today(), service.today().plusDays(21),
                7, 2, "수선화가 심어진 동쪽 화단. 햇볕이 잘 들고 배수가 빠른 편이다.");
        String id = service.getSchedules(zone.getId(), false).get(0).getId();
        service.completeSchedule(id);
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame(service);
            try {
                frame.setVisible(true);
                JTabbedPane tabs = find(frame, JTabbedPane.class);
                tabs.setSelectedIndex(2);
                JTable history = (JTable) field(frame, "history");
                JButton details = (JButton) field(frame, "historyDetails");
                JButton undo = (JButton) field(frame, "undo");
                if (details.isEnabled()) throw new AssertionError("unselected details enabled");
                history.setRowSelectionInterval(0, 0);
                if (!details.isEnabled() || !undo.isEnabled()) throw new AssertionError("selected controls disabled");
                String memo = "실제 물 사용량: 1.5L\n강우 여부: 오전에 약한 비\n식물 상태: 잎이 처지지 않고 양호함";
                dialogAction(details, memo, true, true, null);
                if (!service.findSchedule(id).getCompletionNotes().equals(memo)) throw new AssertionError("save notes");
                capture(frame, output.resolve("completion-list.png"));
                dialogAction(details, null, false, true, output.resolve("completion-detail.png"));
                dialogAction(details, "취소한 입력", false, true, null);
                if (!service.findSchedule(id).getCompletionNotes().equals(memo)) throw new AssertionError("cancel changed notes");
                Timer cancelUndo = new Timer(200, event -> {
                    for (Window window : Window.getWindows()) {
                        if (window instanceof JDialog && window.isVisible()) {
                            find(window, JOptionPane.class).setValue(JOptionPane.CANCEL_OPTION);
                        }
                    }
                });
                cancelUndo.setRepeats(false);
                cancelUndo.start();
                undo.doClick();
                cancelUndo.stop();
                if (!service.findSchedule(id).isCompleted()) throw new AssertionError("undo confirmation ignored");
                service.deleteZone(zone.getId());
                var refresh = MainFrame.class.getDeclaredMethod("refreshAll");
                refresh.setAccessible(true);
                refresh.invoke(frame);
                history.setRowSelectionInterval(0, 0);
                if (undo.isEnabled() || !details.isEnabled()) throw new AssertionError("deleted controls");
                dialogAction(details, null, false, false, output.resolve("completion-deleted.png"));
                System.out.println("PASS: completion details, notes save/cancel, undo confirmation, deleted record read-only");
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                frame.dispose();
            }
        });
        Files.deleteIfExists(data);
        Files.delete(data.getParent());
    }
}
