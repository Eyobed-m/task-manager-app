package com.taskmanager;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GmailClient {
    private static final String SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
    private static final String GMAIL_API = "https://gmail.googleapis.com/gmail/v1/users/me";
    private static final Pattern TASK_CUE = Pattern.compile(
            "\\b(assignment|assignments|announcement|announcements|due date|due by|due on|"
                    + "deadline|submit by|submission|quiz|exam|homework|coursework|project|lab)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DUE_DATE = Pattern.compile(
            "(?i)(?:due(?:\\s+date|\\s+by|\\s+on)?|deadline|submit(?:ted)?\\s+by)"
                    + "(?:\\s+(?:is|on|by|Mon(?:day)?|Tue(?:sday)?|Wed(?:nesday)?|"
                    + "Thu(?:rsday)?|Fri(?:day)?|Sat(?:urday)?|Sun(?:day)?)[, ]*)?"
                    + "[^\\p{Alnum}]{0,24}"
                    + "(?:(?:Mon(?:day)?|Tue(?:sday)?|Wed(?:nesday)?|Thu(?:rsday)?|"
                    + "Fri(?:day)?|Sat(?:urday)?|Sun(?:day)?)[, ]+)?"
                    + "(\\d{4}-\\d{1,2}-\\d{1,2}|"
                    + "(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|"
                    + "Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|"
                    + "Dec(?:ember)?)\\.?\\s+\\d{1,2}(?:,?\\s+\\d{4})?)");
    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("MMM d, uuuu")
                    .toFormatter(Locale.US),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("MMMM d, uuuu")
                    .toFormatter(Locale.US)
    };

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private String accessToken;

    void connect(String clientId) throws Exception {
        if (clientId == null || !clientId.trim().endsWith(".apps.googleusercontent.com")) {
            throw new IllegalArgumentException("Enter a Google OAuth client ID for a Desktop app.");
        }
        String verifier = randomUrlToken(64);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        String state = randomUrlToken(32);
        try (ServerSocket callback = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            callback.setSoTimeout((int) Duration.ofMinutes(3).toMillis());
            URI redirectUri = URI.create("http://127.0.0.1:" + callback.getLocalPort());
            URI authorizationUri = URI.create("https://accounts.google.com/o/oauth2/v2/auth?"
                    + form("client_id", clientId.trim())
                    + "&" + form("redirect_uri", redirectUri.toString())
                    + "&" + form("response_type", "code")
                    + "&" + form("scope", SCOPE)
                    + "&" + form("state", state)
                    + "&" + form("code_challenge", challenge)
                    + "&" + form("code_challenge_method", "S256")
                    + "&prompt=select_account"
                    + "&access_type=online");
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                throw new IOException("This computer cannot open a browser for Google sign-in.");
            }
            Desktop.getDesktop().browse(authorizationUri);
            String code = receiveAuthorizationCode(callback, state);
            exchangeCode(clientId.trim(), redirectUri, code, verifier);
        }
    }

    List<Task> fetchTasks() throws IOException, InterruptedException {
        if (accessToken == null) throw new IllegalStateException("Connect to Gmail before syncing.");
        List<Task> tasks = new ArrayList<>();
        String pageToken = "";
        for (int page = 0; page < 5; page++) {
            String query = "newer_than:180d {assignment assignments announcement announcements "
                    + "\"due date\" deadline \"due by\" \"submit by\" quiz exam homework project}";
            URI uri = URI.create(GMAIL_API + "/messages?maxResults=100&q=" + encodeQuery(query)
                    + (pageToken.isBlank() ? "" : "&pageToken=" + encodeQuery(pageToken)));
            Map<String, Object> response = getObject(uri);
            Object messageList = response.get("messages");
            if (messageList != null && !(messageList instanceof List<?>)) {
                throw new IOException("Gmail returned an unexpected message list.");
            }
            for (Object entry : listValue(messageList)) {
                if (!(entry instanceof Map<?, ?> messageRef)) {
                    throw new IOException("Gmail returned an unexpected message entry.");
                }
                String id = stringValue(messageRef.get("id"));
                if (id.isBlank()) continue;
                Task task = readTask(id);
                if (task != null) tasks.add(task);
            }
            pageToken = stringValue(response.get("nextPageToken"));
            if (pageToken.isBlank()) break;
        }
        return tasks;
    }

    private Task readTask(String id) throws IOException, InterruptedException {
        Map<String, Object> message = getObject(URI.create(GMAIL_API + "/messages/"
                + encodeQuery(id) + "?format=full"));
        Map<String, Object> payload = mapValue(message.get("payload"));
        Map<String, String> headers = new java.util.HashMap<>();
        for (Object entry : listValue(payload.get("headers"))) {
            Map<String, Object> header = mapValue(entry);
            headers.put(stringValue(header.get("name")).toLowerCase(),
                    stringValue(header.get("value")));
        }
        String subject = headers.getOrDefault("subject", "(No subject)").trim();
        String body = readBody(payload);
        if (body.isBlank()) body = stringValue(message.get("snippet"));
        String content = subject + "\n" + body;
        if (!TASK_CUE.matcher(content).find()) return null;

        LocalDate dueDate = findDueDate(content);
        Task.Priority priority = content.matches("(?is).*\\b(urgent|high priority|asap|overdue)\\b.*")
                ? Task.Priority.HIGH : Task.Priority.MEDIUM;
        if (dueDate != null && !dueDate.isAfter(LocalDate.now().plusDays(3))) {
            priority = Task.Priority.HIGH;
        }
        String sender = headers.getOrDefault("from", "").trim();
        String excerpt = body.replaceAll("\\s+", " ").trim();
        if (excerpt.length() > 1200) excerpt = excerpt.substring(0, 1200) + "...";
        String details = "Gmail" + (sender.isBlank() ? "" : " · " + sender)
                + (excerpt.isBlank() ? "" : "\n\n" + excerpt);
        return new Task(null, subject, details, priority, dueDate, false, Task.Source.GMAIL, id);
    }

    private Map<String, Object> getObject(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Gmail returned HTTP " + response.statusCode() + ": "
                    + response.body().substring(0, Math.min(response.body().length(), 300)));
        }
        Object parsed = Json.parse(response.body());
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IOException("Gmail returned an unexpected response.");
        }
        return stringMap(map);
    }

    private void exchangeCode(String clientId, URI redirectUri, String code, String verifier)
            throws IOException, InterruptedException {
        String formBody = form("client_id", clientId)
                + "&" + form("code", code)
                + "&" + form("code_verifier", verifier)
                + "&" + form("redirect_uri", redirectUri.toString())
                + "&grant_type=authorization_code";
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> tokenResponse = mapValue(Json.parse(response.body()));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Google sign-in failed: "
                    + stringValue(tokenResponse.getOrDefault("error_description", tokenResponse.get("error"))));
        }
        accessToken = stringValue(tokenResponse.get("access_token"));
        if (accessToken.isBlank()) throw new IOException("Google sign-in did not return an access token.");
    }

    private static String receiveAuthorizationCode(ServerSocket callback, String expectedState)
            throws IOException {
        try (var socket = callback.accept()) {
            socket.setSoTimeout(10000);
            var input = new java.io.BufferedReader(new java.io.InputStreamReader(
                    socket.getInputStream(), StandardCharsets.US_ASCII));
            String requestLine = input.readLine();
            if (requestLine == null || !requestLine.startsWith("GET /?")) {
                respond(socket, "Sign-in callback was invalid.");
                throw new IOException("Google sign-in callback was invalid.");
            }
            String target = requestLine.split(" ", 3)[1];
            String query = target.substring(target.indexOf('?') + 1);
            Map<String, String> parameters = new java.util.HashMap<>();
            for (String pair : query.split("&")) {
                String[] parts = pair.split("=", 2);
                parameters.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
            }
            if (!expectedState.equals(parameters.get("state"))) {
                respond(socket, "Sign-in state check failed. You can close this tab.");
                throw new IOException("Google sign-in state check failed.");
            }
            String code = parameters.get("code");
            if (code == null || code.isBlank()) {
                respond(socket, "Google did not authorize the requested access. You can close this tab.");
                throw new IOException("Google sign-in did not return an authorization code.");
            }
            respond(socket, "Gmail connected. You can close this tab and return to Task Manager.");
            return code;
        }
    }

    private static void respond(java.net.Socket socket, String message) throws IOException {
        byte[] body = ("<!doctype html><meta charset=\"utf-8\"><title>Task Manager</title><p>"
                + message + "</p>").getBytes(StandardCharsets.UTF_8);
        socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().write(body);
        socket.getOutputStream().flush();
    }

    private static String readBody(Map<String, Object> payload) {
        String plain = "";
        String html = "";
        Object data = mapValue(payload.get("body")).get("data");
        if (data != null) {
            String decoded = decodeBody(stringValue(data));
            if ("text/html".equalsIgnoreCase(stringValue(payload.get("mimeType")))) html = decoded;
            else plain = decoded;
        }
        for (Object partValue : listValue(payload.get("parts"))) {
            Map<String, Object> part = mapValue(partValue);
            String mimeType = stringValue(part.get("mimeType"));
            String nested = readBody(part);
            if (mimeType.equalsIgnoreCase("text/plain") && !nested.isBlank()) plain += "\n" + nested;
            else if (mimeType.equalsIgnoreCase("text/html") && !nested.isBlank()) html += "\n" + nested;
        }
        if (!plain.isBlank()) return plain;
        return html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?s)<[^>]+>", " ").replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">").replaceAll("\\s+", " ").trim();
    }

    private static String decodeBody(String data) {
        try {
            return new String(Base64.getUrlDecoder().decode(data), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Gmail returned an invalid message body.", exception);
        }
    }

    private static LocalDate findDueDate(String text) {
        Matcher matcher = DUE_DATE.matcher(text);
        if (!matcher.find()) return null;
        String dateText = matcher.group(1).replaceAll("(?i)(Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\.",
                "$1");
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(dateText, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported date format.
            }
        }
        for (String pattern : List.of("MMM d", "MMMM d")) {
            try {
                MonthDay monthDay = MonthDay.parse(dateText, new DateTimeFormatterBuilder()
                        .parseCaseInsensitive().appendPattern(pattern).toFormatter(Locale.US));
                LocalDate date = monthDay.atYear(LocalDate.now().getYear());
                return date.isBefore(LocalDate.now()) ? date.plusYears(1) : date;
            } catch (DateTimeParseException ignored) {
                // Try the next supported date format.
            }
        }
        return null;
    }

    private static String randomUrlToken(int bytes) {
        byte[] random = new byte[bytes];
        new SecureRandom().nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private static String form(String key, String value) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static Map<String, Object> stringMap(Map<?, ?> map) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (key instanceof String stringKey) result.put(stringKey, value);
        });
        return result;
    }

    private static List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }
}
