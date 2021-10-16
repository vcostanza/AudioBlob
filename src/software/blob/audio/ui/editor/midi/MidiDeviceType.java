package software.blob.audio.ui.editor.midi;

import javax.sound.midi.MidiDevice;
import java.util.*;

/**
 * Types of MIDI devices (very limited list based on my own testing)
 */
public enum MidiDeviceType {

    KEYBOARD,
    DRUMS,
    OTHER;

    private static final Map<String, MidiDeviceType> deviceToType = new HashMap<>();

    static {
        deviceToType.put("Q49", KEYBOARD);
        deviceToType.put("eDrum", DRUMS);
    }

    public static MidiDeviceType getType(String deviceName) {
        MidiDeviceType type = deviceToType.get(deviceName);
        return type != null ? type : OTHER;
    }

    public static MidiDeviceType getType(MidiDevice device) {
        MidiDevice.Info info = device.getDeviceInfo();
        String name = info.getName();
        int parIdx = name.indexOf('[');
        if (parIdx > -1)
            name = name.substring(0, parIdx).trim();
        return getType(name);
    }
}
