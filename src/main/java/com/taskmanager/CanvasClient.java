package com.taskmanager;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class CanvasClient {
    private final URI baseUri;
    private final String token;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    CanvasClient(String baseUrl, String token) {
        URI parsed = URI.create(baseUrl.trim());
        if (!"https".equalsIgnoreCase(parsed.getScheme()) || parsed.getHost() == null
                || parsed.getUserInfo() != null || parsed.getQuery() != null || parsed.getFragment() != null) {
            throw new IllegalArgumentException("Enter a Canvas site address beginning with https://.");
        }
        String normalized = parsed.toString().replaceAll("/+$", "");
        this.baseUri = URI.create(normalized);
        this.token = token.trim();
        if (this.token.isEmpty()) throw new IllegalArgumentException("A Canvas access token is required.");
    }

    List<Task> fetchAssignments() throws IOException, InterruptedException {
        List<Task> tasks = new ArrayList<>();
        for (Map<String, Object> course : getPages("/api/v1/users/self/courses"
                + "?enrollment_state=active&include%5B%5D=term&per_page=100")) {
            String courseId = stringValue(course.get("id"));
            String courseName = stringValue(course.get("name"));
            if (courseId.isBlank()) continue;
            String coursePath = "/api/v1/courses/" + encodePath(courseId)
                    + "/assignments?include%5B%5D=submission&per_page=100";
            for (Map<String, Object> assignment : getPages(coursePath)) {
                String assignmentId = stringValue(assignment.get("id"));
                String name = stringValue(assignment.get("name")).trim();
                if (assignmentId.isBlank() || name.isBlank()) continue;
                String dueAt = stringValue(assignment.get("due_at"));
                LocalDate dueDate = dueAt.isBlank() ? null : OffsetDateTime.parse(dueAt).toLocalDate();
                String description = stripHtml(stringValue(assignment.get("description")));
                String url = baseUri.resolve("/courses/" + encodePath(courseId)
                        + "/assignments/" + encodePath(assignmentId)).toString();
                String details = "Canvas · " + courseName + (description.isBlank() ? "" : "\n\n" + description)
                        + "\n\n" + url;
                Task.Priority priority = dueDate != null && dueDate.isBefore(LocalDate.now())
                        ? Task.Priority.HIGH : Task.Priority.MEDIUM;
                String workflowState = stringValue(mapValue(assignment.get("submission")).get("workflow_state"));
                boolean completed = List.of("submitted", "graded", "pending_review", "complete")
                        .contains(workflowState);
                tasks.add(new Task(null, name, details, priority, dueDate, completed,
                        Task.Source.CANVAS, courseId + ":" + assignmentId));
            }
        }
        return tasks;
    }

    private List<Map<String, Object>> getPages(String path) throws IOException, InterruptedException {
        URI next = baseUri.resolve(path);
        List<Map<String, Object>> results = new ArrayList<>();
        while (next != null) {
            if (!sameOrigin(baseUri, next) || !next.getPath().startsWith("/api/v1/")) {
                throw new IOException("Canvas returned a pagination link outside its API.");
            }
            HttpRequest request = HttpRequest.newBuilder(next)
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Canvas returned HTTP " + response.statusCode() + ": "
                        + response.body().substring(0, Math.min(response.body().length(), 300)));
            }
            Object json = Json.parse(response.body());
            if (!(json instanceof List<?> rows)) throw new IOException("Canvas returned an unexpected response.");
            for (Object row : rows) {
                if (!(row instanceof Map<?, ?> map)) {
                    throw new IOException("Canvas returned an unexpected collection item.");
                }
                results.add(stringMap(map));
            }
            next = nextLink(response.headers().allValues("Link"));
        }
        return results;
    }

    private URI nextLink(List<String> headers) {
        for (String header : headers) {
            for (String link : header.split(",")) {
                if (link.contains("rel=\"next\"")) {
                    int start = link.indexOf('<');
                    int end = link.indexOf('>');
                    if (start >= 0 && end > start) return baseUri.resolve(link.substring(start + 1, end));
                }
            }
        }
        return null;
    }

    private static boolean sameOrigin(URI first, URI second) {
        return first.getScheme().equalsIgnoreCase(second.getScheme())
                && first.getHost().equalsIgnoreCase(second.getHost())
                && effectivePort(first) == effectivePort(second);
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() == -1 ? 443 : uri.getPort();
    }

    private static Map<String, Object> stringMap(Map<?, ?> map) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (key instanceof String stringKey) result.put(stringKey, value);
        });
        return result;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        map.forEach((key, item) -> {
            if (key instanceof String stringKey) result.put(stringKey, item);
        });
        return result;
    }

    private static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String stripHtml(String value) {
        return value.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replaceAll("\\s+", " ").trim();
    }
}
