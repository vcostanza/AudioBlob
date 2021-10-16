package software.blob.audio.ui.editor.instruments;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.util.Misc;

/**
 * A drum pad with random samples
 */
public class GenDrumInstrument extends GenInstrument {

    private static final String[] DRUM_NOTE_NAMES = { "G2", "D2", "A2", "C3", "A#2", "C#3", "D#3" };
    private static final int[] DRUM_NOTES = new int[DRUM_NOTE_NAMES.length];
    private static final int[] VELOCITIES = {0, 64, 92, 120};

    static {
        for (int i = 0; i < DRUM_NOTES.length; i++)
            DRUM_NOTES[i] = Misc.getNoteValue(DRUM_NOTE_NAMES[i]);
    }

    public GenDrumInstrument(AudioEditor editor) {
        super(editor);
    }

    @Override
    protected void forEachNote(NoteIterator noteIter) {
        int[] velocities = params.velocityControls.isEmpty()
                || params.velocityControls.size() == 1
                && params.velocityControls.contains(GenInstrumentParams.Velocity.AMPLITUDE)
                ? new int[1] : VELOCITIES;
        int i = 0, total = DRUM_NOTES.length * velocities.length;
        for (int n : DRUM_NOTES) {
            for (int v : velocities)
                noteIter.forEachNote(n, v, i++, total);
        }
    }
}
