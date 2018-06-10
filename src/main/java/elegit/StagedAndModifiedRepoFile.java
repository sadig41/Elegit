package elegit;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A subclass of RepoFile that contains a file that Git is ignoring.
 */
public class StagedAndModifiedRepoFile extends RepoFile {

    private StagedAndModifiedRepoFile(Path filePath, RepoHelper repo) {
        super(filePath, repo);
        diffButton.setText("STAGED\nMODIFIED");
        diffButton.setId("stagedModifiedDiffButton");
        diffButton.setTooltip(getToolTip("لهذا الملف نسخة مخزنة في فهرس جيت\n وتعديلات اخري في دليل العمل"));
    }

    StagedAndModifiedRepoFile(String filePathString, RepoHelper repo) {
        this(Paths.get(filePathString), repo);
    }

    @Override public boolean canAdd() {
        return true;
    }

    @Override public boolean canRemove() {
        return true;
    }
}
