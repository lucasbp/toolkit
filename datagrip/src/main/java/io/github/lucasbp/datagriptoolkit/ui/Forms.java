package io.github.lucasbp.datagriptoolkit.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.DefaultListCellRenderer;

final class Forms {
    private Forms() {}

    static javax.swing.JSplitPane split(JComponent left, JComponent right, int location) {
        var split = new javax.swing.JSplitPane(javax.swing.JSplitPane.HORIZONTAL_SPLIT, left, right) {
            @Override public void updateUI() {
                setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
                    @Override public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
                        return new javax.swing.plaf.basic.BasicSplitPaneDivider(this) {
                            @Override public void paint(java.awt.Graphics graphics) {
                                var color = javax.swing.UIManager.getColor("Panel.background");
                                graphics.setColor(color == null ? getBackground() : color);
                                graphics.fillRect(0, 0, getWidth(), getHeight());
                            }
                        };
                    }
                });
            }
        };
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setDividerSize(6);
        split.setContinuousLayout(true);
        split.setResizeWeight(0);
        split.setDividerLocation(location);
        return split;
    }

    static DefaultListCellRenderer literalRenderer() {
        var renderer = new DefaultListCellRenderer();
        renderer.putClientProperty("html.disable", Boolean.TRUE);
        return renderer;
    }

    static JPanel stack() {
        var panel = new JPanel() {
            @Override protected void addImpl(Component component, Object constraints, int index) {
                if (component instanceof JComponent child) child.setAlignmentX(Component.LEFT_ALIGNMENT);
                super.addImpl(component, constraints, index);
            }
        };
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        return panel;
    }

    static void field(JPanel parent, String label, JComponent component) {
        var row = new JPanel(new BorderLayout(0, 4));
        row.add(new JLabel(label), BorderLayout.NORTH);
        row.add(component, BorderLayout.CENTER);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 65));
        parent.add(row);
        parent.add(Box.createVerticalStrut(8));
    }

    static JButton button(JPanel parent, String title, Runnable action) {
        var button = new JButton(title);
        button.addActionListener(event -> {
            try { action.run(); }
            catch (IllegalArgumentException error) {
                JOptionPane.showMessageDialog(parent, error.getMessage(), "Server Management", JOptionPane.WARNING_MESSAGE);
            }
        });
        parent.add(button);
        return button;
    }

    static JTextArea preview(JPanel parent) {
        var text = new JTextArea(7, 36);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        parent.add(new JScrollPane(text));
        return text;
    }
}
