package com.taskmanager;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
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
import javax.swing.border.AbstractBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public final class TaskManagerApp {
    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(TaskManagerApp.class);
    private static Color INK;
    private static Color MUTED;
    private static Color PAPER;
    private static Color GREEN;
    private static Color ACCENT_SOFT;
    private static Color PANEL;
    private static Color BORDER;
    private static Color ADD_PANEL;
    private static Color DANGER;
    private static Color BUTTON_SOFT;
    private static Color DANGER_SOFT;
    private static Color TABLE_HEADER;
    private static Color TABLE_SELECT;
    private static Color COMPLETED_BG;
    private static Color TOGGLE_SELECTED;
    private static Color CONNECTED_BORDER;
    private static Color NEUTRAL;
    private static Color HIGH_PRIORITY;
    private static Color MEDIUM_PRIORITY;
    private static Color PRIMARY_ACTION;
    private static Color PRIMARY_ACTION_TEXT;

    static {
        setPalette(false);
    }
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
    private final JLabel gmailStatusLabel = statusPill("Gmail", false);
    private final JLabel canvasStatusLabel = statusPill("Canvas", false);
    private final JPanel calendarGrid = new JPanel(new GridLayout(6, 7, 8, 8));
    private final JLabel calendarMonthLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel calendarSummaryLabel = new JLabel();
    private YearMonth displayedMonth = YearMonth.now();
    private String selectedSection = "TASKS";
    private boolean searchListenerInstalled;
    private JPanel sourceChoices;
    private JFrame frame;
    private boolean darkMode;
    private Task.Source selectedSource;
    private String selectedStatus = "Open";
    private Task.Priority selectedPriority;
    private CanvasClient canvasClient;
    private GmailClient gmailClient;
    private boolean syncInProgress;

    private TaskManagerApp() throws IOException {
        tasks = store.load();
        darkMode = PREFERENCES.getBoolean("darkMode", false);
        setPalette(darkMode);
        applyLookAndFeelTheme();
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
        frame = new JFrame("Task Manager");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(760, 520));
        frame.setSize(1020, 690);
        frame.setLocationRelativeTo(null);
        rebuildWindowContents();
        frame.setVisible(true);
    }

    private void rebuildWindowContents() {
        frame.getContentPane().removeAll();
        frame.getContentPane().setBackground(PAPER);
        frame.setLayout(new BorderLayout(0, 0));
        JPanel shell = new JPanel(new BorderLayout(0, 8));
        shell.setBackground(PAPER);
        shell.add(buildHeader(), BorderLayout.NORTH);

        CardLayout sectionLayout = new CardLayout();
        JPanel sectionCards = new JPanel(sectionLayout);
        sectionCards.setBackground(PAPER);
        sectionCards.add(buildWorkspace(), "TASKS");
        sectionCards.add(buildCalendar(), "CALENDAR");
        JPanel sectionArea = new JPanel(new BorderLayout(0, 14));
        sectionArea.setBackground(PAPER);
        sectionArea.setBorder(BorderFactory.createEmptyBorder(0, 30, 26, 30));
        sectionArea.add(buildSectionNavigation(sectionLayout, sectionCards), BorderLayout.NORTH);
        sectionArea.add(sectionCards, BorderLayout.CENTER);
        shell.add(sectionArea, BorderLayout.CENTER);
        frame.add(shell, BorderLayout.CENTER);
        frame.revalidate();
        frame.repaint();
    }

    private JComponent buildSectionNavigation(CardLayout sectionLayout, JPanel sectionCards) {
        JPanel navigation = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        navigation.setOpaque(false);
        navigation.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        JToggleButton tasksButton = navigationButton("Tasks", "TASKS".equals(selectedSection));
        JToggleButton calendarButton = navigationButton("Calendar", "CALENDAR".equals(selectedSection));
        ButtonGroup group = new ButtonGroup();
        group.add(tasksButton);
        group.add(calendarButton);
        tasksButton.addChangeListener(event -> updateNavigationButton(tasksButton));
        calendarButton.addChangeListener(event -> updateNavigationButton(calendarButton));
        tasksButton.addActionListener(event -> {
            selectedSection = "TASKS";
            sectionLayout.show(sectionCards, selectedSection);
        });
        calendarButton.addActionListener(event -> {
            selectedSection = "CALENDAR";
            sectionLayout.show(sectionCards, selectedSection);
        });
        navigation.add(tasksButton);
        navigation.add(calendarButton);
        sectionLayout.show(sectionCards, selectedSection);
        return navigation;
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setBackground(PAPER);
        header.setBorder(BorderFactory.createEmptyBorder(22, 30, 12, 30));

        JPanel titles = new JPanel(new BorderLayout(0, 6));
        titles.setOpaque(false);
        JLabel title = new JLabel("Your tasks");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        title.setForeground(INK);
        JLabel subtitle = new JLabel("A quiet space to plan what matters.");
        subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        subtitle.setForeground(MUTED);
        JPanel accountStatuses = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        accountStatuses.setOpaque(false);
        accountStatuses.add(gmailStatusLabel);
        accountStatuses.add(canvasStatusLabel);
        updateConnectionBadges();
        titles.add(title, BorderLayout.NORTH);
        titles.add(subtitle, BorderLayout.CENTER);
        titles.add(accountStatuses, BorderLayout.SOUTH);

        JButton addButton = button("+  New task", PRIMARY_ACTION, PRIMARY_ACTION_TEXT);
        addButton.addActionListener(event -> addTask());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton canvasButton = button("Connect Canvas", BUTTON_SOFT, INK);
        canvasButton.addActionListener(event -> syncCanvas());
        JButton gmailButton = button("Connect Gmail", BUTTON_SOFT, INK);
        gmailButton.addActionListener(event -> syncGmail());
        JButton themeButton = button(darkMode ? "Light mode" : "Dark mode", BUTTON_SOFT, INK);
        themeButton.addActionListener(event -> toggleTheme());
        actions.add(canvasButton);
        actions.add(gmailButton);
        actions.add(themeButton);
        actions.add(addButton);
        header.add(titles, BorderLayout.WEST);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private JComponent buildWorkspace() {
        JPanel workspace = new JPanel(new BorderLayout(0, 14));
        workspace.setBackground(PAPER);
        workspace.setOpaque(false);
        workspace.add(buildToolbar(), BorderLayout.NORTH);

        table.setRowHeight(48);
        table.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        table.setForeground(INK);
        table.setBackground(PANEL);
        table.setOpaque(true);
        table.setSelectionBackground(TABLE_SELECT);
        table.setSelectionForeground(INK);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setRowSorter(sorter);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(0, 42));
        table.getTableHeader().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        table.getTableHeader().setForeground(MUTED);
        table.getTableHeader().setBackground(TABLE_HEADER);
        table.getTableHeader().setOpaque(true);
        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer();
        headerRenderer.setBackground(TABLE_HEADER);
        headerRenderer.setForeground(MUTED);
        headerRenderer.setOpaque(true);
        headerRenderer.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        table.getTableHeader().setDefaultRenderer(headerRenderer);
        table.setDefaultRenderer(String.class, new ThemedTableRenderer());
        table.setDefaultRenderer(Task.Source.class, new ThemedTableRenderer());
        table.getColumnModel().getColumn(0).setPreferredWidth(410);
        table.getColumnModel().getColumn(1).setPreferredWidth(145);
        table.getColumnModel().getColumn(2).setPreferredWidth(110);
        table.getColumnModel().getColumn(3).setPreferredWidth(90);
        table.getColumnModel().getColumn(4).setPreferredWidth(85);
        table.getColumnModel().getColumn(1).setCellRenderer(new DateRenderer());
        table.getColumnModel().getColumn(2).setCellRenderer(new PriorityRenderer());
        table.getColumnModel().getColumn(3).setCellRenderer(new StatusRenderer());

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(PANEL);
        SurfacePanel list = new SurfacePanel(new BorderLayout(), PANEL, true);
        list.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        list.add(scrollPane, BorderLayout.CENTER);
        emptyLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        emptyLabel.setForeground(MUTED);
        emptyLabel.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));
        list.add(emptyLabel, BorderLayout.SOUTH);
        workspace.add(list, BorderLayout.CENTER);
        return workspace;
    }

    private JComponent buildCalendar() {
        JPanel calendar = new JPanel(new BorderLayout(0, 14));
        calendar.setOpaque(false);
        calendar.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JPanel calendarHeader = new JPanel(new BorderLayout());
        calendarHeader.setOpaque(false);
        JPanel monthControls = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        monthControls.setOpaque(false);
        JButton previousMonth = button("<", PANEL, INK);
        previousMonth.setToolTipText("Previous month");
        previousMonth.addActionListener(event -> {
            displayedMonth = displayedMonth.minusMonths(1);
            updateCalendar();
        });
        JButton nextMonth = button(">", PANEL, INK);
        nextMonth.setToolTipText("Next month");
        nextMonth.addActionListener(event -> {
            displayedMonth = displayedMonth.plusMonths(1);
            updateCalendar();
        });
        calendarMonthLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        calendarMonthLabel.setForeground(INK);
        calendarMonthLabel.setPreferredSize(new Dimension(190, 34));
        monthControls.add(previousMonth);
        monthControls.add(calendarMonthLabel);
        monthControls.add(nextMonth);
        calendarSummaryLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        calendarSummaryLabel.setForeground(MUTED);
        calendarHeader.add(monthControls, BorderLayout.CENTER);
        calendarHeader.add(calendarSummaryLabel, BorderLayout.SOUTH);
        calendar.add(calendarHeader, BorderLayout.NORTH);

        SurfacePanel calendarSurface = new SurfacePanel(new BorderLayout(0, 10), PANEL, true);
        calendarSurface.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JPanel calendarContent = new JPanel(new BorderLayout(0, 8));
        calendarContent.setOpaque(false);
        JPanel weekdays = new JPanel(new GridLayout(1, 7, 8, 0));
        weekdays.setOpaque(false);
        for (String day : List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")) {
            JLabel label = new JLabel(day, SwingConstants.CENTER);
            label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            label.setForeground(MUTED);
            weekdays.add(label);
        }
        calendarGrid.setOpaque(false);
        calendarContent.add(weekdays, BorderLayout.NORTH);
        calendarContent.add(calendarGrid, BorderLayout.CENTER);
        calendarSurface.add(calendarContent, BorderLayout.CENTER);
        calendar.add(calendarSurface, BorderLayout.CENTER);
        updateCalendar();
        return calendar;
    }

    private void updateCalendar() {
        if (calendarGrid == null) return;
        calendarMonthLabel.setText(displayedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        calendarGrid.removeAll();
        LocalDate firstDay = displayedMonth.atDay(1);
        int leadingDays = firstDay.getDayOfWeek().getValue() - 1;
        int taskCount = 0;
        for (int cell = 0; cell < 42; cell++) {
            int dayNumber = cell - leadingDays + 1;
            if (dayNumber < 1 || dayNumber > displayedMonth.lengthOfMonth()) {
                JPanel blankDay = new JPanel();
                blankDay.setOpaque(false);
                calendarGrid.add(blankDay);
                continue;
            }
            LocalDate date = displayedMonth.atDay(dayNumber);
            List<Task> dueTasks = tasks.stream()
                    .filter(task -> date.equals(task.dueDate()))
                    .toList();
            taskCount += dueTasks.size();
            calendarGrid.add(buildCalendarDay(date, dueTasks));
        }
        calendarSummaryLabel.setText(taskCount + " scheduled task" + (taskCount == 1 ? "" : "s")
                + " this month");
        calendarGrid.revalidate();
        calendarGrid.repaint();
    }

    private JComponent buildCalendarDay(LocalDate date, List<Task> dueTasks) {
        SurfacePanel dayCell = new SurfacePanel(new BorderLayout(0, 6), PANEL, false);
        dayCell.setBorder(BorderFactory.createCompoundBorder(
                new RoundedLineBorder(date.equals(LocalDate.now()) ? GREEN : BORDER, 14),
                BorderFactory.createEmptyBorder(7, 8, 6, 8)));

        JLabel dayLabel = new JLabel(Integer.toString(date.getDayOfMonth()));
        dayLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        dayLabel.setForeground(date.equals(LocalDate.now()) ? GREEN : INK);
        dayCell.add(dayLabel, BorderLayout.NORTH);

        JPanel taskList = new JPanel();
        taskList.setOpaque(false);
        taskList.setLayout(new javax.swing.BoxLayout(taskList, javax.swing.BoxLayout.Y_AXIS));
        int shown = Math.min(dueTasks.size(), 3);
        for (int index = 0; index < shown; index++) {
            Task task = dueTasks.get(index);
            JLabel taskLabel = new RoundedLabel("  " + task.title());
            taskLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            taskLabel.setForeground(task.completed() ? MUTED : GREEN);
            taskLabel.setOpaque(true);
            taskLabel.setBackground(task.completed() ? COMPLETED_BG : ACCENT_SOFT);
            taskLabel.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
            taskLabel.setToolTipText(task.title() + " · " + task.priority()
                    + (task.completed() ? " · Completed" : ""));
            taskList.add(taskLabel);
            taskList.add(javax.swing.Box.createVerticalStrut(3));
        }
        if (dueTasks.size() > shown) {
            JLabel moreLabel = new JLabel("+" + (dueTasks.size() - shown) + " more");
            moreLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            moreLabel.setForeground(MUTED);
            taskList.add(moreLabel);
        }
        dayCell.add(taskList, BorderLayout.CENTER);
        return dayCell;
    }

    private JComponent buildToolbar() {
        SurfacePanel area = new SurfacePanel(new BorderLayout(0, 12), PANEL, true);
        area.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        JPanel summary = new JPanel(new BorderLayout());
        summary.setOpaque(false);
        summaryLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        summaryLabel.setForeground(MUTED);
        summaryLabel.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
        summary.add(summaryLabel, BorderLayout.WEST);
        area.add(summary, BorderLayout.NORTH);
        area.add(buildSourceFilters(), BorderLayout.CENTER);

        JPanel controls = new JPanel(new BorderLayout(12, 0));
        controls.setOpaque(false);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        filters.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        for (String status : List.of("Open", "All", "Done")) {
            JToggleButton toggle = new StyledToggleButton(status, status.equals(selectedStatus));
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
        priorityFilter.setUI(new javax.swing.plaf.basic.BasicComboBoxUI());
        if (selectedPriority != null) priorityFilter.setSelectedItem(selectedPriority);
        priorityFilter.setBackground(BUTTON_SOFT);
        priorityFilter.setForeground(INK);
        priorityFilter.setOpaque(true);
        priorityFilter.setBorder(BorderFactory.createCompoundBorder(
                new RoundedLineBorder(BORDER, 12),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        priorityFilter.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                    int index, boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                setBackground(selected ? TOGGLE_SELECTED : BUTTON_SOFT);
                setForeground(INK);
                setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
                return this;
            }
        });
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
        searchField.setBackground(PANEL);
        searchField.setForeground(INK);
        searchField.setCaretColor(INK);
        searchField.setToolTipText("Search tasks and details");
        searchField.setBorder(BorderFactory.createCompoundBorder(
                new RoundedLineBorder(BORDER, 12),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)));
        if (!searchListenerInstalled) {
            searchField.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent event) { applyFilter(); }
                public void removeUpdate(DocumentEvent event) { applyFilter(); }
                public void changedUpdate(DocumentEvent event) { applyFilter(); }
            });
            searchListenerInstalled = true;
        }
        searchWrap.add(searchField, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        actions.setOpaque(false);
        JButton editButton = button("Edit", BUTTON_SOFT, INK);
        editButton.addActionListener(event -> editTask());
        JButton doneButton = button("Complete", ACCENT_SOFT, GREEN);
        doneButton.addActionListener(event -> toggleComplete());
        JButton deleteButton = button("Delete", DANGER_SOFT, DANGER);
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

    private JComponent buildSourceFilters() {
        JPanel sourceSection = new JPanel(new BorderLayout(0, 7));
        sourceSection.setOpaque(false);
        JLabel heading = new JLabel("TASK COLLECTION");
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        heading.setForeground(MUTED);
        heading.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
        sourceSection.add(heading, BorderLayout.NORTH);

        sourceChoices = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        sourceChoices.setOpaque(false);
        rebuildSourceChoices();
        sourceSection.add(sourceChoices, BorderLayout.CENTER);
        return sourceSection;
    }

    private void rebuildSourceChoices() {
        if (sourceChoices == null) return;
        sourceChoices.removeAll();
        ButtonGroup group = new ButtonGroup();
        List<SourceFilter> sourceFilters = List.of(
                new SourceFilter("All tasks", null),
                new SourceFilter("Canvas", Task.Source.CANVAS),
                new SourceFilter("Gmail", Task.Source.GMAIL),
                new SourceFilter("Personal", Task.Source.MANUAL));
        for (SourceFilter sourceFilter : sourceFilters) {
            long count = tasks.stream()
                    .filter(task -> sourceFilter.source() == null || task.source() == sourceFilter.source())
                    .count();
            JToggleButton choice = navigationButton(
                    sourceFilter.label() + "  " + count,
                    selectedSource == sourceFilter.source());
            choice.addChangeListener(event -> updateNavigationButton(choice));
            choice.addActionListener(event -> {
                selectedSource = sourceFilter.source();
                applyFilter();
                updateSummary();
            });
            group.add(choice);
            sourceChoices.add(choice);
        }
        sourceChoices.revalidate();
        sourceChoices.repaint();
    }

    private void addTask() {
        TaskDraft draft = showTaskDialog(null);
        if (draft == null) return;
        tasks.add(new Task(UUID.randomUUID(), draft.title(), draft.details(), draft.priority(), draft.dueDate(),
                false, Task.Source.MANUAL, ""));
        persistAndRefresh();
    }

    private void syncCanvas() {
        if (canvasClient == null) {
            JTextField urlField = new JTextField("https://", 28);
            JPasswordField tokenField = new JPasswordField(28);
            JPanel form = new JPanel(new GridBagLayout());
            form.setBackground(ADD_PANEL);
            form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(6, 6, 6, 6);
            constraints.anchor = GridBagConstraints.WEST;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            addFormRow(form, constraints, 0, "Canvas site", urlField);
            addFormRow(form, constraints, 1, "Personal token", tokenField);
            JLabel help = new JLabel("Use your own Canvas account and generate a personal access token in your profile settings.");
            help.setForeground(MUTED);
            help.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            constraints.gridx = 1;
            constraints.gridy = 2;
            form.add(help, constraints);
            int answer = JOptionPane.showConfirmDialog(null, form, "Connect your Canvas account",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) return;
            try {
                canvasClient = new CanvasClient(urlField.getText(), new String(tokenField.getPassword()));
                updateConnectionBadges();
            } catch (IllegalArgumentException exception) {
                showError("Canvas connection", exception);
                return;
            }
        }
        CanvasClient client = canvasClient;
        runSync("Canvas", client::fetchAssignments);
    }

    private void syncGmail() {
        GmailClient client = gmailClient;
        boolean needsAuthorization = client == null;
        String clientId = null;
        if (needsAuthorization) {
            JTextField clientIdField = new JTextField(34);
            JPanel form = new JPanel(new GridBagLayout());
            form.setBackground(ADD_PANEL);
            form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(6, 6, 6, 6);
            constraints.anchor = GridBagConstraints.WEST;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            addFormRow(form, constraints, 0, "Google OAuth client ID", clientIdField);
            JLabel help = new JLabel("Sign in with your personal Google account. Google still requires a Desktop OAuth client ID from Google Cloud.");
            help.setForeground(MUTED);
            help.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            constraints.gridx = 1;
            constraints.gridy = 1;
            form.add(help, constraints);
            int answer = JOptionPane.showConfirmDialog(null, form, "Connect your Gmail account",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) return;
            clientId = clientIdField.getText().trim();
            client = new GmailClient();
        }
        GmailClient selectedClient = client;
        String selectedClientId = clientId;
        boolean connect = needsAuthorization;
        runSync("Gmail", () -> {
            if (connect) selectedClient.connect(selectedClientId);
            return selectedClient.fetchTasks();
        }, () -> {
            if (connect) {
                gmailClient = selectedClient;
                updateConnectionBadges();
            }
        });
    }

    private void runSync(String service, java.util.concurrent.Callable<List<Task>> fetch) {
        runSync(service, fetch, () -> { });
    }

    private void runSync(String service, java.util.concurrent.Callable<List<Task>> fetch, Runnable onSuccess) {
        if (syncInProgress) {
            JOptionPane.showMessageDialog(null, "Wait for the current sync to finish before starting another.",
                    "Sync in progress", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        syncInProgress = true;
        new javax.swing.SwingWorker<List<Task>, Void>() {
            @Override
            protected List<Task> doInBackground() throws Exception {
                return fetch.call();
            }

            @Override
            protected void done() {
                try {
                    List<Task> imported = get();
                    onSuccess.run();
                    addImportedTasks(service, imported);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError(service + " sync", exception);
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    if (cause != null && cause.getMessage() != null
                            && cause.getMessage().contains("HTTP 401")) {
                        if (service.equals("Gmail")) gmailClient = null;
                        if (service.equals("Canvas")) canvasClient = null;
                    }
                    showError(service + " sync", cause == null ? exception : cause);
                } finally {
                    syncInProgress = false;
                }
            }
        }.execute();
    }

    private void addImportedTasks(String service, List<Task> imported) {
        List<Task> added = new ArrayList<>();
        Set<String> existingSources = new HashSet<>();
        for (Task task : tasks) existingSources.add(sourceKey(task));
        for (Task task : imported) {
            if (existingSources.add(sourceKey(task))) {
                added.add(new Task(UUID.randomUUID(), task.title(), task.details(), task.priority(),
                        task.dueDate(), false, task.source(), task.sourceId()));
            }
        }
        if (added.isEmpty()) {
            JOptionPane.showMessageDialog(null, "No new tasks were found in " + service + ".",
                    service + " sync", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        tasks.addAll(added);
        try {
            store.save(tasks);
            refresh();
            JOptionPane.showMessageDialog(null, "Added " + added.size() + " task"
                    + (added.size() == 1 ? "" : "s") + " from " + service + ".",
                    service + " sync", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException exception) {
            tasks.removeAll(added);
            showError(service + " save", exception);
        }
    }

    private String sourceKey(Task task) {
        return task.source().name() + "\t" + task.sourceId();
    }

    private void showError(String title, Throwable exception) {
        String message = exception.getMessage();
        JOptionPane.showMessageDialog(null,
                title + " failed: " + (message == null ? exception.getClass().getSimpleName() : message),
                title, JOptionPane.ERROR_MESSAGE);
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
        JTextField titleField = new JTextField(task == null ? "" : task.title(), 20);
        JTextField dueDateField = new JTextField(task == null || task.dueDate() == null
                ? "" : task.dueDate().toString(), 10);
        JComboBox<Task.Priority> priorityBox = new JComboBox<>(Task.Priority.values());
        if (task != null) priorityBox.setSelectedItem(task.priority());
        JTextArea detailsArea = new JTextArea(task == null ? "" : task.details(), 3, 22);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);
        detailsArea.setBackground(PANEL);
        detailsArea.setForeground(INK);
        detailsArea.setCaretColor(INK);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(ADD_PANEL);
        form.setBorder(BorderFactory.createEmptyBorder(5, 7, 5, 7));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        addFormRow(form, constraints, 0, "Task", titleField);
        addFormRow(form, constraints, 1, "Due date", dueDateField);
        addFormRow(form, constraints, 2, "Priority", priorityBox);
        JScrollPane detailsScroll = new JScrollPane(detailsArea);
        detailsScroll.setPreferredSize(new Dimension(275, 72));
        addFormRow(form, constraints, 3, "Details", detailsScroll);

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
                .thenComparingInt(task -> task.priority().ordinal())
                .thenComparing(task -> task.dueDate() == null ? LocalDate.MAX : task.dueDate()));
        tableModel.setTasks(tasks);
        applyFilter();
        updateCalendar();
        rebuildSourceChoices();
        updateSummary();
    }

    private void updateSummary() {
        List<Task> sourceTasks = tasks.stream()
                .filter(task -> selectedSource == null || task.source() == selectedSource)
                .toList();
        long openCount = sourceTasks.stream().filter(task -> !task.completed()).count();
        long doneCount = sourceTasks.size() - openCount;
        String sourceName = selectedSource == null ? "all tasks" : switch (selectedSource) {
            case MANUAL -> "personal tasks";
            case CANVAS -> "Canvas tasks";
            case GMAIL -> "Gmail tasks";
        };
        summaryLabel.setText(sourceTasks.size() + " " + sourceName + "    ·    "
                + openCount + " open    ·    " + doneCount + " completed");
    }

    private void applyFilter() {
        String query = searchField.getText().trim().toLowerCase();
        sorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends TaskTableModel, ? extends Integer> entry) {
                Task task = tableModel.getTask(entry.getIdentifier());
                boolean statusMatches = selectedStatus.equals("All")
                        || selectedStatus.equals("Done") == task.completed();
                boolean sourceMatches = selectedSource == null || task.source() == selectedSource;
                boolean priorityMatches = selectedPriority == null || task.priority() == selectedPriority;
                boolean queryMatches = query.isEmpty()
                        || task.title().toLowerCase().contains(query)
                        || task.details().toLowerCase().contains(query);
                return sourceMatches && statusMatches && priorityMatches && queryMatches;
            }
        });
        emptyLabel.setVisible(table.getRowCount() == 0);
    }

    private JLabel statusPill(String name, boolean connected) {
        JLabel label = new JLabel("  " + name + "  ");
        label.setOpaque(true);
        label.setBackground(connected ? ACCENT_SOFT : NEUTRAL);
        label.setForeground(connected ? GREEN : MUTED);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(connected ? CONNECTED_BORDER : BORDER),
                BorderFactory.createEmptyBorder(5, 9, 5, 9)));
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        return label;
    }

    private void updateConnectionBadges() {
        applyStatusBadge(gmailStatusLabel, gmailClient != null);
        applyStatusBadge(canvasStatusLabel, canvasClient != null);
    }

    private void applyStatusBadge(JLabel label, boolean connected) {
        label.setBackground(connected ? ACCENT_SOFT : NEUTRAL);
        label.setForeground(connected ? GREEN : MUTED);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(connected ? CONNECTED_BORDER : BORDER),
                BorderFactory.createEmptyBorder(5, 9, 5, 9)));
    }

    private void toggleTheme() {
        darkMode = !darkMode;
        setPalette(darkMode);
        applyLookAndFeelTheme();
        rebuildWindowContents();
        try {
            PREFERENCES.putBoolean("darkMode", darkMode);
        } catch (IllegalStateException | SecurityException exception) {
            JOptionPane.showMessageDialog(frame,
                    "The theme changed, but the preference could not be saved: " + exception.getMessage(),
                    "Theme preference", JOptionPane.WARNING_MESSAGE);
        }
    }

    private static void setPalette(boolean dark) {
        if (dark) {
            INK = Color.WHITE;
            MUTED = Color.WHITE;
            PAPER = new Color(37, 42, 39);
            GREEN = Color.WHITE;
            ACCENT_SOFT = new Color(54, 62, 57);
            PANEL = new Color(44, 50, 46);
            BORDER = new Color(76, 85, 79);
            ADD_PANEL = new Color(42, 48, 44);
            DANGER = Color.WHITE;
            BUTTON_SOFT = new Color(47, 54, 49);
            DANGER_SOFT = new Color(49, 55, 51);
            TABLE_HEADER = new Color(47, 54, 49);
            TABLE_SELECT = new Color(58, 66, 60);
            COMPLETED_BG = new Color(49, 56, 51);
            TOGGLE_SELECTED = new Color(54, 62, 57);
            CONNECTED_BORDER = new Color(105, 117, 108);
            NEUTRAL = new Color(47, 54, 49);
            HIGH_PRIORITY = Color.WHITE;
            MEDIUM_PRIORITY = Color.WHITE;
            PRIMARY_ACTION = new Color(54, 62, 57);
            PRIMARY_ACTION_TEXT = Color.WHITE;
        } else {
            INK = new Color(58, 58, 52);
            MUTED = new Color(125, 121, 111);
            PAPER = new Color(247, 244, 235);
            GREEN = new Color(83, 111, 91);
            ACCENT_SOFT = new Color(230, 235, 224);
            PANEL = new Color(255, 253, 247);
            BORDER = new Color(226, 220, 207);
            ADD_PANEL = new Color(244, 240, 229);
            DANGER = new Color(144, 91, 84);
            BUTTON_SOFT = new Color(239, 235, 224);
            DANGER_SOFT = new Color(245, 235, 229);
            TABLE_HEADER = new Color(245, 241, 231);
            TABLE_SELECT = new Color(230, 235, 225);
            COMPLETED_BG = new Color(242, 239, 230);
            TOGGLE_SELECTED = new Color(228, 235, 226);
            CONNECTED_BORDER = new Color(199, 213, 196);
            NEUTRAL = new Color(241, 238, 229);
            HIGH_PRIORITY = new Color(153, 89, 79);
            MEDIUM_PRIORITY = new Color(143, 116, 69);
            PRIMARY_ACTION = new Color(83, 111, 91);
            PRIMARY_ACTION_TEXT = Color.WHITE;
        }
    }

    private static void applyLookAndFeelTheme() {
        UIManager.put("Panel.background", PAPER);
        UIManager.put("OptionPane.background", PAPER);
        UIManager.put("OptionPane.messageForeground", INK);
        UIManager.put("Label.foreground", INK);
        UIManager.put("Button.background", BUTTON_SOFT);
        UIManager.put("Button.foreground", INK);
        UIManager.put("ToggleButton.background", PAPER);
        UIManager.put("ToggleButton.foreground", INK);
        UIManager.put("TextField.background", PANEL);
        UIManager.put("TextField.foreground", INK);
        UIManager.put("TextField.caretForeground", INK);
        UIManager.put("PasswordField.background", PANEL);
        UIManager.put("PasswordField.foreground", INK);
        UIManager.put("PasswordField.caretForeground", INK);
        UIManager.put("TextArea.background", PANEL);
        UIManager.put("TextArea.foreground", INK);
        UIManager.put("TextArea.caretForeground", INK);
        UIManager.put("ComboBox.background", PANEL);
        UIManager.put("ComboBox.foreground", INK);
        UIManager.put("TabbedPane.background", PAPER);
        UIManager.put("TabbedPane.foreground", INK);
        UIManager.put("TabbedPane.selected", PANEL);
        UIManager.put("Table.background", PANEL);
        UIManager.put("Table.foreground", INK);
        UIManager.put("TableHeader.background", TABLE_HEADER);
        UIManager.put("TableHeader.foreground", MUTED);
        UIManager.put("ScrollPane.background", PANEL);
        UIManager.put("Viewport.background", PANEL);
        UIManager.put("ComboBox.selectionBackground", ACCENT_SOFT);
        UIManager.put("ComboBox.selectionForeground", INK);
        UIManager.put("ComboBox.buttonBackground", BUTTON_SOFT);
        UIManager.put("ComboBox.buttonShadow", BORDER);
        UIManager.put("ComboBox.buttonDarkShadow", BORDER);
        UIManager.put("ComboBox.buttonHighlight", BUTTON_SOFT);
    }

    private JButton button(String text, Color background, Color foreground) {
        JButton button = new SoftButton(text, background);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        button.setForeground(foreground);
        button.setBackground(background);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 13, 8, 13));
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        return button;
    }

    private JToggleButton navigationButton(String text, boolean selected) {
        JToggleButton button = new StyledToggleButton(text, selected);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        button.setFocusPainted(false);
        button.setOpaque(true);
        updateNavigationButton(button);
        return button;
    }

    private void updateNavigationButton(JToggleButton button) {
        boolean selected = button.isSelected();
        button.setBackground(selected ? TOGGLE_SELECTED : PANEL);
        button.setForeground(selected ? GREEN : MUTED);
        button.setBorder(BorderFactory.createCompoundBorder(
                new RoundedLineBorder(selected ? CONNECTED_BORDER : BORDER, 16),
                BorderFactory.createEmptyBorder(8, 20, 8, 20)));
    }

    private void styleToggle(JToggleButton toggle) {
        toggle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        toggle.setFocusPainted(false);
        toggle.setBorder(BorderFactory.createCompoundBorder(
                new RoundedLineBorder(BORDER, 15),
                BorderFactory.createEmptyBorder(8, 13, 8, 13)));
        toggle.addChangeListener(event -> {
            boolean selected = toggle.isSelected();
            toggle.setBackground(selected ? TOGGLE_SELECTED : (darkMode ? BUTTON_SOFT : PAPER));
            toggle.setForeground(darkMode ? INK : selected ? GREEN : MUTED);
        });
        toggle.setBackground(toggle.isSelected() ? TOGGLE_SELECTED : (darkMode ? BUTTON_SOFT : PAPER));
        toggle.setForeground(darkMode ? INK : toggle.isSelected() ? GREEN : MUTED);
        toggle.setOpaque(false);
        toggle.setContentAreaFilled(false);
    }

    private record TaskDraft(String title, String details, Task.Priority priority, LocalDate dueDate) { }
    private record SourceFilter(String label, Task.Source source) { }

    private static final class SurfacePanel extends JPanel {
        private final boolean shadow;

        private SurfacePanel(java.awt.LayoutManager layout, Color background, boolean shadow) {
            super(layout);
            this.shadow = shadow;
            setBackground(background);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getWidth() - 1;
            int height = getHeight() - 1;
            if (shadow) {
                copy.setColor(new Color(25, 35, 28, 15));
                copy.fillRoundRect(0, 3, width, height, 18, 18);
            }
            copy.setColor(getBackground());
            copy.fillRoundRect(0, 0, width, Math.max(0, height - 3), 18, 18);
            copy.dispose();
        }

    }

    private static final class RoundedLineBorder extends AbstractBorder {
        private final Color color;
        private final int radius;

        private RoundedLineBorder(Color color, int radius) {
            this.color = color;
            this.radius = radius;
        }

        @Override
        public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setColor(color);
            copy.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            copy.dispose();
        }

        @Override
        public Insets getBorderInsets(Component component) {
            return new Insets(1, 1, 1, 1);
        }
    }

    private static final class SoftButton extends JButton {
        private SoftButton(String text, Color background) {
            super(text);
            setBackground(background);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill = getModel().isRollover() ? getBackground().brighter() : getBackground();
            copy.setColor(fill);
            copy.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            copy.setColor(BORDER);
            copy.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            copy.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class StyledToggleButton extends JToggleButton {
        private StyledToggleButton(String text, boolean selected) {
            super(text, selected);
            setUI(new javax.swing.plaf.basic.BasicButtonUI());
            setContentAreaFilled(false);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setColor(getBackground());
            copy.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            copy.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class RoundedLabel extends JLabel {
        private RoundedLabel(String text) {
            super(text);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setColor(getBackground());
            copy.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            copy.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class TaskTableModel extends AbstractTableModel {
        private final String[] columns = {"TASK", "DUE DATE", "PRIORITY", "STATUS", "SOURCE"};
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
                case 4 -> Task.Source.class;
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
                case 4 -> task.source();
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
            setBackground(selected ? table.getSelectionBackground() : PANEL);
            setOpaque(true);
            setForeground(value == null && !selected ? MUTED : INK);
            return this;
        }
    }

    private static final class ThemedTableRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            setBackground(selected ? table.getSelectionBackground() : PANEL);
            setForeground(INK);
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
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
            setBackground(selected ? table.getSelectionBackground() : PANEL);
            setOpaque(true);
            if (!selected && value instanceof Task.Priority priority) {
                setForeground(switch (priority) {
                    case HIGH -> HIGH_PRIORITY;
                    case MEDIUM -> MEDIUM_PRIORITY;
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
            setBackground(selected ? table.getSelectionBackground() : PANEL);
            setOpaque(true);
            if (selected) setForeground(INK);
            else setForeground(completed ? MUTED : GREEN);
            return this;
        }
    }
}