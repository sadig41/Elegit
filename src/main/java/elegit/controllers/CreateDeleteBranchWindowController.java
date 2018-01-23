package elegit.controllers;

import elegit.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.controlsfx.control.PopOver;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.transport.RemoteRefUpdate;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.List;

/**
 * Controller for the create/delete branch window
 */
public class CreateDeleteBranchWindowController {

    @FXML private AnchorPane anchorRoot;
    @FXML private CheckBox checkoutCheckBox;
    @FXML private TextField newBranchTextField;
    @FXML private ComboBox<LocalBranchHelper> localBranchesDropdown;
    @FXML private ComboBox<RemoteBranchHelper> remoteBranchesDropdown;
    @FXML private Button createButton;
    @FXML private Button deleteButton;
    @FXML private Button deleteButton2;
    @FXML private StackPane notificationPane;
    @FXML private NotificationController notificationPaneController;
    @FXML private TabPane tabPane;
    @FXML private Tab deleteLocalTab;
    @FXML private Tab deleteRemoteTab;
    @FXML private Tab createTab;

    private Stage stage;
    SessionModel sessionModel;
    RepoHelper repoHelper;
    private BranchModel branchModel;
    private CommitTreeModel localCommitTreeModel;
    private SessionController sessionController;

    static final Logger logger = LogManager.getLogger();

    /**
     * Initialize method called automatically by JavaFX
     */
    public void initialize() {
        sessionModel = SessionModel.getSessionModel();
        repoHelper = sessionModel.getCurrentRepoHelper();
        branchModel = repoHelper.getBranchModel();
        refreshBranchesDropDown();
        localBranchesDropdown.setPromptText("أختيار تفريعة محلية...");
        remoteBranchesDropdown.setPromptText("اختيار تفريعة بعيدة...");
        newBranchTextField.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);
        createButton.setDisable(true);
        deleteButton.setDisable(true);

        newBranchTextField.textProperty().addListener(((observable, oldValue, newValue) -> {
            if(newValue.equals("")) {
                createButton.setDisable(true);
            }else {
                createButton.setDisable(false);
            }
        }));
        newBranchTextField.setOnAction((event -> {
            createNewBranch(newBranchTextField.getText(), checkoutCheckBox.isSelected());
        }));
        localBranchesDropdown.getSelectionModel().selectedIndexProperty().addListener(((observable, oldValue, newValue) -> {
            if((int) newValue == -1) {
                deleteButton.setDisable(true);
            }else {
                deleteButton.setDisable(false);
            }
        }));
        deleteButton2.disableProperty().bind(remoteBranchesDropdown.getSelectionModel().selectedIndexProperty().lessThan(0));

        // Get the current commit tree models
        localCommitTreeModel = CommitTreeController.getCommitTreeModel();

