import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class Calculator extends JFrame {

    private final JTextField display = new JTextField("0");

    // Calculator state
    private BigDecimal storedValue = null;     // left operand
    private String pendingOp = null;           // "+", "-", "×", "÷"
    private boolean startNewNumber = true;     // whether next digit starts a new number
    private final MathContext mc = new MathContext(16, RoundingMode.HALF_UP);

    public Calculator() {
        super("Basic Calculator");

        // --- Display setup ---
        display.setHorizontalAlignment(SwingConstants.RIGHT);
        display.setEditable(false);
        display.setFont(display.getFont().deriveFont(Font.BOLD, 26f));
        display.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // --- Buttons grid ---
        JPanel grid = new JPanel(new GridLayout(5, 4, 8, 8));

        String[] labels = {
            "C", "⌫", "÷", "×",
            "7", "8", "9", "-",
            "4", "5", "6", "+",
            "1", "2", "3", "=",
            "0", "00", ".", "=" // The last "=" just fills the grid; action will be same
        };

        for (String text : labels) {
            JButton b = new JButton(text);
            b.setFont(b.getFont().deriveFont(Font.PLAIN, 20f));
            b.setFocusPainted(false);
            b.addActionListener(e -> onButton(text));
            grid.add(b);
        }

        // --- Layout ---
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.add(display, BorderLayout.NORTH);
        root.add(grid, BorderLayout.CENTER);

        // --- Keyboard support ---
        addKeyBindings(root);

        setContentPane(root);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(340, 420);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void addKeyBindings(JComponent root) {
        // Map keys to actions using InputMap/ActionMap (works regardless of focus)
        int cond = JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT;
        InputMap im = root.getInputMap(cond);
        ActionMap am = root.getActionMap();

        String digits = "0123456789";
        for (char c : digits.toCharArray()) {
            bind(im, am, String.valueOf(c), () -> appendDigit(String.valueOf(c)));
        }
        bind(im, am, ".", () -> appendDot());
        bind(im, am, "ENTER", this::equalsPressed);
        bind(im, am, "EQUALS", this::equalsPressed);
        bind(im, am, "ADD", () -> operator("+"));
        bind(im, am, "SUBTRACT", () -> operator("-"));
        bind(im, am, "MULTIPLY", () -> operator("×"));
        bind(im, am, "DIVIDE", () -> operator("÷"));
        bind(im, am, "PLUS", () -> operator("+"));
        bind(im, am, "MINUS", () -> operator("-"));

        // Also map ASCII keys
        bind(im, am, "+", () -> operator("+"));
        bind(im, am, "-", () -> operator("-"));
        bind(im, am, "*", () -> operator("×"));
        bind(im, am, "/", () -> operator("÷"));

        // Clear and backspace
        bind(im, am, "BACK_SPACE", this::backspace);
        bind(im, am, "DELETE", this::clearAll);
        bind(im, am, "ESCAPE", this::clearAll);
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
                break;
            case ".":
                appendDot();
                break;
            case "+": case "-": case "×": case "÷":
                operator(label);
                break;
            case "=":
                equalsPressed();
                break;
            case "C":
                clearAll();
                break;
            case "⌫":
                backspace();
                break;
            default:
                break;
        }
    }

    private void appendDigit(String d) {
        if (startNewNumber) {
            // Avoid leading zeros like 0002; allow "0." start
            if (d.equals("00")) {
                display.setText("0");
            } else if (d.equals("0")) {
                display.setText("0");
            } else {
                display.setText(d);
            }
            startNewNumber = false;
        } else {
            String cur = display.getText();
            // If current is "0" and we append a digit (not a dot), replace instead of append
            if (cur.equals("0") && !d.equals("."))
                cur = "";
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
            if (pendingOp == null || storedValue == null) {
                // Nothing to do
                return;
            }
            BigDecimal right = parseDisplay();
            storedValue = compute(storedValue, right, pendingOp);
            displayResult(storedValue);
            // After equals, allow chaining: result becomes left operand; op cleared
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
                if (b.compareTo(BigDecimal.ZERO) == 0)
                    throw new ArithmeticException("Division by zero");
                // Try exact; if not, scale reasonably
                try {
                    return a.divide(b, mc);
                } catch (ArithmeticException inexact) {
                    return a.divide(b, 12, RoundingMode.HALF_UP);
                }
            default:  return b;
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
            // Avoid dangling minus or empty string
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
        // Clean formatting: strip trailing zeros and use plain string
        String s = val.stripTrailingZeros().toPlainString();
        display.setText(s);
    }

    private void showError(String msg) {
        display.setText("Error");
        storedValue = null;
        pendingOp = null;
        startNewNumber = true;
        // Optional: show a brief dialog
        JOptionPane.showMessageDialog(this, msg, "Calculation Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        // Native look & feel (if available)
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(Calculator::new);
    }
}
