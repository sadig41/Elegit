package elegit.controllers;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import elegit.*;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.CheckoutResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller class for the تفحص ملفات window
 */
public class CheckoutFilesController {

    private Stage stage;
    private SessionController sessionController;
    private RepoHelper repoHelper;
    private CommitHelper commitHelper;

    @FXML private TextField fileField;
    @FXML private VBox filesToCheckout;
    @FXML private NotificationController notificationPaneController;
    @FXML private Label header;
    @FXML private AnchorPane anchorRoot;

    private List<String> fileNames;

    static final Logger logger = LogManager.getLogger();

    /**
     * Initialize method automatically called by JavaFX
     *
     * Sets up views and buttons
     */
    public void initialize(){
        logger.info("بدأت نافذة تفحص ملفات من ايداع");

        SessionModel sessionModel = SessionModel.getSessionModel();
        this.repoHelper = sessionModel.getCurrentRepoHelper();
        this.fileNames = new ArrayList<>();

        this.notificationPaneController.bindParentBounds(anchorRoot.heightProperty());
    }

    /**
     * Shows the checkout manager
     * @param pane the anchor of the stage
     */
    public void showStage(AnchorPane pane) {
        stage = new Stage();
        stage.setTitle("تفحص ملفات");
        stage.setScene(new Scene(pane));
        stage.setOnCloseRequest(event -> {
            logger.info("اغلقت نافذة تفحص ملفات من ايداع");
        });
        stage.show();
        this.notificationPaneController.setAnchor(stage);
    }

    /**
     * Handler for the commit button. Attempts a commit of the added files,
     * then closes the window and notifies SessionController its done
     */
    public void handleCheckoutButton() {
        try {
            if(fileNames.size() == 0) {
                notificationPaneController.addNotification("عليك اضافة بعض الملفات اولا");
                return;
            }
            CheckoutResult result = this.repoHelper.checkoutFiles(fileNames, commitHelper.getId());
            switch (result.getStatus()) {
                case CONFLICTS:
                    notificationPaneController.addNotification("لم يكتمل التفحص بسبب تعارضات التفحص");
                    break;
                case ERROR:
                    notificationPaneController.addNotification("حدث خطلأ أثناء التفحص");
                    break;
                case NONDELETED:
                    notificationPaneController.addNotification("أكتمل التفحص، لكن لايمكن حذف بعض الملفات.");
                    break;
                case NOT_TRIED:
                    notificationPaneController.addNotification("شيء ما خطأ ... جرب التفحص مجددا.");
                    break;
                // The OK case happens when a file is changed in the index or an invalid file
                // was entered, for now just call git status and close
                // TODO: figure out if anything actually changed
                case OK:
                    sessionController.gitStatus();
                    closeWindow();
                    break;
            }
        } catch (Exception e) {
            notificationPaneController.addNotification("شيء ما خطأ.");
        }
    }

    public void handleAddButton() {
        String fileName = fileField.getText();

        // Don't allow adding the same file more than once
        if (fileNames.contains(fileName)) {
            notificationPaneController.addNotification("تمت اضافة "+fileName+" بالفعل.");
            return;
        }

        if(!fileName.equals("")){
            Label line = new Label(fileName);
            line.setWrapText(true);
            line.setId("notification");
            line.setGraphic(GlyphsDude.createIcon(FontAwesomeIcon.TIMES_CIRCLE));
            line.setOnMouseClicked(event -> {
                if (event.getTarget().equals(line.getGraphic()))
                    fileNames.remove(fileName);
                filesToCheckout.getChildren().remove(line);
            });
            fileNames.add(fileName);
            filesToCheckout.getChildren().add(0,line);
        }else {
            notificationPaneController.addNotification("عليك كتابة اسم الملف اولا");
            return;
        }
    }

    public void closeWindow() { this.stage.close(); }

    void setSessionController(SessionController controller) { this.sessionController = controller; }

    void setCommitHelper(CommitHelper commitHelper) {
        this.commitHelper = commitHelper;
        header.setText(header.getText()+commitHelper.getId().substring(0,8));
    }
}
