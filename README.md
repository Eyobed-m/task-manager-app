# Task Manager

A Java desktop app for organizing personal tasks and syncing Canvas assignments and
task-related Gmail messages into one list.

## Features

- Create, edit, complete, and delete tasks
- Set a high, medium, or low priority and an optional due date
- Search task names and details; filter by completion status or priority
- Sort tasks by priority (high first), then due date; click a table heading to change the sort
- Save tasks locally between launches
- Import active Canvas course assignments and Gmail messages that mention assignments,
  announcements, deadlines, quizzes, exams, homework, projects, or related task keywords
- Identify imported tasks by their source and avoid importing the same source item twice

## Requirements

- Java 17 or newer

## Run

From the project root, compile and launch with:

```powershell
javac -d out src/main/java/com/taskmanager/*.java
java -cp out com.taskmanager.TaskManagerApp
```

Task data is stored in `.task-manager/tasks.tsv` under your user home directory. Existing
task files are migrated automatically when first saved.

## Connect Canvas

Choose **Sync Canvas**, enter your Canvas site address (for example,
`https://school.instructure.com`) and a Canvas access token. Create a token from your
Canvas account's approved access-token settings. The app reads active-course assignments,
their due dates, and submission status. Only use a token for a Canvas site you trust.

## Connect Gmail

Choose **Sync Gmail** and enter a Google OAuth client ID configured as a **Desktop app**.
In Google Cloud, enable the Gmail API, configure the OAuth consent screen, and add your
Google account as a test user if the consent screen is still in testing. The app opens
Google sign-in in your browser and requests read-only access. Gmail messages are searched
for assignment, announcement, due-date, and other task-related keywords; due dates in
messages are added when the app can recognize their format.

Canvas tokens and Gmail access tokens remain in memory only and are not written to disk.
You will need to connect again after restarting the app. Imported task titles, due dates,
source IDs, and message excerpts are saved with your local tasks. This version supports
Canvas and Gmail; it does not yet connect to other services.
