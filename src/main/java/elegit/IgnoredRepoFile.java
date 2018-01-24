package elegit;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A subclass of RepoFile that contains a file that Git is ignoring.
 */
public class IgnoredRepoFile extends RepoFile {

    private IgnoredRepoFile(Path filePath, RepoHelper repo) {
        super(filePath, repo);
        diffButton.setText("IGNORED");
        diffButton.setId("ignoredDiffButton");
        diffButton.setTooltip(getToolTip("تم تجاهل هذا الملف لانه في المتجاهلات .gitignore.\n ازله من  .gitignore اذا تود اضافته الي جيت"));
    }

    IgnoredRepoFile(String filePathString, RepoHelper repo) {
        this(Paths.get(filePathString), repo);
    }

    @Override public boolean canAdd() { return false; }

    @Override public boolean canRemove() { return false; }
}
