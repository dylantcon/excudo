package com.excudo.core.utils;

import java.util.*;

/**
 * Lightweight JSON builder for Excudo CLI responses
 * Avoids external dependencies while providing clean JSON output
 */
public class JSONBuilder {
    
    private final Map<String, Object> data;
    
    public JSONBuilder() {
        this.data = new LinkedHashMap<>();
    }
    
    /**
     * Add a string value
     */
    public JSONBuilder put(String key, String value) {
        data.put(key, value);
        return this;
    }
    
    /**
     * Add an integer value
     */
    public JSONBuilder put(String key, int value) {
        data.put(key, value);
        return this;
    }
    
    /**
     * Add a long value
     */
    public JSONBuilder put(String key, long value) {
        data.put(key, value);
        return this;
    }
    
    /**
     * Add a boolean value
     */
    public JSONBuilder put(String key, boolean value) {
        data.put(key, value);
        return this;
    }
    
    /**
     * Add a double value
     */
    public JSONBuilder put(String key, double value) {
        data.put(key, value);
        return this;
    }
    
    /**
     * Add a nested object
     */
    public JSONBuilder putObject(String key, JSONBuilder nestedObject) {
        data.put(key, nestedObject);
        return this;
    }
    
    /**
     * Add an array of strings
     */
    public JSONBuilder putArray(String key, List<String> values) {
        data.put(key, values);
        return this;
    }
    
    /**
     * Add an array of JSON objects
     */
    public JSONBuilder putObjectArray(String key, List<JSONBuilder> objects) {
        data.put(key, objects);
        return this;
    }
    
    /**
     * Create a nested object and return its builder
     */
    public JSONBuilder createNestedObject(String key) {
        JSONBuilder nested = new JSONBuilder();
        data.put(key, nested);
        return nested;
    }
    
    /**
     * Convert to JSON string
     */
    @Override
    public String toString() {
        return toJSONString(data, 0);
    }
    
    /**
     * Convert to formatted JSON string with indentation
     */
    public String toFormattedString() {
        return toJSONString(data, 0, true);
    }
    
    private String toJSONString(Object obj, int indent) {
        return toJSONString(obj, indent, false);
    }
    
    @SuppressWarnings("unchecked")
    private String toJSONString(Object obj, int indent, boolean formatted) {
        if (obj == null) {
            return "null";
        }
        
        if (obj instanceof String) {
            return escapeString((String) obj);
        }
        
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        
        if (obj instanceof JSONBuilder) {
            return toJSONString(((JSONBuilder) obj).data, indent, formatted);
        }
        
        if (obj instanceof Map) {
            return mapToJSON((Map<String, Object>) obj, indent, formatted);
        }
        
        if (obj instanceof List) {
            return listToJSON((List<Object>) obj, indent, formatted);
        }
        
        // Fallback for other types
        return escapeString(obj.toString());
    }
    
    private String mapToJSON(Map<String, Object> map, int indent, boolean formatted) {
        if (map.isEmpty()) {
            return "{}";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        
        if (formatted) {
            sb.append("\n");
        }
        
        Iterator<Map.Entry<String, Object>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            
            if (formatted) {
                sb.append(getIndent(indent + 1));
            }
            
            sb.append(escapeString(entry.getKey()));
            sb.append(":");
            
            if (formatted) {
                sb.append(" ");
            }
            
            sb.append(toJSONString(entry.getValue(), indent + 1, formatted));
            
            if (iterator.hasNext()) {
                sb.append(",");
            }
            
            if (formatted) {
                sb.append("\n");
            }
        }
        
        if (formatted) {
            sb.append(getIndent(indent));
        }
        
        sb.append("}");
        return sb.toString();
    }
    
