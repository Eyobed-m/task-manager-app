package com.taskmanager;

import java.time.LocalDate;
import java.util.UUID;

public record Task(
        UUID id,
        String title,
        String details,
        Priority priority,
        LocalDate dueDate,
        boolean completed) {

    public enum Priority {
        HIGH,
        MEDIUM,
        LOW
    }

    public Task withChanges(String title, String details, Priority priority, LocalDate dueDate) {
        return new Task(id, title, details, priority, dueDate, completed);
    }

    public Task withCompleted(boolean completed) {
        return new Task(id, title, details, priority, dueDate, completed);
    }
}