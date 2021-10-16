package software.blob.audio.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Helper methods relating to JSON parsing
 */
public class JSONUtils {

    /**
     * Read JSON object from a file
     * @param file File
     * @return JSON object
     * @throws Exception Failed to parse file as a JSON object
     */
    public static JSONObject readObject(File file) throws Exception {
        return new JSONObject(readString(file));
    }

    /**
     * Read JSON array from a file
     * @param file File
     * @return JSON array
     * @throws Exception Failed to parse file as a JSON array
     */
    public static JSONArray readArray(File file) throws Exception {
        return new JSONArray(readString(file));
    }

    /**
     * Read JSON file as a string
     * @param file JSON file
     * @return String
     * @throws Exception Failed to read or parse string
     */
    public static String readString(File file) throws Exception {
        byte[] data = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            if (fis.read(data) != data.length)
                throw new Exception("Unexpected read length");
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    /**
     * Write a JSON object to a file
     * @param file File to write to
     * @param json JSON object
     * @throws Exception Failed to write object
     */
    public static void writeObject(File file, JSONObject json) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(json.toString(4).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Put a JSON object if it isn't empty
     * @param json JSON object to put inside
     * @param key Key string
     * @param value JSON object
     */
    public static void putIfNonEmpty(JSONObject json, String key, JSONObject value) {
        if (value != null && !value.isEmpty())
            json.put(key, value);
    }
}
