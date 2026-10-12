package com.taskmanager;

import java.time.LocalDate;
import java.util.UUID;

public record Task(
        UUID id,
        String title,
        String details,
        Priority priority,
        LocalDate dueDate,
        boolean completed,
        Source source,
        String sourceId) {

    public enum Priority {
        HIGH,
        MEDIUM,
        LOW
    }

    public enum Source {
        MANUAL,
        CANVAS,
        GMAIL
    }

    public Task withChanges(String title, String details, Priority priority, LocalDate dueDate) {
        return new Task(id, title, details, priority, dueDate, completed, source, sourceId);
    }

    public Task withCompleted(boolean completed) {
        return new Task(id, title, details, priority, dueDate, completed, source, sourceId);
    }
}