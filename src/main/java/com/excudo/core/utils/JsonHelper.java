package com.excudo.core.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * Thin static helpers over Gson's JsonObject for consistent JSON access
 * across the codebase. Replaces the duplicated extractInt/extractString/extractLong
 * methods that were copy-pasted across 4+ files.
 */
public final class JsonHelper {

    private JsonHelper() {}

    public static String getString(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return null;
        return el.getAsString();
    }

    public static int getInt(JsonObject obj, String key, int defaultValue) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return defaultValue;
        return el.getAsInt();
    }

    public static long getLong(JsonObject obj, String key, long defaultValue) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return defaultValue;
        return el.getAsLong();
    }

    public static double getDouble(JsonObject obj, String key, double defaultValue) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return defaultValue;
        return el.getAsDouble();
    }

    public static boolean getBoolean(JsonObject obj, String key, boolean defaultValue) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return defaultValue;
        return el.getAsBoolean();
    }

    /**
     * Parse a JSON string as a JsonObject.
     * @throws IllegalArgumentException if the string is not valid JSON or not an object
     */
    public static JsonObject parseObject(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw new IllegalArgumentException("Invalid JSON object: " + e.getMessage(), e);
        }
    }

    /**
     * Parse a JSON string as a JsonArray.
     * @throws IllegalArgumentException if the string is not valid JSON or not an array
     */
    public static JsonArray parseArray(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonArray();
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw new IllegalArgumentException("Invalid JSON array: " + e.getMessage(), e);
        }
    }
}
