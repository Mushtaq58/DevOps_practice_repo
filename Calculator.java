import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import javax.sound.sampled.*;

public class Calculator extends JFrame {

    private final JTextField display = new JTextField("0");
    private final JCheckBox soundToggle = new JCheckBox("Sound", true);

    // Calculator state
    private BigDecimal storedValue = null;
    private String pendingOp = null;
    private boolean startNewNumber = true;
    private final MathContext mc = new MathContext(16, RoundingMode.HALF_UP);

    public Calculator() {
        super("Basic Calculator");

        display.setHorizontalAlignment(SwingConstants.RIGHT);
        display.setEditable(false);
        display.setFont(display.getFont().deriveFont(Font.BOLD, 26f));
        display.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel grid = new JPanel(new GridLayout(5, 4, 8, 8));
        String[] labels = {
                "C", "⌫", "÷", "×",
                "7", "8", "9", "-",
                "4", "5", "6", "+",
                "1", "2", "3", "=",
                "0", "00", ".", "="
        };
        for (String text : labels) {
            JButton b = new JButton(text);
            b.setFont(b.getFont().deriveFont(Font.PLAIN, 20f));
            b.setFocusPainted(false);
            b.addActionListener(e -> onButton(text));
            grid.add(b);
        }

        JPanel north = new JPanel(new BorderLayout());
        north.add(display, BorderLayout.CENTER);
        JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        topRight.add(soundToggle);
        north.add(topRight, BorderLayout.EAST);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.add(north, BorderLayout.NORTH);
        root.add(grid, BorderLayout.CENTER);

        addKeyBindings(root);

        setContentPane(root);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(360, 440);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void addKeyBindings(JComponent root) {
        int cond = JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT;
        InputMap im = root.getInputMap(cond);
        ActionMap am = root.getActionMap();

        String digits = "0123456789";
        for (char c : digits.toCharArray()) {
            bind(im, am, String.valueOf(c), () -> { appendDigit(String.valueOf(c)); clickDigit(); });
        }
        bind(im, am, ".", () -> { appendDot(); clickDigit(); });
        bind(im, am, "ENTER", () -> { equalsPressed(); toneEquals(); });
        bind(im, am, "EQUALS", () -> { equalsPressed(); toneEquals(); });

        Runnable add = () -> { operator("+"); clickOp(); };
        Runnable sub = () -> { operator("-"); clickOp(); };
        Runnable mul = () -> { operator("×"); clickOp(); };
        Runnable div = () -> { operator("÷"); clickOp(); };

        bind(im, am, "ADD", add);
        bind(im, am, "SUBTRACT", sub);
        bind(im, am, "MULTIPLY", mul);
        bind(im, am, "DIVIDE", div);
        bind(im, am, "+", add);
        bind(im, am, "-", sub);
        bind(im, am, "*", mul);
        bind(im, am, "/", div);

        bind(im, am, "BACK_SPACE", () -> { backspace(); clickMinor(); });
        bind(im, am, "DELETE", () -> { clearAll(); clickMinor(); });
        bind(im, am, "ESCAPE", () -> { clearAll(); clickMinor(); });
    }

    private void bind(InputMap im, ActionMap am, String key, Runnable r) {
        im.put(KeyStroke.getKeyStroke(key), key);
        am.put(key, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { r.run(); }
        });
    }

    private void onButton(String label) {
        switch (label) {
            case "0": case "1": case "2": case "3": case "4":
            case "5": case "6": case "7": case "8": case "9":
            case "00":
                appendDigit(label);
                clickDigit();
                break;
            case ".":
                appendDot();
                clickDigit();
                break;
            case "+": case "-": case "×": case "÷":
                operator(label);
                clickOp();
                break;
            case "=":
                equalsPressed();
                toneEquals();
                break;
            case "C":
                clearAll();
                clickMinor();
                break;
            case "⌫":
                backspace();
                clickMinor();
                break;
            default:
                break;
        }
    }

    private void appendDigit(String d) {
        if (startNewNumber) {
            if (d.equals("00")) display.setText("0");
            else if (d.equals("0")) display.setText("0");
            else display.setText(d);
            startNewNumber = false;
        } else {
            String cur = display.getText();
            if (cur.equals("0") && !d.equals(".")) cur = "";
            display.setText(cur + d);
        }
    }

    private void appendDot() {
        if (startNewNumber) {
            display.setText("0.");
            startNewNumber = false;
        } else if (!display.getText().contains(".")) {
            display.setText(display.getText() + ".");
        }
    }

