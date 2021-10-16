package software.blob.audio.ui.editor.view;

import software.blob.ui.view.menu.MenuItemView;
import software.blob.ui.view.menu.MenuView;

import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Manages a menu that contains recently opened files
 */
public class RecentFilesMenuManager {

    private final MenuView menu;
    private final String pref;
    private final Preferences prefs;
    private final List<File> files;

    private int maxFiles;
    private ActionListener actionListener;

    public RecentFilesMenuManager(MenuView menu, String pref) {
        this.menu = menu;
        this.pref = pref;
        this.prefs = Preferences.userNodeForPackage(getClass());
        this.files = new ArrayList<>(maxFiles);
    }

    /**
     * Load the list of files from the preference and build the menu
     */
    public void init() {
        loadPreference();
        rebuildMenu();
    }

    /**
     * Set the max number of files to show in this menu
     * @param maxFiles Max number of files
     */
    public void setMaxFiles(int maxFiles) {
        this.maxFiles = maxFiles;
        limitFiles();
        rebuildMenu();
    }

    /**
     * Set the action listener for each file item
     * @param listener Listener
     */
    public void setActionListener(ActionListener listener) {
        this.actionListener = listener;
        rebuildMenu();
    }

    /**
     * Add a file to the menu
     * If the file already exists it will be pushed to the top
     * @param newFile New file
     */
    public void addFile(File newFile) {
        // Invalid file check
        if (newFile == null || !newFile.exists())
            return;

        // Remove file from the list if it's already there
        for (int i = 0; i < files.size(); i++) {
            File file = files.get(i);
            if (file.equals(newFile) || !file.exists())
                files.remove(i--);
        }

        // Prune old entries
        limitFiles();

        // Add file to the top of the list
        files.add(newFile);

        // Save list to preferences
        savePreference();

        // Rebuild menu
        rebuildMenu();
    }

    private void loadPreference() {
        files.clear();
        String pathList = prefs.get(pref, null);
        if (pathList != null) {
            String[] paths = pathList.split("\n");
            for (String path : paths) {
                if (path.isEmpty())
                    continue;
                File f = new File(path);
                if (f.exists())
                    files.add(f);
            }
        }
    }

    private void savePreference() {
        StringBuilder sb = new StringBuilder();
        for (File f : files)
            sb.append(f.getAbsolutePath()).append("\n");
        prefs.put(pref, sb.toString());
    }

    private void limitFiles() {
        while (files.size() >= maxFiles)
            files.remove(0);
    }

    private void rebuildMenu() {
        menu.removeAll();
        for (File f : files) {
            MenuItemView mi = new MenuItemView(f.getName());
            mi.setName(menu.getName());
            mi.setActionCommand(f.getAbsolutePath());
            mi.addActionListener(actionListener);
            menu.add(mi, 0);
        }
        menu.setVisible(!files.isEmpty());
    }
}
