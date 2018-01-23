package elegit.controllers;

import elegit.RepoHelper;
import elegit.SessionModel;
import elegit.exceptions.NoFilesToStashException;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.errors.GitAPIException;

public class StashSaveController {

    private Stage stage;

    private SessionController sessionController;
    private RepoHelper repoHelper;

    @FXML private NotificationController notificationPaneController;
    @FXML private AnchorPane anchorRoot;
    @FXML private Button saveButton;
    @FXML private TextField stashMessage;
    @FXML private CheckBox includeUntracked;

    static final Logger logger = LogManager.getLogger();

    /**
     * Initialize method automatically called by JavaFX
     *
     * Sets up views and buttons
     */
    public void initialize() {
        logger.info("بدءت نافذت حفظ اخفاء");

        SessionModel sessionModel = SessionModel.getSessionModel();
        this.repoHelper = sessionModel.getCurrentRepoHelper();

        stashMessage.setOnAction((event -> {
            stashSave(stashMessage.getText());
        }));

        this.notificationPaneController.bindParentBounds(anchorRoot.heightProperty());
    }

    /**
     * Shows the stash list manager
     * @param pane the anchor of the stage
     */
    public void showStage(AnchorPane pane) {
        stage = new Stage();
        stage.setTitle("حفظ اخفاء");
        stage.setScene(new Scene(pane));
        stage.setOnCloseRequest(event -> {
            logger.info("أغلقت نافذت حفظ اخفاء");
        });
        stage.show();
        this.notificationPaneController.setAnchor(stage);
    }

    /* ********************** BUTTON HANDLERS ********************** */
    public void closeWindow() { this.stage.close(); }

    public void handleSave() {
        if (stashMessage.getText() != null)
            stashSave(stashMessage.getText());
        else
            stashSave();
    }

    public void stashSave() {
        try {
            repoHelper.stashSave(includeUntracked.isSelected());
            sessionController.gitStatus();
            closeWindow();
        } catch (GitAPIException e) {
            notificationPaneController.addNotification("شيء ما خطأ اثناء الحفظ.");
        } catch (NoFilesToStashException e) {
            notificationPaneController.addNotification("لم تخف ملفات.");
        }
    }

    public void stashSave(String message) {
        try {
            repoHelper.stashSave(includeUntracked.isSelected(), message,"");
            sessionController.gitStatus();
            closeWindow();
        } catch (GitAPIException e) {
            notificationPaneController.addNotification("شيء ما خطأ اثناء الحفظ.");
        } catch (NoFilesToStashException e) {
            notificationPaneController.addNotification("لم تخف ملفات");
        }
    }

    void setSessionController(SessionController controller) { this.sessionController = controller; }

}
