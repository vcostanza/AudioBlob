package software.blob.audio.ui.editor.instruments;

import software.blob.audio.wave.WavData;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Note + velocity mapped to wav
 */
public class NoteWavMap {

    private final Map<Integer, WavData> map = new HashMap<>();

    public void put(int note, int velocity, WavData wav) {
        map.put(getKey(note, velocity), wav);
    }

    public WavData get(int note, int velocity) {
        return map.get(getKey(note, velocity));
    }

    public int size() {
        return map.size();
    }

    public void clear() {
        map.clear();
    }

    private static int getKey(int note, int velocity) {
        return Objects.hash(note, velocity);
    }
}
