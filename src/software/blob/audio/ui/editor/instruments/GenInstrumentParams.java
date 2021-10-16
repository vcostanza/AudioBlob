package software.blob.audio.ui.editor.instruments;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import software.blob.ui.util.Log;
import software.blob.audio.util.JSONUtils;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Generated instrument parameters
 */
public class GenInstrumentParams {

    public enum Sample {
        SAME, RANDOM
    }

    public enum Offset {
        START, RANDOM
    }

    public enum Pitch {
        DEFAULT, CONSTANT, RANDOM
    }

    public enum Fade {
        NONE, IN, OUT, RANDOM_STEREO, RANDOM_MONO
    }

    public enum Velocity {
        AMPLITUDE, PITCH, OFFSET, SAMPLE
    }

    public String name;
    public File file;
    public Sample sample = Sample.SAME;
    public Offset offset = Offset.START;
    public Pitch pitch = Pitch.DEFAULT;
    public Fade fade = Fade.NONE;
    public final Set<Velocity> velocityControls = new HashSet<>();
    public double minDuration, maxDuration;
    public boolean randomDuration;
    public int bpm;

    public GenInstrumentParams() {
    }

    public GenInstrumentParams(String name) {
        this.name = name;
    }

    public GenInstrumentParams(File file) throws IllegalArgumentException {
        if (!file.exists())
            throw new IllegalArgumentException("Failed to find params file: " + file);
        this.file = file;
        try {
            JSONObject json = JSONUtils.readObject(file);
            this.name = json.optString("name", "Untitled");
            setEnumValue(json, "sample");
            setEnumValue(json, "offset");
            setEnumValue(json, "pitch");
            setEnumValue(json, "fade");
            this.minDuration = json.optDouble("minDuration", 0);
            this.maxDuration = json.optDouble("maxDuration", 0);
            this.randomDuration = json.optBoolean("randomDuration");
            JSONArray velControls = json.optJSONArray("velocityControls");
            if (velControls != null) {
                for (int i = 0; i < velControls.length(); i++) {
                    String vel = velControls.getString(i);
                    this.velocityControls.add(Velocity.valueOf(vel.toUpperCase(Locale.ROOT)));
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read JSON metadata", e);
        }
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.putOpt("name", this.name);
        json.put("sample", this.sample.toString().toLowerCase(Locale.ROOT));
        json.put("offset", this.offset.toString().toLowerCase(Locale.ROOT));
        json.put("pitch", this.pitch.toString().toLowerCase(Locale.ROOT));
        json.put("fade", this.fade.toString().toLowerCase(Locale.ROOT));
        json.put("minDuration", this.minDuration);
        json.put("maxDuration", this.maxDuration);
        json.put("randomDuration", this.randomDuration);
        if (!this.velocityControls.isEmpty()) {
            JSONArray arr = new JSONArray();
            for (Velocity vel : velocityControls)
                arr.put(vel.toString().toLowerCase(Locale.ROOT));
            json.put("velocityControls", arr);
        }
        return json;
    }

    /**
     * Convenience method for converting string value to its corresponding enum value
     * @param key Parameter key (must match field name; i.e. "sample", "offset")
     * @param value Value string (must at least partially match enum value; i.e. "defaultPitch" -> Pitch.DEFAULT)
     * @return True if enum value successfully set
     */
    public boolean setEnumValue(String key, String value) {
        if (value == null)
            return false;
        Class<?> cl = getClass();
        try {
            Field field = cl.getField(key);
            if (field == null)
                return false;

            Class<?> type = field.getType();
            Method valuesM = type.getMethod("values");
            Method nameM = type.getMethod("name");
            if (valuesM == null || nameM == null)
                return false;

            value = value.toLowerCase()
                    .replace("_", "")
                    .replace(type.getSimpleName().toLowerCase(), "")
                    .toUpperCase();

            Object enumValues = valuesM.invoke(cl);
            int len = Array.getLength(enumValues);
            for (int i = 0; i < len; i++) {
                Object enumVal = Array.get(enumValues, i);
                String name = (String) nameM.invoke(enumVal);
                if (name.replace("_", "").equals(value)) {
                    field.set(this, enumVal);
                    return true;
                }
            }
            Log.e("Failed to find enum value \"" + value + "\"");
        } catch (Exception e) {
            Log.e("Failed to set enum value: " + key + ", " + value);
        }
        return false;
    }

    private void setEnumValue(JSONObject json, String key) {
        setEnumValue(key, json.optString(key));
    }

    @Override
    public String toString() {
        return this.name != null ? this.name : "Untitled";
    }
}
