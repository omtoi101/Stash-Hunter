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

public class DiscordWebhook {
    private static final Gson GSON = new Gson();

    public static void sendMessage(String content, DiscordEmbed embed) {
        if (Config.discordWebhookUrl.isEmpty()) {
            return;
        }

        try {
            URL url = new URI(Config.discordWebhookUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "StashHunter");
            connection.setDoOutput(true);

            JsonObject json = new JsonObject();
            json.addProperty("content", content);
            if (embed != null) {
                JsonArray embeds = new JsonArray();
                embeds.add(GSON.toJsonTree(embed));
                json.add("embeds", embeds);
            }

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            connection.getResponseCode();
            connection.disconnect();
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
    }
}
