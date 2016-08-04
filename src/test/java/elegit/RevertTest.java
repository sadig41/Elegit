package elegit;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Created by Eric Walker.
 */
public class RevertTest {
    private Path directoryPath;
    private String testFileLocation;
    private RepoHelper helper;
    private static final String EDIT_STRING = "Lorem Ipsum";
    Path logPath;

    // Used to indicate that if password files are missing, then tests should just pass
    private boolean looseTesting;

    @Before
    public void setUp() throws Exception {
        initializeLogger();
        this.directoryPath = Files.createTempDirectory("unitTestRepos");
        directoryPath.toFile().deleteOnExit();
        testFileLocation = System.getProperty("user.home") + File.separator +
                "elegitTests" + File.separator;
        File strictTestingFile = new File(testFileLocation + "strictAuthenticationTesting.txt");
        looseTesting = !strictTestingFile.exists();
    }

    @After
    public void tearDown() throws Exception {
        removeAllFilesFromDirectory(this.logPath.toFile());
    }

    // Helper method to avoid annoying traces from logger
    void initializeLogger() {
        // Create a temp directory for the files to be placed in
        try {
            this.logPath = Files.createTempDirectory("elegitLogs");
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.logPath.toFile().deleteOnExit();
        System.setProperty("logFolder", logPath.toString());
    }

    // Helper tear-down method:
    void removeAllFilesFromDirectory(File dir) {
        for (File file: dir.listFiles()) {
            if (file.isDirectory()) removeAllFilesFromDirectory(file);
            file.delete();
        }
    }

    // Test to make sure creating a local branch lets us push and
    // that pushing will create the new branch.
    @Test
    public void testResetFile() throws Exception {
        File authData = new File(testFileLocation + "httpUsernamePassword.txt");

        // If a developer does not have this file present, test should just pass.
        if (!authData.exists() && looseTesting)
            return;

        Scanner scanner = new Scanner(authData);
        String ignoreURL = scanner.next();
        String username = scanner.next();
        String password = scanner.next();
        UsernamePasswordCredentialsProvider credentials = new UsernamePasswordCredentialsProvider(username, password);

        String remoteURL = "https://github.com/TheElegitTeam/ResetTesting.git";

        // Repo that will commit to master
        Path repoPath = directoryPath.resolve("repo");
        helper = new ClonedRepoHelper(repoPath, remoteURL, credentials);
        assertNotNull(helper);

        Git git = new Git(helper.repo);
        Path filePath = repoPath.resolve("modify.txt");


        /* ********************* SINGLE REVERT SECTION ********************* */
        // make a commit, then revert it, check that changes occurred

        helper.getBranchModel().refreshHeadIds();
        String oldHead = helper.getBranchModel().getCurrentBranchHead().getId();

        modifyAddFile(filePath);
        helper.commit("Modified file #1");
        helper.updateModel();
        helper.getBranchModel().refreshHeadIds();
        assertEquals(false, helper.getBranchModel().getCurrentBranchHead().getId().equals(oldHead));
        helper.revert(helper.getBranchModel().getCurrentBranchHead());
        // The EDIT_TEXT should have been reverted
        assertEquals(1, Files.readAllLines(filePath).size());
        // And a new HEAD should be there
        assertEquals(false, helper.getBranchModel().getCurrentBranchHead().getId().equals(oldHead));

        /* ********************* MULTIPLE REVERT SECTION ********************* */
        // make a commit, then another, then another, revert first and third, check content

    }

    private void modifyFile(Path file) throws Exception {
        Files.write(file, EDIT_STRING.getBytes(), StandardOpenOption.APPEND);
    }

    private void modifyAddFile(Path file) throws Exception {
        Files.write(file, EDIT_STRING.getBytes(), StandardOpenOption.APPEND);
        helper.addFilePath(file);
    }
}
