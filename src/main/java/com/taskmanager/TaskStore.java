package com.taskmanager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public final class TaskStore {
    private final Path file;

    public TaskStore(Path file) {
        this.file = file;
    }

    public List<Task> load() throws IOException {
        if (Files.notExists(file)) {
            return new ArrayList<>();
        }

        List<Task> tasks = new ArrayList<>();
        List<String> rows = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int index = 0; index < rows.size(); index++) {
            String[] fields = rows.get(index).split("\\t", -1);
            try {
                if (fields.length != 6 && fields.length != 8) {
                    throw new IllegalArgumentException("Expected six or eight fields");
                }
                String title = decode(fields[1]);
                String details = decode(fields[2]);
                LocalDate dueDate = fields[4].isBlank() ? null : LocalDate.parse(fields[4]);
                Task.Source source = fields.length == 8
                        ? Task.Source.valueOf(fields[6]) : Task.Source.MANUAL;
                String sourceId = fields.length == 8 ? decode(fields[7]) : "";
                tasks.add(new Task(
                        UUID.fromString(fields[0]),
                        title,
                        details,
                        Task.Priority.valueOf(fields[3]),
                        dueDate,
                        Boolean.parseBoolean(fields[5]),
                        source,
                        sourceId));
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid task data on line " + (index + 1), exception);
            }
        }
        return tasks;
    }

    public void save(List<Task> tasks) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporaryFile = file.resolveSibling(file.getFileName() + ".tmp");
        List<String> rows = tasks.stream().map(task -> String.join("\t",
                task.id().toString(),
                encode(task.title()),
                encode(task.details()),
                task.priority().name(),
                task.dueDate() == null ? "" : task.dueDate().toString(),
                Boolean.toString(task.completed()),
                task.source().name(),
                encode(task.sourceId()))).toList();
        Files.write(temporaryFile, rows, StandardCharsets.UTF_8);
        try {
            Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}