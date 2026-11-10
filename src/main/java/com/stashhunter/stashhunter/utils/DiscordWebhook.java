package com.stashhunter.stashhunter.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stashhunter.stashhunter.StashHunter;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DiscordWebhook {
    private static final Gson GSON = new Gson();
    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_QUEUED = 50;
    private static final int MAX_ATTEMPTS = 4;
    private static final long MAX_RETRY_WAIT_MS = 30_000;
    private static final String EVERYONE = "@everyone";

    // Webhooks are sent off the game thread so a slow or unreachable Discord never freezes the client.
    // The queue is bounded: while Discord is down, new messages are dropped instead of piling up.
    private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(MAX_QUEUED),
        r -> {
            Thread thread = new Thread(r, "StashHunter-DiscordWebhook");
            thread.setDaemon(true);
            return thread;
        },
        (r, executor) -> {
            // After shutdown() every send is rejected; that isn't a full queue.
            if (executor.isShutdown()) StashHunter.LOG.debug("Discord webhook dropped, client is shutting down");
            else StashHunter.LOG.warn("Discord webhook queue is full, dropping message");
        });
    private static final long SHUTDOWN_WAIT_MS = 3000;

    public static void sendMessage(String content, DiscordEmbed embed) {
        sendMessage(Config.discordWebhookUrl, content, embed);
    }

    public static void sendMessage(String webhookUrl, String content, DiscordEmbed embed) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }

        // @everyone only pings when the user opted in; otherwise drop it from the text as well.
        JsonArray allowedParse = new JsonArray();
        if (Config.mentionEveryone) {
            allowedParse.add("everyone");
        } else if (content != null) {
            content = content.replace(EVERYONE, "").trim();
        }
        JsonObject allowedMentions = new JsonObject();
        allowedMentions.add("parse", allowedParse);

        JsonObject json = new JsonObject();
        json.addProperty("content", content);
        json.add("allowed_mentions", allowedMentions);
        if (embed != null) {
            JsonArray embeds = new JsonArray();
            embeds.add(GSON.toJsonTree(embed));
            json.add("embeds", embeds);
        }
        byte[] payload = GSON.toJson(json).getBytes(StandardCharsets.UTF_8);

        EXECUTOR.execute(() -> post(webhookUrl, payload));
    }

    // Called from the JVM shutdown hook: gives queued messages (e.g. the disconnect notice) a short,
    // bounded window to go out, then interrupts retry waits so exit isn't held up by a dead Discord.
    public static void shutdown() {
        EXECUTOR.shutdown();
        try {
            if (!EXECUTOR.awaitTermination(SHUTDOWN_WAIT_MS, TimeUnit.MILLISECONDS)) {
                int dropped = EXECUTOR.shutdownNow().size();
                if (dropped > 0) StashHunter.LOG.warn("Discord webhook: {} queued message(s) not sent before exit", dropped);
            }
        } catch (InterruptedException e) {
            EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // Retries rate limits (429, honouring retry_after) and server/network errors with backoff.
    // Other 4xx answers (bad URL, deleted webhook, invalid payload) won't change on retry.
    private static void post(String webhookUrl, byte[] payload) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            long waitMs;
            HttpURLConnection connection = null;
            try {
                URL url = new URI(webhookUrl).toURL();
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("User-Agent", "StashHunter");
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setDoOutput(true);

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(payload);
                }

                int status = connection.getResponseCode();
                if (status >= 200 && status < 300) return;
                if (status == 429) {
                    waitMs = retryAfterMs(connection);
                } else if (status >= 500) {
                    waitMs = backoffMs(attempt);
                } else {
                    StashHunter.LOG.warn("Discord webhook rejected the message (HTTP {}), not retrying", status);
                    return;
                }
                StashHunter.LOG.warn("Discord webhook answered HTTP {}, retrying in {} ms", status, waitMs);
            } catch (URISyntaxException | IllegalArgumentException e) {
                StashHunter.LOG.warn("Invalid Discord webhook URL: {}", e.getMessage());
                return;
            } catch (IOException e) {
                waitMs = backoffMs(attempt);
                StashHunter.LOG.warn("Discord webhook request failed ({}), retrying in {} ms", e.getMessage(), waitMs);
            } finally {
                if (connection != null) connection.disconnect();
            }

            if (attempt == MAX_ATTEMPTS) break;
            try {
                Thread.sleep(Math.min(waitMs, MAX_RETRY_WAIT_MS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        StashHunter.LOG.warn("Discord webhook message dropped after {} attempts", MAX_ATTEMPTS);
    }

    private static long backoffMs(int attempt) {
        return 1000L << (attempt - 1);
    }

    // Discord sends the wait in seconds, both as Retry-After header and as retry_after in the body.
    private static long retryAfterMs(HttpURLConnection connection) {
        try {
            String header = connection.getHeaderField("Retry-After");
            if (header != null) return (long) (Double.parseDouble(header) * 1000);
        } catch (NumberFormatException ignored) {} // fall through to the body
        try (InputStream error = connection.getErrorStream()) {
            if (error != null) {
                JsonObject body = JsonParser.parseReader(new InputStreamReader(error, StandardCharsets.UTF_8)).getAsJsonObject();
                if (body.has("retry_after")) return (long) (body.get("retry_after").getAsDouble() * 1000);
            }
        } catch (IOException | RuntimeException ignored) {} // fall back to the default wait
        return backoffMs(2);
    }
}
