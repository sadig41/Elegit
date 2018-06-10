package elegit;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A subclass of RepoFile that contains a file that Git is ignoring.
 */
public class StagedRepoFile extends RepoFile {

    StagedRepoFile(Path filePath, RepoHelper repo) {
        super(filePath, repo);
        diffButton.setText("STAGED");
        diffButton.setId("stagedDiffButton");
        diffButton.setTooltip(getToolTip("لهذا الملف اصدارة محفوظة في فهرس جيت \n وهو جاهز للايداع."));
    }

    StagedRepoFile(String filePathString, RepoHelper repo) {
        this(Paths.get(filePathString), repo);
    }

    @Override public boolean canAdd() {
        return false;
    }

    @Override public boolean canRemove() {
        return true;
    }
}
