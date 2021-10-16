package software.blob.audio.ui.editor.controllers;

import software.blob.audio.ui.editor.EditorProject;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.events.EditorProjectListener;
import software.blob.audio.ui.editor.events.EditorTrackListener;
import software.blob.audio.ui.editor.instruments.Instrument;
import software.blob.audio.ui.editor.track.Track;
import software.blob.audio.util.Misc;

import java.util.*;

/**
 * Track manager
 */
public class TrackController extends EditorController implements EditorProjectListener, Iterable<Track> {

    private final List<Track> tracks = new ArrayList<>();
    private final Map<Long, Track> trackMap = new HashMap<>();
    private Track selected;
    private final List<EditorTrackListener> listeners = new ArrayList<>();

    public TrackController(AudioEditor editor) {
        super(editor);
    }

    /**
     * Get the currently selected track
     * @return Track
     */
    public Track getSelected() {
        return this.selected;
    }

    /**
     * Get the first track in the list
     * @return First track
     */
    public Track getFirst() {
        return tracks.isEmpty() ? null : tracks.get(0);
    }

    /**
     * Get the last track in the list
     * @return Last track
     */
    public Track getLast() {
        return tracks.isEmpty() ? null : tracks.get(tracks.size() - 1);
    }

    /**
     * Get the track at the given position index
     * @param index Index
     * @return Track or null if not found
     */
    public Track get(int index) {
        return index >= 0 && index < tracks.size() ? tracks.get(index) : null;
    }

    /**
     * Get all tracks
     * @return Track list
     */
    public List<Track> getAll() {
        return this.tracks;
    }

    /**
     * Get the number of tracks
     * @return Number of tracks
     */
    public int getCount() {
        return this.tracks.size();
    }

    /**
     * Find a track by ID
     * @param id ID number
     * @return Track or null if not found
     */
    public Track findByID(long id) {
        return trackMap.get(id);
    }

    /**
     * Add a new track
     * @param track Track
     */
    public void add(Track track) {
        if (track != null && tracks.add(track)) {
            trackMap.put(track.id, track);
            for (EditorTrackListener l : listeners)
                l.onTrackAdded(track);
        }
    }

    /**
     * Remove a track
     * @param track Track to remove
     */
    public void remove(Track track) {
        if (tracks.remove(track)) {
            trackMap.remove(track.id);
            for (EditorTrackListener l : listeners)
                l.onTrackRemoved(track);
        }
        if (track == selected)
            select(!tracks.isEmpty() ? tracks.get(0) : null);
    }

    /**
     * Move the given track to the set position
     * @param track Track
     * @param position Position
     */
    public void move(Track track, int position) {
        if (tracks.remove(track)) {
            position = Misc.clamp(position, 0, tracks.size());
            tracks.add(position, track);
        }
    }

    /**
     * Create a new track with the given name
     * @param name Track name
     * @return Newly created track
     */
    public Track create(String name) {
        Track track = new Track(editor, name);
        add(track);
        return track;
    }

    /**
     * Create a new track with a default name
     * @return New track
     */
    public Track create() {
        return create("Track " + (tracks.size() + 1));
    }

    /**
     * Select a track
     * @param track Track to select
     */
    public void select(Track track) {
        if (this.selected != track) {
            this.selected = track;
            for (Track tr : this)
                tr.selected = tr == track;
            for (EditorTrackListener l : listeners)
                l.onTrackSelected(track);
        }
    }

    /**
     * Clear and dispose all tracks
     */
    public void clear() {
        for (Track track : tracks) {
            for (EditorTrackListener l : listeners)
                l.onTrackRemoved(track);
        }
        tracks.clear();
        trackMap.clear();
        select(null);
    }

    /**
     * Get all instruments w/out duplicates
     * @return Instrument list
     */
    public List<Instrument> getInstruments() {
        // Add instruments w/out duplicates
        Set<Instrument> instSet = new HashSet<>();
        for (Track tr : this) {
            if (tr.instrument != null)
                instSet.add(tr.instrument);
        }

        // Sort by name
        List<Instrument> instruments = new ArrayList<>(instSet);
        instruments.sort(Instrument.SORT_NAME);
        return instruments;
    }

    @Override
    public void refreshListeners() {
        listeners.clear();
        listeners.addAll(editor.getEditorListeners(EditorTrackListener.class));
    }

    @Override
    public void dispose() {
        clear();
    }

    @Override
    public void onLoadProject(EditorProject project) {
        clear();
        if (project.tracks != null) {
            tracks.addAll(project.tracks);
            if (!tracks.isEmpty()) {
                Track selected = tracks.get(0);
                for (Track track : tracks) {
                    trackMap.put(track.id, track);
                    if (track.selected)
                        selected = track;
                }
                select(selected);
            }
        }
    }

    @Override
    public void onSaveProject(EditorProject project) {
        project.tracks = new ArrayList<>(this.tracks);
    }

    @Override
    public Iterator<Track> iterator() {
        return tracks.iterator();
    }
}
