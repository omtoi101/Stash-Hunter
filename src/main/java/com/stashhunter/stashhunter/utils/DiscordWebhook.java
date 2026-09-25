package com.stashhunter.stashhunter.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DiscordWebhook {
    private static final Gson GSON = new Gson();
    private static final int TIMEOUT_MS = 10_000;

    // Webhooks are sent off the game thread so a slow or unreachable Discord never freezes the client.
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "StashHunter-DiscordWebhook");
        thread.setDaemon(true);
        return thread;
    });

    public static void sendMessage(String content, DiscordEmbed embed) {
        sendMessage(Config.discordWebhookUrl, content, embed);
    }

    public static void sendMessage(String webhookUrl, String content, DiscordEmbed embed) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }

        JsonObject json = new JsonObject();
        json.addProperty("content", content);
        if (embed != null) {
            JsonArray embeds = new JsonArray();
            embeds.add(GSON.toJsonTree(embed));
            json.add("embeds", embeds);
        }
        byte[] payload = GSON.toJson(json).getBytes(StandardCharsets.UTF_8);

        EXECUTOR.execute(() -> post(webhookUrl, payload));
    }

    private static void post(String webhookUrl, byte[] payload) {
        try {
            URL url = new URI(webhookUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "StashHunter");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload);
            }

            connection.getResponseCode();
            connection.disconnect();
        } catch (IOException | URISyntaxException | IllegalArgumentException e) {
            e.printStackTrace();
        }
    }
}
