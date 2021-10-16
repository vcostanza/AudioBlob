package software.blob.audio.ui.editor.controllers;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.EditorProject;
import software.blob.audio.ui.editor.events.EditorProjectListener;
import software.blob.audio.ui.editor.view.SidebarButton;
import software.blob.ui.util.Log;
import software.blob.ui.view.layout.InflatedLayout;
import software.blob.ui.view.layout.LinearLayout;

import java.awt.*;
import java.util.*;

/**
 * Sidebar buttons
 */
public class SidebarController extends EditorController implements EditorProjectListener {

    private final InflatedLayout inf;
    private final Map<String, SidebarButton> sideButtons = new HashMap<>();

    public SidebarController(AudioEditor editor) {
        super(editor);
        this.inf = editor.getInflatedLayout();
        initSidebar("leftSidebar");
        initSidebar("rightSidebar");
    }

    @Override
    public void onLoadProject(EditorProject project) {
        if (project.sidebars == null)
            return;
        Set<String> names = new HashSet<>(project.sidebars);
        for (Map.Entry<String, SidebarButton> e : sideButtons.entrySet()) {
            String name = e.getKey();
            SidebarButton btn = e.getValue();
            toggleSidebar(btn, names.contains(name));
        }
    }

    @Override
    public void onSaveProject(EditorProject project) {
        project.sidebars = new ArrayList<>();
        for (Map.Entry<String, SidebarButton> e : sideButtons.entrySet()) {
            String name = e.getKey();
            SidebarButton btn = e.getValue();
            if (btn.isSelected())
                project.sidebars.add(name);
        }
    }

    private void initSidebar(String name) {
        LinearLayout sidebar = inf.findByName(name);
        if (sidebar == null)
            return;

        for (Component c : sidebar.getComponents()) {
            if (c instanceof SidebarButton)
                initButton((SidebarButton) c);
        }
    }

    private void initButton(final SidebarButton btn) {
        String viewName = btn.getAttribute("view", null);
        if (viewName == null) {
            Log.w("Sidebar button does not have a view name: " + btn.getName());
            return;
        }

        final Component view = inf.findByName(viewName);
        if (view == null) {
            Log.w("Sidebar button view not found: " + viewName);
            return;
        }

        // Associate side pane with the button without having to look it up again
        btn.setTag(view);
        sideButtons.put(viewName, btn);

        // Make sure the side pane and button are synced with the default visibility
        boolean sVisible = view.getParent().isVisible();
        view.setVisible(sVisible);
        btn.setSelected(sVisible);

        // Button click listener
        btn.setOnClickListener((v, event) -> toggleSidebar(btn));
    }

    private void toggleSidebar(SidebarButton btn, boolean visible) {
        Object tag = btn.getTag();
        if (!(tag instanceof Component)) {
            Log.e("Sidebar button for " + btn.getAttribute("view", null) + " does not have tag set!");
            return;
        }

        Component sidePane = (Component) tag;

        btn.setSelected(visible);
        sidePane.setVisible(visible);

        // Check if any of the parent views are visible
        Container parent = sidePane.getParent();
        boolean childrenVisible = checkChildrenVisible(parent);

        // Toggle visibility of the entire side pane accordingly
        parent.setVisible(childrenVisible);
    }

    private void toggleSidebar(SidebarButton btn) {
        toggleSidebar(btn, !btn.isSelected());
    }

    private static boolean checkChildrenVisible(Container container) {
        for (Component c : container.getComponents()) {
            if (c.isVisible())
                return true;
        }
        return false;
    }
}
