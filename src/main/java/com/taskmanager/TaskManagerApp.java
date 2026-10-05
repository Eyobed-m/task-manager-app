package com.taskmanager;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class TaskManagerApp {
    private static final Color INK = new Color(34, 45, 42);
    private static final Color MUTED = new Color(112, 124, 119);
    private static final Color PAPER = new Color(248, 249, 246);
    private static final Color GREEN = new Color(38, 112, 87);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final TaskStore store = new TaskStore(
            Path.of(System.getProperty("user.home"), ".task-manager", "tasks.tsv"));
    private final List<Task> tasks;
    private final TaskTableModel tableModel = new TaskTableModel();
    private final JTable table = new JTable(tableModel);
    private final TableRowSorter<TaskTableModel> sorter = new TableRowSorter<>(tableModel);
    private final JTextField searchField = new JTextField(22);
    private final JLabel summaryLabel = new JLabel();
    private final JLabel emptyLabel = new JLabel("No tasks match this view.", SwingConstants.CENTER);
    private String selectedStatus = "Open";
    private Task.Priority selectedPriority;

    private TaskManagerApp() throws IOException {
        tasks = store.load();
        createWindow();
        refresh();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                new TaskManagerApp();
            } catch (Exception exception) {
                JOptionPane.showMessageDialog(null,
                        "Could not open tasks: " + exception.getMessage(),
                        "Task Manager", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void createWindow() {
        JFrame frame = new JFrame("Task Manager");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(760, 520));
        frame.setSize(1020, 690);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(PAPER);
        frame.setLayout(new BorderLayout(0, 0));
        frame.add(buildHeader(), BorderLayout.NORTH);
        frame.add(buildWorkspace(), BorderLayout.CENTER);
        frame.setVisible(true);
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setBackground(PAPER);
        header.setBorder(BorderFactory.createEmptyBorder(28, 34, 18, 34));

        JPanel titles = new JPanel(new BorderLayout(0, 4));
        titles.setOpaque(false);
        JLabel title = new JLabel("Your tasks");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 27));
        title.setForeground(INK);
        JLabel subtitle = new JLabel("A clear view of what matters next.");
        subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        subtitle.setForeground(MUTED);
        titles.add(title, BorderLayout.NORTH);
        titles.add(subtitle, BorderLayout.SOUTH);

        JButton addButton = button("+  New task", GREEN, Color.WHITE);
        addButton.addActionListener(event -> addTask());
        header.add(titles, BorderLayout.WEST);
        header.add(addButton, BorderLayout.EAST);
        return header;
    }

    private JComponent buildWorkspace() {
        JPanel workspace = new JPanel(new BorderLayout(0, 14));
        workspace.setBackground(PAPER);
        workspace.setBorder(BorderFactory.createEmptyBorder(0, 34, 28, 34));
        workspace.add(buildToolbar(), BorderLayout.NORTH);

        table.setRowHeight(48);
        table.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        table.setForeground(INK);
        table.setBackground(Color.WHITE);
        table.setSelectionBackground(new Color(226, 239, 232));
        table.setSelectionForeground(INK);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setRowSorter(sorter);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(0, 40));
        table.getTableHeader().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        table.getTableHeader().setForeground(MUTED);
        table.getTableHeader().setBackground(new Color(242, 244, 240));
        table.getColumnModel().getColumn(0).setPreferredWidth(410);
        table.getColumnModel().getColumn(1).setPreferredWidth(145);
        table.getColumnModel().getColumn(2).setPreferredWidth(110);
        table.getColumnModel().getColumn(3).setPreferredWidth(90);
        table.getColumnModel().getColumn(1).setCellRenderer(new DateRenderer());
        table.getColumnModel().getColumn(2).setCellRenderer(new PriorityRenderer());
        table.getColumnModel().getColumn(3).setCellRenderer(new StatusRenderer());

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 230, 224)));
        scrollPane.getViewport().setBackground(Color.WHITE);
        JPanel list = new JPanel(new BorderLayout());
        list.setBackground(Color.WHITE);
        list.add(scrollPane, BorderLayout.CENTER);
        emptyLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        emptyLabel.setForeground(MUTED);
        emptyLabel.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));
        list.add(emptyLabel, BorderLayout.SOUTH);
        workspace.add(list, BorderLayout.CENTER);
        return workspace;
    }

    private JComponent buildToolbar() {
        JPanel area = new JPanel(new BorderLayout(0, 14));
        area.setOpaque(false);

        JPanel summary = new JPanel(new BorderLayout());
        summary.setOpaque(false);
        summaryLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        summaryLabel.setForeground(MUTED);
        summary.add(summaryLabel, BorderLayout.WEST);
        area.add(summary, BorderLayout.NORTH);

        JPanel controls = new JPanel(new BorderLayout(12, 0));
        controls.setOpaque(false);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        filters.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        for (String status : List.of("Open", "All", "Done")) {
            JToggleButton toggle = new JToggleButton(status, status.equals("Open"));
            styleToggle(toggle);
            toggle.addActionListener(event -> {
                selectedStatus = status;
                applyFilter();
            });
            group.add(toggle);
            filters.add(toggle);
        }

        JComboBox<Object> priorityFilter = new JComboBox<>(new Object[]{"Any priority", Task.Priority.HIGH,
                Task.Priority.MEDIUM, Task.Priority.LOW});
        priorityFilter.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        priorityFilter.setPreferredSize(new Dimension(142, 34));
        priorityFilter.addActionListener(event -> {
            Object value = priorityFilter.getSelectedItem();
            selectedPriority = value instanceof Task.Priority priority ? priority : null;
            applyFilter();
        });
        filters.add(priorityFilter);

        JPanel searchWrap = new JPanel(new BorderLayout());
        searchWrap.setOpaque(false);
        searchField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        searchField.setToolTipText("Search tasks and details");
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(218, 224, 218)),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { applyFilter(); }
            public void removeUpdate(DocumentEvent event) { applyFilter(); }
            public void changedUpdate(DocumentEvent event) { applyFilter(); }
        });
        searchWrap.add(searchField, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        actions.setOpaque(false);
        JButton editButton = button("Edit", new Color(232, 237, 231), INK);
        editButton.addActionListener(event -> editTask());
        JButton doneButton = button("Complete", new Color(232, 237, 231), INK);
        doneButton.addActionListener(event -> toggleComplete());
        JButton deleteButton = button("Delete", new Color(250, 233, 229), new Color(154, 68, 53));
        deleteButton.addActionListener(event -> deleteTask());
        actions.add(editButton);
        actions.add(doneButton);
        actions.add(deleteButton);

        JPanel left = new JPanel(new BorderLayout(12, 0));
        left.setOpaque(false);
        left.add(filters, BorderLayout.WEST);
        left.add(searchWrap, BorderLayout.CENTER);
        controls.add(left, BorderLayout.CENTER);
        controls.add(actions, BorderLayout.EAST);
        area.add(controls, BorderLayout.SOUTH);
        return area;
    }

    private void addTask() {
        TaskDraft draft = showTaskDialog(null);
        if (draft == null) return;
        tasks.add(new Task(UUID.randomUUID(), draft.title(), draft.details(), draft.priority(), draft.dueDate(), false));
        persistAndRefresh();
    }

    private void editTask() {
        Task task = selectedTask();
        if (task == null) return;
        TaskDraft draft = showTaskDialog(task);
        if (draft == null) return;
        replaceTask(task.withChanges(draft.title(), draft.details(), draft.priority(), draft.dueDate()));
        persistAndRefresh();
    }

    private void toggleComplete() {
        Task task = selectedTask();
        if (task == null) return;
        replaceTask(task.withCompleted(!task.completed()));
        persistAndRefresh();
    }

    private void deleteTask() {
        Task task = selectedTask();
        if (task == null) return;
        int answer = JOptionPane.showConfirmDialog(null,
                "Delete \"" + task.title() + "\"?", "Delete task", JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            tasks.removeIf(item -> item.id().equals(task.id()));
            persistAndRefresh();
        }
    }

    private TaskDraft showTaskDialog(Task task) {
        JTextField titleField = new JTextField(task == null ? "" : task.title(), 28);
        JTextField dueDateField = new JTextField(task == null || task.dueDate() == null
                ? "" : task.dueDate().toString(), 12);
        JComboBox<Task.Priority> priorityBox = new JComboBox<>(Task.Priority.values());
        if (task != null) priorityBox.setSelectedItem(task.priority());
        JTextArea detailsArea = new JTextArea(task == null ? "" : task.details(), 4, 28);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(5, 5, 5, 5);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        addFormRow(form, constraints, 0, "Task", titleField);
        addFormRow(form, constraints, 1, "Due date", dueDateField);
        addFormRow(form, constraints, 2, "Priority", priorityBox);
        addFormRow(form, constraints, 3, "Details", new JScrollPane(detailsArea));

        while (true) {
            int answer = JOptionPane.showConfirmDialog(null, form,
                    task == null ? "New task" : "Edit task", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) {
                return null;
            }
            TaskDraft draft = readTaskDraft(titleField, detailsArea, priorityBox, dueDateField);
            if (draft != null) return draft;
        }
    }

    private TaskDraft readTaskDraft(JTextField titleField, JTextArea detailsArea,
            JComboBox<Task.Priority> priorityBox, JTextField dueDateField) {
        try {
            TaskDraft draft = createDraft(titleField.getText(), detailsArea.getText(),
                    (Task.Priority) priorityBox.getSelectedItem(), dueDateField.getText());
            if (draft == null) {
                JOptionPane.showMessageDialog(null, "Enter a task name.", "Missing task name",
                        JOptionPane.WARNING_MESSAGE);
            }
            return draft;
        } catch (DateTimeParseException exception) {
            JOptionPane.showMessageDialog(null, "Use YYYY-MM-DD for the due date, or leave it blank.",
                    "Invalid date", JOptionPane.WARNING_MESSAGE);
            return null;
        }
    }

    private TaskDraft createDraft(String title, String details, Task.Priority priority, String dateText) {
        String cleanedTitle = title.trim();
        if (cleanedTitle.isEmpty()) return null;
        String cleanedDate = dateText.trim();
        LocalDate dueDate = cleanedDate.isEmpty() ? null : LocalDate.parse(cleanedDate);
        return new TaskDraft(cleanedTitle, details.trim(), priority, dueDate);
    }

    private void addFormRow(JPanel form, GridBagConstraints constraints, int row, String label, JComponent input) {
        constraints.gridy = row;
        constraints.gridx = 0;
        constraints.weightx = 0;
        form.add(new JLabel(label), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        form.add(input, constraints);
    }

    private Task selectedTask() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(null, "Select a task first.", "No task selected",
                    JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return tableModel.getTask(table.convertRowIndexToModel(viewRow));
    }

    private void replaceTask(Task replacement) {
        for (int index = 0; index < tasks.size(); index++) {
            if (tasks.get(index).id().equals(replacement.id())) {
                tasks.set(index, replacement);
                return;
            }
        }
    }

    private void persistAndRefresh() {
        try {
            store.save(tasks);
            refresh();
        } catch (Exception exception) {
            JOptionPane.showMessageDialog(null, "Could not save tasks: " + exception.getMessage(),
                    "Save error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refresh() {
        tasks.sort(Comparator.comparing(Task::completed)
                .thenComparing(task -> task.dueDate() == null ? LocalDate.MAX : task.dueDate())
                .thenComparingInt(task -> task.priority().ordinal()));
        tableModel.setTasks(tasks);
        applyFilter();
        long openCount = tasks.stream().filter(task -> !task.completed()).count();
        long doneCount = tasks.size() - openCount;
        summaryLabel.setText(tasks.size() + " tasks     " + openCount + " open     " + doneCount + " completed");
    }

    private void applyFilter() {
        String query = searchField.getText().trim().toLowerCase();
        sorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends TaskTableModel, ? extends Integer> entry) {
                Task task = tableModel.getTask(entry.getIdentifier());
                boolean statusMatches = selectedStatus.equals("All")
                        || selectedStatus.equals("Done") == task.completed();
                boolean priorityMatches = selectedPriority == null || task.priority() == selectedPriority;
                boolean queryMatches = query.isEmpty()
                        || task.title().toLowerCase().contains(query)
                        || task.details().toLowerCase().contains(query);
                return statusMatches && priorityMatches && queryMatches;
            }
        });
        emptyLabel.setVisible(table.getRowCount() == 0);
    }

    private JButton button(String text, Color background, Color foreground) {
        JButton button = new JButton(text);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        button.setForeground(foreground);
        button.setBackground(background);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(9, 13, 9, 13));
        return button;
    }

    private void styleToggle(JToggleButton toggle) {
        toggle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        toggle.setFocusPainted(false);
        toggle.setBorder(BorderFactory.createEmptyBorder(8, 11, 8, 11));
        toggle.addChangeListener(event -> {
            boolean selected = toggle.isSelected();
            toggle.setBackground(selected ? new Color(222, 235, 226) : PAPER);
            toggle.setForeground(selected ? GREEN : MUTED);
        });
        toggle.setBackground(toggle.isSelected() ? new Color(222, 235, 226) : PAPER);
        toggle.setForeground(toggle.isSelected() ? GREEN : MUTED);
    }

    private record TaskDraft(String title, String details, Task.Priority priority, LocalDate dueDate) { }

    private static final class TaskTableModel extends AbstractTableModel {
        private final String[] columns = {"TASK", "DUE DATE", "PRIORITY", "STATUS"};
        private transient List<Task> tasks = List.of();

        public void setTasks(List<Task> tasks) {
            this.tasks = new ArrayList<>(tasks);
            fireTableDataChanged();
        }

        public Task getTask(int row) {
            return tasks.get(row);
        }

        @Override
        public int getRowCount() { return tasks.size(); }

        @Override
        public int getColumnCount() { return columns.length; }

        @Override
        public String getColumnName(int column) { return columns[column]; }

        @Override
        public Class<?> getColumnClass(int column) {
            return switch (column) {
                case 1 -> LocalDate.class;
                case 2 -> Task.Priority.class;
                case 3 -> Boolean.class;
                default -> String.class;
            };
        }

        @Override
        public Object getValueAt(int row, int column) {
            Task task = tasks.get(row);
            return switch (column) {
                case 0 -> task.title();
                case 1 -> task.dueDate();
                case 2 -> task.priority();
                case 3 -> task.completed();
                default -> "";
            };
        }
    }

    private static final class DateRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value == null ? "No date"
                    : DATE_FORMAT.format((LocalDate) value), selected, focused, row, column);
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
            setForeground(value == null && !selected ? MUTED : INK);
            return this;
        }
    }

    private static final class PriorityRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            setText(value == null ? "" : value.toString());
            setFont(getFont().deriveFont(Font.BOLD, 11f));
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
            if (!selected && value instanceof Task.Priority priority) {
                setForeground(switch (priority) {
                    case HIGH -> new Color(176, 75, 59);
                    case MEDIUM -> new Color(163, 116, 39);
                    case LOW -> GREEN;
                });
            }
            return this;
        }
    }

    private static final class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                boolean focused, int row, int column) {
            boolean completed = Boolean.TRUE.equals(value);
            super.getTableCellRendererComponent(table, completed ? "Done" : "Open", selected, focused, row,
                    column);
            setFont(getFont().deriveFont(Font.BOLD, 11f));
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
            if (selected) setForeground(INK);
            else setForeground(completed ? MUTED : GREEN);
            return this;
        }
    }
}