package elegit;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A subclass of RepoFile that contains a file that Git reports as missing.
 */
public class MissingRepoFile extends RepoFile {

    MissingRepoFile(Path filePath, RepoHelper repo) {
        super(filePath, repo);
        diffButton.setText("مفقود");
        diffButton.setId("missingDiffButton");
        diffButton.setTooltip(getToolTip("هذا الملف مفقود."));
    }

    MissingRepoFile(String filePathString, RepoHelper repo) {
        this(Paths.get(filePathString), repo);
    }

    @Override public boolean canAdd() { return false; }

    @Override public boolean canRemove() { return true; }
}
