package dev.goddeh.retainlastserver.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class UUIDLookupService {

    private static final Gson GSON = new Gson();
    private static final String MOJANG_API_URL = "https://api.mojang.com/users/profiles/minecraft/";

    /**
     * Asynchronously fetches a player's UUID from Mojang API using their username
     *
     * @param username The player's username
     * @return A CompletableFuture that resolves to the player's UUID if found, or null if not found
     */
    public static CompletableFuture<UUID> getUUID(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL(MOJANG_API_URL + username);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestMethod("GET");

                int status = connection.getResponseCode();

                if (status == 200) {
                    try (InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
                        JsonObject response = GSON.fromJson(reader, JsonObject.class);

                        // Parse the UUID (Mojang returns it without dashes)
                        String id = response.get("id").getAsString();
                        String username_correct = response.get("name").getAsString();

                        // Convert to UUID with dashes
                        return UUID.fromString(id.replaceFirst(
                                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                                "$1-$2-$3-$4-$5"
                        ));
                    }
                } else if (status == 204 || status == 404) {
                    // Player not found
                    return null;
                } else {
                    // Some other error
                    System.out.println("Error fetching UUID for " + username + ": HTTP " + status);
                    return null;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    /**
     * Gets the correct username capitalization from the Mojang API
     *
     * @param username The player's username (any capitalization)
     * @return A CompletableFuture that resolves to the correctly capitalized username, or null if not found
     */
    public static CompletableFuture<String> getCorrectUsername(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL(MOJANG_API_URL + username);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestMethod("GET");

                int status = connection.getResponseCode();

                if (status == 200) {
                    try (InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
                        JsonObject response = GSON.fromJson(reader, JsonObject.class);
                        return response.get("name").getAsString();
                    }
                } else {
                    return null;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        });
    }
}