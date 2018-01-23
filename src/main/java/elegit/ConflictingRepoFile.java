package elegit;

import elegit.exceptions.MissingRepoException;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.MenuItem;
import javafx.scene.image.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A subclass of the RepoFile class that holds a reference to
 * and interacts with a file in the repository that has conflicts
 * in git.
 */
public class ConflictingRepoFile extends RepoFile {

    private String resultType;

    static final Logger logger = LogManager.getLogger();

    public ConflictingRepoFile(Path filePath, RepoHelper repo) {
        super(filePath, repo);
        diffButton.setText("CONFLICTING");
        diffButton.setId("conflictingDiffButton");
        diffButton.setTooltip(getToolTip("يسبب هذا الملف تعارض دمج.\n عدل الملف لحل التضارب."));
        MenuItem resolveMerge = new MenuItem("حل التعارض...");
        contextMenu.getItems().add(resolveMerge);
    }

    public ConflictingRepoFile(String filePathString, RepoHelper repo) {
        this(Paths.get(filePathString), repo);
    }

    @Override public boolean canAdd() throws GitAPIException, IOException{
        ReentrantLock lock = new ReentrantLock();
        Condition finishedAlert = lock.newCondition();

        Platform.runLater(() -> {
            logger.warn("تنبيه عن ملف متعارض");
            lock.lock();
            try{
                resultType = PopUpWindows.showCommittingConflictingFileAlert();
                finishedAlert.signal();
            }finally{
                lock.unlock();
            }
        });

        lock.lock();
        try{
            finishedAlert.await();
            //System.out.println(resultType);
            switch (resultType) {
                case "resolve":
                    Desktop desktop = Desktop.getDesktop();

                    File workingDirectory = this.repo.getRepo().getWorkTree();
                    File unrelativized = new File(workingDirectory, this.filePath.toString());

                    desktop.open(unrelativized);
                    break;
                case "add":
                    return true;
                case "help":
                    PopUpWindows.showConflictingHelpAlert();
                    break;
            }
        }catch(InterruptedException ignored){
        }finally{
            lock.unlock();
        }
        return false;
    }

    @Override public boolean canRemove() {
        return true;
    }
}
