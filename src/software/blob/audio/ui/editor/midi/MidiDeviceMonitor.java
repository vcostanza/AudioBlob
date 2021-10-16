package software.blob.audio.ui.editor.midi;

import software.blob.ui.util.Log;

import javax.sound.midi.*;
import java.util.*;

/**
 * Monitors midi device input and automatically opens any newly connected devices
 */
public class MidiDeviceMonitor {

    private final Thread monitorThread;
    private final Set<String> ignore = new HashSet<>();

    private final Receiver receiver;
    private MidiDevice lastDevice;
    private boolean active = true;

    public MidiDeviceMonitor(Receiver receiver) {
        this.receiver = receiver;

        // Default devices to ignore
        ignore.add("Real Time Sequencer [RealTimeSequencerInfo]");

        monitorThread = new Thread(this::run, getClass().getSimpleName());
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    public void dispose() {
        active = false;
    }

    public MidiDevice getLastConnectedDevice() {
        return this.lastDevice;
    }

    private void run() {
        while (active) {
            scan();
            try {
                Thread.sleep(1000);
            } catch (Exception ignore) {}
        }
    }

    private void scan() {
        MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
        for (MidiDevice.Info info : infos) {

            String infoStr = info.toString() + " [" + info.getClass().getSimpleName() + "]";

            // Device does not have a transmitter - ignore
            if (ignore.contains(infoStr))
                continue;

            try {
                MidiDevice device = MidiSystem.getMidiDevice(info);
                if (!device.isOpen()) {
                    Transmitter trans = device.getTransmitter();
                    trans.setReceiver(receiver);
                    device.open();
                    Log.d(infoStr + " was opened");
                    lastDevice = device;
                }
            } catch (MidiUnavailableException e) {
                // Ignore devices that do not have transmitters
                Log.d("Ignoring " + infoStr + ": " + e.getMessage());
                ignore.add(infoStr);
            }
        }
    }
}