    private String listToJSON(List<Object> list, int indent, boolean formatted) {
        if (list.isEmpty()) {
            return "[]";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        
        if (formatted) {
            sb.append("\n");
        }
        
        for (int i = 0; i < list.size(); i++) {
            if (formatted) {
                sb.append(getIndent(indent + 1));
            }
            
            sb.append(toJSONString(list.get(i), indent + 1, formatted));
            
            if (i < list.size() - 1) {
                sb.append(",");
            }
            
            if (formatted) {
                sb.append("\n");
            }
        }
        
        if (formatted) {
            sb.append(getIndent(indent));
        }
        
        sb.append("]");
        return sb.toString();
    }
    
    private String escapeString(String str) {
        if (str == null) {
            return "null";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("\"");
        
        for (char c : str.toCharArray()) {
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        
        sb.append("\"");
        return sb.toString();
    }
    
    private String getIndent(int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level * 2; i++) {
            sb.append(" ");
        }
        return sb.toString();
    }
    
    /**
     * Parse simple JSON response for success/failure
     * Only handles basic boolean extraction for our CLI use case
     */
    public static boolean parseSuccess(String jsonResponse) {
        try {
            // Simple regex-based parsing for our specific use case
            return jsonResponse.contains("\"success\":true") || 
                   jsonResponse.contains("\"success\": true");
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Parse simple JSON response for message
     */
    public static String parseMessage(String jsonResponse) {
        try {
            // Simple regex-based parsing for our specific use case
            int messageStart = jsonResponse.indexOf("\"message\":\"");
            if (messageStart == -1) {
                messageStart = jsonResponse.indexOf("\"message\": \"");
                if (messageStart == -1) return "";
            }
            
            int valueStart = jsonResponse.indexOf("\"", messageStart + 10) + 1;
            int valueEnd = jsonResponse.indexOf("\"", valueStart);
            
            if (valueStart > 0 && valueEnd > valueStart) {
                return jsonResponse.substring(valueStart, valueEnd);
            }
            
            return "";
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * Simple JSON parser for command parsing
     * Only handles basic string/int extraction for our CLI use case
     */
    public static JSONBuilder fromString(String jsonString) {
        JSONBuilder builder = new JSONBuilder();
        // For now, return empty builder - we'll pass the raw JSON string
        // In a production system, this would use a proper JSON parser
        builder.data.put("_raw", jsonString);
        return builder;
    }
    
    /**
     * Get string value from parsed JSON
     */
    public String getString(String key) {
        String rawJson = (String) data.get("_raw");
        if (rawJson == null) return null;
        
        return extractJsonStringValue(rawJson, key);
    }
    
    /**
     * Get string value with default
     */
    public String getString(String key, String defaultValue) {
        String value = getString(key);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Get int value from parsed JSON
     */
    public int getInt(String key, int defaultValue) {
        String rawJson = (String) data.get("_raw");
        if (rawJson == null) return defaultValue;
        
        String value = extractJsonStringValue(rawJson, key);
        if (value == null) return defaultValue;
        
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private static String extractJsonStringValue(String json, String key) {
        try {
            String searchPattern = "\"" + key + "\"";
            int keyIndex = json.indexOf(searchPattern);
            if (keyIndex == -1) return null;
            
            int colonIndex = json.indexOf(":", keyIndex);
            if (colonIndex == -1) return null;
            
            // Skip whitespace after colon
            int valueStart = colonIndex + 1;
            while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
                valueStart++;
            }
            
            if (valueStart >= json.length()) return null;
            
            char firstChar = json.charAt(valueStart);
            
            if (firstChar == '"') {
                // String value
                int valueEnd = json.indexOf('"', valueStart + 1);
                if (valueEnd == -1) return null;
                return json.substring(valueStart + 1, valueEnd);
            } else {
                // Number or boolean value
                int valueEnd = valueStart;
                while (valueEnd < json.length()) {
                    char c = json.charAt(valueEnd);
                    if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) {
                        break;
                    }
                    valueEnd++;
                }
                return json.substring(valueStart, valueEnd);
            }
            
        } catch (Exception e) {
            return null;
        }
    }
}