    private void operator(String op) {
        try {
            BigDecimal current = parseDisplay();
            if (storedValue == null) {
                storedValue = current;
            } else if (!startNewNumber) {
                storedValue = compute(storedValue, current, pendingOp);
                displayResult(storedValue);
            }
            pendingOp = op;
            startNewNumber = true;
        } catch (ArithmeticException ex) {
            showError(ex.getMessage());
        }
    }

    private void equalsPressed() {
        try {
            if (pendingOp == null || storedValue == null) return;
            BigDecimal right = parseDisplay();
            storedValue = compute(storedValue, right, pendingOp);
            displayResult(storedValue);
            pendingOp = null;
            startNewNumber = true;
        } catch (ArithmeticException ex) {
            showError(ex.getMessage());
        }
    }

    private BigDecimal compute(BigDecimal a, BigDecimal b, String op) {
        if (op == null) return b;
        switch (op) {
            case "+": return a.add(b, mc);
            case "-": return a.subtract(b, mc);
            case "×": return a.multiply(b, mc);
            case "÷":
                if (b.compareTo(BigDecimal.ZERO) == 0) throw new ArithmeticException("Division by zero");
                try {
                    return a.divide(b, mc);
                } catch (ArithmeticException inexact) {
                    return a.divide(b, 12, RoundingMode.HALF_UP);
                }
            default: return b;
        }
    }

    private void clearAll() {
        display.setText("0");
        storedValue = null;
        pendingOp = null;
        startNewNumber = true;
    }

    private void backspace() {
        if (startNewNumber) return;
        String cur = display.getText();
        if (cur.length() <= 1) {
            display.setText("0");
            startNewNumber = true;
        } else {
            cur = cur.substring(0, cur.length() - 1);
            if (cur.equals("-") || cur.isEmpty()) {
                display.setText("0");
                startNewNumber = true;
            } else {
                display.setText(cur);
            }
        }
    }

    private BigDecimal parseDisplay() {
        String txt = display.getText();
        if (txt.equals(".") || txt.equals("-.")) txt = "0";
        return new BigDecimal(txt);
    }

    private void displayResult(BigDecimal val) {
        String s = val.stripTrailingZeros().toPlainString();
        display.setText(s);
    }

    private void showError(String msg) {
        toneError();
        display.setText("Error");
        storedValue = null;
        pendingOp = null;
        startNewNumber = true;
        JOptionPane.showMessageDialog(this, msg, "Calculation Error", JOptionPane.ERROR_MESSAGE);
    }

    // ---- Sound helpers ----
    private boolean soundOn() { return soundToggle.isSelected(); }
    private void clickDigit()  { if (soundOn()) Sound.playAsync(700, 40, 0.3); }
    private void clickOp()     { if (soundOn()) Sound.playAsync(520, 60, 0.35); }
    private void clickMinor()  { if (soundOn()) Sound.playAsync(420, 45, 0.28); }
    private void toneEquals()  { if (soundOn()) Sound.playAsync(880, 120, 0.35); }
    private void toneError()   { if (soundOn()) Sound.playAsync(220, 220, 0.4); }

    // Simple tone generator (16‑bit PCM, mono, 44.1kHz)
    static class Sound {
        private static final float SAMPLE_RATE = 44100f;

        static void playAsync(double freqHz, int durationMs, double volume) {
            new Thread(() -> {
                try {
                    play(freqHz, durationMs, volume);
                } catch (Exception ignored) {}
            }, "tone").start();
        }

        static void play(double freqHz, int durationMs, double volume) throws LineUnavailableException {
            AudioFormat fmt = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            try (SourceDataLine line = AudioSystem.getSourceDataLine(fmt)) {
                line.open(fmt);
                line.start();
                int totalSamples = (int) (durationMs / 1000.0 * SAMPLE_RATE);
                byte[] buffer = new byte[totalSamples * 2]; // 16-bit
                double twoPiF = 2 * Math.PI * freqHz;
                // Quick attack-decay envelope to avoid clicks
                int attack = Math.max(1, (int)(0.01 * totalSamples));
                int release = Math.max(1, (int)(0.08 * totalSamples));
                for (int i = 0; i < totalSamples; i++) {
                    double t = i / SAMPLE_RATE;
                    double env;
                    if (i < attack) env = i / (double) attack;
                    else if (i > totalSamples - release) env = (totalSamples - i) / (double) release;
                    else env = 1.0;
                    double val = Math.sin(twoPiF * t) * volume * env;
                    short s = (short) (val * Short.MAX_VALUE);
                    buffer[2 * i]     = (byte) (s & 0xff);
                    buffer[2 * i + 1] = (byte) ((s >> 8) & 0xff);
                }
                line.write(buffer, 0, buffer.length);
                line.drain();
            }
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(Calculator::new);
    }
}
