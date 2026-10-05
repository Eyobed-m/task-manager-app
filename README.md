# Task Manager

A Java desktop app for organizing tasks by due date and priority.

## Features

- Create, edit, complete, and delete tasks
- Set a high, medium, or low priority and an optional due date
- Search task names and details; filter by completion status or priority
- Sort tasks by clicking a table heading
- Save tasks locally between launches

## Requirements

- Java 17 or newer

## Run

From the project root, compile and launch with:

```powershell
javac -d out src/main/java/com/taskmanager/*.java
java -cp out com.taskmanager.TaskManagerApp
```

Task data is stored in `.task-manager/tasks.tsv` under your user home directory.
