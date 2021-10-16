package software.blob.audio.ui.editor.view.pattern;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.controllers.PatternController;
import software.blob.audio.ui.editor.track.Pattern;
import software.blob.ui.view.View;
import software.blob.ui.view.layout.LayoutInflater;
import software.blob.ui.view.layout.list.ListAdapter;
import software.blob.ui.view.layout.list.ListView;

/**
 * List adapter for pattern buttons
 */
public class PatternAdapter extends ListAdapter {

    private final AudioEditor editor;
    private final PatternController patterns;

    public PatternAdapter(AudioEditor editor) {
        this.editor = editor;
        this.patterns = editor.getPatterns();
    }

    @Override
    public int getCount() {
        return patterns.getCount();
    }

    @Override
    public Pattern getItem(int position) {
        return patterns.get(position);
    }

    @Override
    public View getView(int position, View existing, ListView list) {
        Pattern pattern = getItem(position);
        PatternLayout layout = existing instanceof PatternLayout ? (PatternLayout) existing : null;
        if (layout == null) {
            layout = LayoutInflater.inflate("pattern_layout").getRoot();
            layout.init(editor);
        }
        layout.setPattern(pattern);
        return layout;
    }
}
