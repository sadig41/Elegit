package elegit;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A subclass of RepoFile that contains a file that Git reports as modified.
 */
public class ModifiedRepoFile extends RepoFile {

    ModifiedRepoFile(Path filePath, RepoHelper repo) {
        super(filePath, repo);
        diffButton.setText("معدل");
        diffButton.setId("modifiedDiffButton");
        diffButton.setTooltip(getToolTip("عدل هذا الملف بعد احدث ايداع قمت به."));
        showPopover = true;
    }

    ModifiedRepoFile(String filePathString, RepoHelper repo) {
        this(Paths.get(filePathString), repo);
    }

    @Override public boolean canAdd() { return true; }

    @Override public boolean canRemove() { return false; }
}
