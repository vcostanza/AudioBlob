package software.blob.audio.ui.editor.res;

import software.blob.ui.res.Resources;
import software.blob.ui.view.layout.LayoutInflater;

/**
 * Used for resource loading in this directory
 */
public class AudioEditorResources {

    public static void init() {
        Resources.addResourceClass(AudioEditorResources.class);

        // Package roots for views used in each of the layouts
        LayoutInflater.addPackageRoot("software.blob.audio.ui.editor");
        LayoutInflater.addPackageRoot("software.blob.audio.ui.editor.view");
        LayoutInflater.addPackageRoot("software.blob.audio.ui.editor.view.pattern");
        LayoutInflater.addPackageRoot("software.blob.audio.ui.editor.view.track");
    }
}