        this.notificationPaneController.bindParentBounds(anchorRoot.heightProperty());
    }

    /**
     * Helper method to update branch dropdown
     */
    private void refreshBranchesDropDown() {
        localBranchesDropdown.setItems(FXCollections.observableArrayList(branchModel.getLocalBranchesTyped()));
        remoteBranchesDropdown.setItems(FXCollections.observableArrayList(branchModel.getRemoteBranchesTyped()));

        // Add styling to the dropdowns
        localBranchesDropdown.setCellFactory(new Callback<ListView<LocalBranchHelper>, ListCell<LocalBranchHelper>>() {
            @Override
            public ListCell<LocalBranchHelper> call(ListView<LocalBranchHelper> param) {
                return new ListCell<LocalBranchHelper>() {

                    private final Label branchName; {
                        branchName = new Label();
                    }

                    @Override protected void updateItem(LocalBranchHelper helper, boolean empty) {
                        super.updateItem(helper, empty);

                        if (helper == null || empty) { setGraphic(null); }
                        else {
                            branchName.setText(helper.getAbbrevName());
                            if (repoHelper.getBranchModel().getCurrentBranch().getRefName().equals(branchName.getText()))
                                branchName.setId("branch-current");
                            else
                                branchName.setId("branch-not-current");
                            setGraphic(branchName);
                            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                        }
                    }
                };
            }
        });
        remoteBranchesDropdown.setCellFactory(new Callback<ListView<RemoteBranchHelper>, ListCell<RemoteBranchHelper>>() {
            @Override
            public ListCell<RemoteBranchHelper> call(ListView<RemoteBranchHelper> param) {
                return new ListCell<RemoteBranchHelper>() {

                    private final Label branchName; {
                        branchName = new Label();
                    }

                    @Override protected void updateItem(RemoteBranchHelper helper, boolean empty) {
                        super.updateItem(helper, empty);

                        if (helper == null || empty) { setGraphic(null); }
                        else {
                            branchName.setText(helper.getAbbrevName());
                            try {
                                if (repoHelper.getBranchModel().getCurrentRemoteBranch() != null &&
                                        repoHelper.getBranchModel().getCurrentRemoteBranch().equals(branchName.getText()))
                                    branchName.setId("branch-current");
                                else
                                    branchName.setId("branch-not-current");
                                setGraphic(branchName);
                                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                            } catch(IOException e) {
                                // This shouldn't happen
                                setGraphic(null);
                            }
                        }
                    }
                };
            }
        });
    }

    /**
     * Shows the window
     * @param pane the AnchorPane root
     */
    void showStage(AnchorPane pane, String tab) {
        anchorRoot = pane;
        stage = new Stage();
        stage.setTitle("انشاء أو حذف تفريعة");
        stage.setScene(new Scene(anchorRoot));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setOnCloseRequest(event -> logger.info("أغلقت نافذت انشاء/حذف تفريعة"));
        stage.show();
        if(tab.equals("انشاء")) tabPane.getSelectionModel().select(createTab);
        if(tab.equals("محلي")) tabPane.getSelectionModel().select(deleteLocalTab);
        if(tab.equals("بعيد")) tabPane.getSelectionModel().select(deleteRemoteTab);
        this.notificationPaneController.setAnchor(stage);
    }

    /**
     * closes the window
     */
    public void closeWindow() {
        stage.close();
    }

    public void handleCreateBranch() {
        createNewBranch(newBranchTextField.getText(), checkoutCheckBox.isSelected());
        stage.close();
    }

    /**
     * Helper method that creates a new branch, and checks it out sometimes
     * @param branchName String
     * @param checkout boolean
     */
    private void createNewBranch(String branchName, boolean checkout) {
        Thread th = new Thread(new Task<Void>() {
            @Override
            protected Void call() {
                LocalBranchHelper newBranch = null;
                try {
                    logger.info("نقر زر تفريعة جديدة");
                    newBranch = branchModel.createNewLocalBranch(branchName);
                    if(checkout) {
                        if(newBranch != null) {
                            checkoutBranch(newBranch, sessionModel);
                        }
                    }
                    sessionController.gitStatus();

                } catch (RefAlreadyExistsException e){
                    logger.warn("تحذير: تفريعة موجودة بالفعل");
                    showRefAlreadyExistsNotification(branchName);
                } catch (InvalidRefNameException e1) {
                    logger.warn("تحذير: اسم تفريعة فاسد");
                    showInvalidBranchNameNotification();
                } catch (RefNotFoundException e1) {
                    // When a repo has no commits, you can't create branches because there
                    //  are no commits to point to. This error gets raised when git can't find
                    //  HEAD.
                    logger.warn("لايمكن انشاء تفريعة بدون الايداع في المستودع");
                    showNoCommitsYetNotification();
                } catch (GitAPIException e1) {
                    logger.warn("خطأ جيت");
                    logger.debug(e1.getStackTrace());
                    showGenericGitErrorNotification();
                    e1.printStackTrace();
                } catch (IOException e1) {
                    logger.warn("خطأ دخل/خرج غير محدد");
                    logger.debug(e1.getStackTrace());
                    showGenericErrorNotification();
                    e1.printStackTrace();
                }finally {
                    refreshBranchesDropDown();
                }
                return null;
            }
        });
        th.setDaemon(true);
        th.setName("createNewBranch");
        th.start();
    }

    /**
     * Checks out the selected local branch
     * @param selectedBranch the local branch to check out
     * @param theSessionModel the session model for resetting branch heads
     * @return true if the checkout successfully happens, false if there is an error
     */
    private boolean checkoutBranch(LocalBranchHelper selectedBranch, SessionModel theSessionModel) {
        if(selectedBranch == null) return false;
        try {
            selectedBranch.checkoutBranch();
            CommitTreeController.focusCommitInGraph(selectedBranch.getCommit());
            CommitTreeController.setBranchHeads(CommitTreeController.getCommitTreeModel(), theSessionModel.getCurrentRepoHelper());
            return true;
        } catch (JGitInternalException e){
            showJGitInternalError(e);
        } catch (CheckoutConflictException e){
            showCheckoutConflictsNotification(e.getConflictingPaths());
        } catch (GitAPIException | IOException e) {
            showGenericErrorNotification();
        }
        return false;
    }

    /**
     * Deletes the selected remote branch
     */
    public void handleDeleteRemoteBranch() {
        logger.info("نقر زر حذف تفريعات بعيدة");
        BranchHelper selectedBranch = remoteBranchesDropdown.getSelectionModel().getSelectedItem();

        deleteBranch(selectedBranch);
        refreshBranchesDropDown();
    }

    /**
     * Deletes the selected local branch
     */
    public void handleDeleteLocalBranch() {
        logger.info("نقر زر حذف تفريعات بعيدة");
        BranchHelper selectedBranch = localBranchesDropdown.getSelectionModel().getSelectedItem();

        deleteBranch(selectedBranch);
        refreshBranchesDropDown();
    }

    /**
     * Deletes the selected branch
     *
     * @param selectedBranch the branch selected to delete
     */
    public void deleteBranch(BranchHelper selectedBranch) {
        boolean authorizationSucceeded = true;
        try {
            if (selectedBranch != null) {
                RemoteRefUpdate.Status deleteStatus;

                if (selectedBranch instanceof LocalBranchHelper) {
                    this.branchModel.deleteLocalBranch((LocalBranchHelper) selectedBranch);
                    updateUser(selectedBranch.getRefName() + " deleted.", BranchModel.BranchType.LOCAL);
                }else {
                    sessionController.deleteRemoteBranch(selectedBranch, branchModel,
                                       (String message) -> updateUser(message, BranchModel.BranchType.REMOTE));
//                    final RepoHelperBuilder.AuthDialogResponse response;
//                    if (sessionController.tryCommandAgainWithHTTPAuth) {
//                        response = RepoHelperBuilder.getAuthCredentialFromDialog();
//                        repoHelper.ownerAuth =
//                                new UsernamePasswordCredentialsProvider(response.username, response.password);
//                    }
//
//                    deleteStatus = this.branchModel.deleteRemoteBranch((RemoteBranchHelper) selectedBranch);
//                    String updateMessage = selectedBranch.getRefName();
//                    // There are a number of possible cases, see JGit's documentation on RemoteRefUpdate.Status
//                    // for the full list.
//                    switch (deleteStatus) {
//                        case OK:
//                            updateMessage += " deleted.";
//                            break;
//                        case NON_EXISTING:
//                            updateMessage += " no longer\nexists on the server.";
//                        default:
//                            updateMessage += " deletion\nfailed.";
//                    }
//                    updateUser(updateMessage, BranchModel.BranchType.REMOTE);
                }
            }
        } catch (NotMergedException e) {
            logger.warn("تحذير: لايمكن حذف التفريعة لانها لم تدمج");
            Platform.runLater(() -> {
                if(PopUpWindows.showForceDeleteBranchAlert() && selectedBranch instanceof LocalBranchHelper) {
                    // If we need to force delete, then it must be a local branch
                    forceDeleteBranch((LocalBranchHelper) selectedBranch);
                }
            });
            this.showNotMergedNotification(selectedBranch);
        } catch (CannotDeleteCurrentBranchException e) {
            logger.warn("تحذير: لايمكن حذف التفريعة الحالية");
            this.showCannotDeleteBranchNotification(selectedBranch);
        } catch (TransportException e) {
            this.showNotAuthorizedNotification();
            authorizationSucceeded = false;
        } catch (GitAPIException e) {
            logger.warn("خطأ جيت");
            this.showGenericGitErrorNotificationWithBranch(selectedBranch);
//        } catch (IOException e) {
//            logger.warn("IO error");
//            this.showGenericErrorNotification();
//        } catch (CancelledAuthorizationException e) {
//            logger.warn("Cancelled authorization");
//            this.showCommandCancelledNotification();
//

        } finally {
            refreshBranchesDropDown();
            // Reset the branch heads
            CommitTreeController.setBranchHeads(localCommitTreeModel, repoHelper);
            if (authorizationSucceeded) {
                sessionController.tryCommandAgainWithHTTPAuth = false;
            } else {
                sessionController.tryCommandAgainWithHTTPAuth = true;
                deleteBranch(selectedBranch);
            }
        }
    }

    /**
     * force deletes a branch
     * @param branchToDelete LocalBranchHelper
     */
    private void forceDeleteBranch(LocalBranchHelper branchToDelete) {
        logger.info("حذف تفريعة حالية");

        try {
            if (branchToDelete != null) {
                // Local delete:
                branchModel.forceDeleteLocalBranch(branchToDelete);

                // Reset the branch heads
                CommitTreeController.setBranchHeads(localCommitTreeModel, repoHelper);

                updateUser(" deleted ");
            }
        } catch (CannotDeleteCurrentBranchException e) {
            logger.warn("تحذير: لايمكن حذف التفريعة الحالية");
            this.showCannotDeleteBranchNotification(branchToDelete);
        } catch (GitAPIException e) {
            logger.warn("خطأ جيت");
            this.showGenericGitErrorNotificationWithBranch(branchToDelete);
            e.printStackTrace();
        }finally {
            refreshBranchesDropDown();
        }
    }

    /**
     * Helper method that tells the user a local branch was created
     * @param type String
     */
    private void updateUser(String type) {
        Platform.runLater(() -> {
            Text txt = new Text(" تفريعة" + type);
            PopOver popOver = new PopOver(txt);
            popOver.setTitle("");
            popOver.show(createButton);
            popOver.detach();
            newBranchTextField.clear();
            checkoutCheckBox.setSelected(false);
            popOver.setAutoHide(true);
        });
    }

    /**
     * Helper method to show a popover about a branch type
     * @param branchType the type of branch that there is a status about
     */
    private void updateUser(String message, BranchModel.BranchType branchType) {
        Platform.runLater(() -> {
        Text txt = new Text(message);
        PopOver popOver = new PopOver(txt);
        popOver.setTitle("");
        Button buttonToShowOver;
        ComboBox<? extends BranchHelper> dropdownToReset;
        if (branchType == BranchModel.BranchType.LOCAL) {
            buttonToShowOver = deleteButton;
            dropdownToReset = localBranchesDropdown;
        } else {
            buttonToShowOver = deleteButton2;
            dropdownToReset = remoteBranchesDropdown;
        }
        System.out.println("ها انا ذا " + message);
        popOver.show(buttonToShowOver);
        System.out.println("عرضت");
        popOver.detach();
        popOver.setAutoHide(true);
        dropdownToReset.getSelectionModel().clearSelection();
        });
    }

    void setSessionController(SessionController sessionController) {
        this.sessionController = sessionController;
    }

    @FXML
    public void onEnter(ActionEvent ae) {
        System.out.println("ضغط زر الادخال!");
    }

    //**************** BEGIN ERROR NOTIFICATIONS***************************

    private void showInvalidBranchNameNotification() {
        Platform.runLater(() -> {
            logger.warn("تنبيه: اسم تفريعة غير صالح");
            notificationPaneController.addNotification("اسم تفريعة غير صالح.");
        });
    }

    private void showNoCommitsYetNotification() {
        Platform.runLater(() -> {
            logger.warn("تنبيه: لايوجد ايداعات بعد");
            notificationPaneController.addNotification("لايمكنك انشاء تفريعة لانه ليس بمستودعك ايداعات بعد. قم بالايداع أولا!");
        });
    }

    private void showGenericGitErrorNotification() {
        Platform.runLater(() -> {
            logger.warn("تنبيه: خطأ جيت");
            notificationPaneController.addNotification("نأسف يوجد خطأ جيت.");
        });
    }

    private void showGenericErrorNotification() {
        Platform.runLater(()-> {
            logger.warn("تحذير: خطأ عام.");
            notificationPaneController.addNotification("نأسف يوجد خطأ ما");
        });
    }

    private void showCannotDeleteBranchNotification(BranchHelper branch) {
        Platform.runLater(() -> {
            logger.warn("تنبيه: لايمكن حذف التفريعة الحالية");
            notificationPaneController.addNotification(String.format("نأسف لايمكن حذف %s الان.  " +
                    "حاول الانتقال لتفريعة أخري أولا", branch.getRefName()));
        });
    }

    private void showGenericGitErrorNotificationWithBranch(BranchHelper branch) {
        Platform.runLater(() -> {
            logger.warn("تنبيه: خطأ جيت علي التفريعة");
            notificationPaneController.addNotification(String.format("نأسف يوجد خطأ جيت on branch %s.", branch.getRefName()));
        });
    }

    private void showCommandCancelledNotification() {
        Platform.runLater(() -> {
            logger.warn("تنبيه: الغي الأمر");
            notificationPaneController.addNotification("الغي الأمر.");
        });
    }

    private void showNotMergedNotification(BranchHelper nonmergedBranch) {
        logger.warn("تنبيه: لم يدمج");
        notificationPaneController.addNotification("يجب دمج هذه التفريعة قبل القيام بذلك.");

        /*
        Action forceDeleteAction = new Action("Force delete", e -> {
            this.forceDeleteBranch(nonmergedBranch);
            anchorPane.hide();
        });*/
    }

    private void showJGitInternalError(JGitInternalException e) {
        Platform.runLater(()-> {
            if (e.getCause().toString().contains("LockFailedException")) {
                logger.warn("تحذير: فشل الاغلاق");
                notificationPaneController.addNotification("لايمكن إغلاق .git/index. اذا لاتوجد عملية جيت اخري تعمل، قم بالحذف يدويا ثم اغلق الملفات.");
            } else {
                logger.warn("تحذير: جي جيت عام داخلي");
                notificationPaneController.addNotification("نأسف يوجد خطأ جيت.");
            }
        });
    }

    private void showCheckoutConflictsNotification(List<String> conflictingPaths) {
        Platform.runLater(() -> {
            logger.warn("تحذير: تضارب تفحص");

            EventHandler handler = event -> sessionController.quickStashSave();
            this.notificationPaneController.addNotification("لايمكنك الانتقال لتلك التفريعة لانه سيوجد تضارب دمج. " +
                    "اخف تعديلاتك. او حل التضاربات أولا.", "stash", handler);
        });
    }

    private void showNotAuthorizedNotification() {
        Platform.runLater(() -> {
            logger.warn("تحذير: تفويض فاسد ");
            this.notificationPaneController.addNotification("معلومات التفويض التي أدحلتها لاتسمح لك بتعديل هذا المستودع . " +
                    "جرب اعادة ادخال كلمة المرور.");
        });
    }

    private void showRefAlreadyExistsNotification(String ref) {
        Platform.runLater(()-> {
            logger.warn("تحير: مرجع موجود مسبقا");
            this.notificationPaneController.addNotification(ref + " موجود بالفعل. اختر اسما مختلفا.");
        });
    }


    //**************** END ERROR NOTIFICATIONS***************************
}
