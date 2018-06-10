package elegit.controllers;

import elegit.*;
import elegit.exceptions.*;
import elegit.treefx.TreeLayout;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.*;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.controlsfx.control.CheckListView;
import org.controlsfx.control.PopOver;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.dircache.InvalidPathException;
import org.eclipse.jgit.errors.NoMergeBaseException;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * The controller for the entire session.
 */
public class SessionController {

    public Button commitButton;
    public Button pushButton;
    public Button fetchButton;
    public Button addButton;
    public Button removeButton;
    public Button checkoutFileButton;
    public Button mergeButton;
    public Button addDeleteBranchButton;
    public Button checkoutButton;
    public Button tagButton;
    public Button pushTagsButton;

    private SessionModel theModel;

    public Node root;

    public Tab workingTreePanelTab;
    public Tab indexPanelTab;
    public Tab allFilesPanelTab;

    public TabPane filesTabPane;
    public TabPane indexTabPane;

    public WorkingTreePanelView workingTreePanelView;
    public AllFilesPanelView allFilesPanelView;
    public StagedTreePanelView indexPanelView;

    public CommitTreePanelView commitTreePanelView;

    public CommitTreeModel commitTreeModel;

    public ImageView remoteImage;

    private String commitInfoNameText = "";

    public TextField tagNameField;

    public HBox currentLocalBranchHbox;
    public HBox currentRemoteTrackingBranchHbox;

    private Label currentLocalBranchLabel;
    private Label currentRemoteTrackingLabel;

    public Text browserText;
    public Text needToFetch;
    public Text branchStatusText;
//    public Text updatingText;

    public URL remoteURL;

    private DataSubmitter d;

    private BooleanProperty isWorkingTreeTabSelected;
    public static SimpleBooleanProperty anythingChecked;

    private volatile boolean isRecentRepoEventListenerBlocked = false;

    static final Logger logger = LogManager.getLogger(SessionController.class);

    public ContextMenu pushContextMenu;
    public ContextMenu commitContextMenu;
    public ContextMenu fetchContextMenu;

    public Hyperlink legendLink;

    public StackPane statusTextPane;

    private Stage mainStage;

    @FXML private AnchorPane anchorRoot;

    // Notification pane
    @FXML private StackPane notificationPane;
    @FXML private NotificationController notificationPaneController;

    // Menu Bar
    @FXML private MenuController menuController;
    @FXML private DropdownController dropdownController;
//    @FXML public CheckMenuItem loggingToggle;
//    @FXML public CheckMenuItem commitSortToggle;
//    @FXML private MenuItem gitIgnoreMenuItem;
//    @FXML private Menu repoMenu;
//    @FXML private MenuItem cloneMenuItem;
//    @FXML private MenuItem createBranchMenuItem;
//    @FXML private MenuItem commitNormalMenuItem;
//    @FXML private MenuItem normalFetchMenuItem;
//    @FXML private MenuItem pullMenuItem;
//    @FXML private MenuItem pushMenuItem;
//    @FXML private MenuItem stashMenuItem1;
//    @FXML private MenuItem stashMenuItem2;

    // Commit Info Box
    @FXML public CommitInfoController commitInfoController;
    @FXML public VBox infoTagBox;


    boolean tryCommandAgainWithHTTPAuth;
    private boolean isGitStatusDone;
    private boolean isTimerDone;

    Preferences preferences;
    private static final String LOGGING_LEVEL_KEY="LOGGING_LEVEL";

    /**
     * Initializes the environment by obtaining the model
     * and putting the views on display.
     *
     * This method is automatically called by JavaFX.
     */
    public void initialize() {
        // Creates the SessionModel
        this.theModel = SessionModel.getSessionModel();

        // Creates a DataSubmitter for logging
        d = new DataSubmitter();

        // Gives other controllers acccess to this one
        CommitTreeController.sessionController = this;
        CommitController.sessionController = this;
        menuController.setSessionController(this);
        dropdownController.setSessionController(this);
        commitInfoController.setSessionController(this);


        // Creates the commit tree model
        this.commitTreeModel = new CommitTreeModel(this.theModel, this.commitTreePanelView);
        CommitTreeController.commitTreeModel = this.commitTreeModel;

        // Passes theModel to panel views
        this.workingTreePanelView.setSessionModel(this.theModel);
        this.allFilesPanelView.setSessionModel(this.theModel);
        this.indexPanelView.setSessionModel(this.theModel);

        this.initializeLayoutParameters();

        this.initButtons();
        this.setButtonIconsAndTooltips();
        this.setButtonsDisabled(true);
        this.initWorkingTreePanelTab();
        // SLOW
        this.theModel.loadRecentRepoHelpersFromStoredPathStrings();
        this.theModel.loadMostRecentRepoHelper();

        // SLOW
        this.initPanelViews();
        this.updateUIEnabledStatus();
        this.setRecentReposDropdownToCurrentRepo();
        this.refreshRecentReposInDropdown();

        this.initRepositoryMonitor();

        this.initStatusText();

        //this.initMenuBarShortcuts();

        this.notificationPaneController.bindParentBounds(anchorRoot.heightProperty());

        VBox.setVgrow(filesTabPane, Priority.ALWAYS);

        // if there are ملفات متضاربة on startup, watches them for changes
        try {
            ConflictingFileWatcher.watchConflictingFiles(theModel.getCurrentRepoHelper());
        } catch (GitAPIException | IOException e) {
            e.printStackTrace();
        }

        tryCommandAgainWithHTTPAuth = false;

        this.preferences = Preferences.userNodeForPackage(this.getClass());
    }

    /**
     * Helper method that passes the main stage to session controller
     * @param stage Stage
     */
    public void setStage(Stage stage) {
        this.mainStage = stage;
        notificationPaneController.setAnchor(mainStage);
    }

    /**
     * Helper method that creates the labels for the branch names
     */
    private void initStatusText() {
//        updatingText.setVisible(false);
//        branchStatusText.visibleProperty().bind(updatingText.visibleProperty().not());

        currentRemoteTrackingLabel = new Label("N/A");
        currentLocalBranchLabel = new Label("N/A");
        initCellLabel(currentLocalBranchLabel, currentLocalBranchHbox);
        initCellLabel(currentRemoteTrackingLabel, currentRemoteTrackingBranchHbox);

        updateStatusText();
    }

    /**
     * Helper method that sets style for cell labels
     * @param label the label that contains the branch name
     * @param hbox  the hbox that contains the label
     */
    private void initCellLabel(Label label, HBox hbox){
        hbox.getStyleClass().clear();
        hbox.getStyleClass().add("cell-label-box");
        label.getStyleClass().clear();
        label.getStyleClass().add("cell-label");
        label.setId("current");
        hbox.setId("current");
        hbox.getChildren().add(label);
    }

    /**
     * Helper method for adding tooltips to nodes
     * @param n the node to attach a tooltip to
     * @param text the text for the tooltip
     */
    private void addToolTip(Node n, String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(350);
        Tooltip.install(n, tooltip);
    }

    /**
     * Helper method to update the current local branch, remote tracking branch and
     * whether or not there are remote changes to fetch
     */
    private void updateStatusText(){
        if (this.theModel.getCurrentRepoHelper()==null) return;
        boolean update;

        update = RepositoryMonitor.hasFoundNewRemoteChanges.get();
        String fetchText = update ? "تعديلات جديدة لجلبها" : "محدثة";
        Color fetchColor = update ? Color.FIREBRICK : Color.FORESTGREEN;
        needToFetch.setText(fetchText);
        needToFetch.setFont(new Font(15));
        needToFetch.setFill(fetchColor);

        BranchHelper localBranch = this.theModel.getCurrentRepoHelper().getBranchModel().getCurrentBranch();
        update = !localBranch.getAbbrevName().equals(currentLocalBranchLabel.getText());
        if (update) {
            Platform.runLater(() -> {
                currentLocalBranchLabel.setText(localBranch.getAbbrevName());
                currentLocalBranchLabel.setOnMouseClicked((event -> CommitTreeController.focusCommitInGraph(localBranch.getCommit())));
                addToolTip(currentLocalBranchHbox, localBranch.getRefName());
            });
        }

        String remoteBranch = "N/A";
        String remoteBranchFull = "N/A";
        CommitHelper remoteHead = null;
        try {
            remoteBranch = this.theModel.getCurrentRepoHelper().getBranchModel().getCurrentRemoteAbbrevBranch();
            remoteHead = this.theModel.getCurrentRepoHelper().getBranchModel().getCurrentRemoteBranchHead();
            remoteBranchFull = this.theModel.getCurrentRepoHelper().getBranchModel().getCurrentRemoteBranch();
        } catch (IOException e) {
            this.showGenericErrorNotification();
        }
        if (remoteBranch==null) {
            remoteBranch = "N/A";
            remoteBranchFull = "N/A";
        }

        String remoteBranchFinal = remoteBranch;
        String remoteBranchFullFinal = remoteBranchFull;
        update = !remoteBranch.equals(currentRemoteTrackingLabel.getText());
        if (update) {
            CommitHelper finalRemoteHead = remoteHead;
            Platform.runLater(() -> {
                currentRemoteTrackingLabel.setText(remoteBranchFinal);
                if (finalRemoteHead != null)
                    currentRemoteTrackingLabel.setOnMouseClicked((event -> CommitTreeController.focusCommitInGraph(finalRemoteHead)));
                addToolTip(currentRemoteTrackingBranchHbox, remoteBranchFullFinal);
            });
        }

        // Ahead/behind count
        int ahead=0, behind=0;
        try {
            ahead = this.theModel.getCurrentRepoHelper().getAheadCount();
            behind = this.theModel.getCurrentRepoHelper().getBehindCount();
        } catch (IOException e) {
            this.showGenericErrorNotification();
        }
        String statusText="محدثة.";
        if (ahead >0) {
            statusText= currentLocalBranchLabel.getText() + " متقدم علي " + currentRemoteTrackingLabel.getText() + " بعدد " + ahead + " ايداع";
            if (ahead > 2 && ahead < 10)
                statusText+="ات";
            if (behind > 0) {
                statusText += "\nومتأخر بعدد " + behind + " ايداع";
                if (behind > 2 && behind < 10)
                    statusText+="ات";
            }
            statusText+=".";
        } else if (behind > 0) {
            statusText = currentLocalBranchLabel.getText() + " متأخر بعدد " + currentRemoteTrackingLabel.getText() + behind + " ايداع";
            if (behind > 2 && behind < 10)
                statusText+="ات";
            statusText+=".";
        }
        update = !statusText.equals(branchStatusText.getText());
        Color statusColor = statusText.equals("محدثة.") ? Color.FORESTGREEN : Color.FIREBRICK;
        if (update) {
            branchStatusText.setText(statusText);
            branchStatusText.setFill(statusColor);
        }
    }

    /**
     * Initializes the workingTreePanelTab
     */
    private void initWorkingTreePanelTab() {
        isWorkingTreeTabSelected = new SimpleBooleanProperty(true);
        isWorkingTreeTabSelected.bind(workingTreePanelTab.selectedProperty());
        workingTreePanelTab.getTabPane().getSelectionModel().select(workingTreePanelTab);
    }

    /**
     * Initializes the repository monitor
     */
    private void initRepositoryMonitor() {
        RepositoryMonitor.startWatching(theModel, this);
        RepositoryMonitor.hasFoundNewRemoteChanges.addListener((observable, oldValue, newValue) -> {
            if(newValue) updateStatusText();
        });
    }

    /**
     * Sets up the layout parameters for things that cannot be set in FXML
     */
    private void initializeLayoutParameters(){
        // Set minimum/maximum sizes for buttons
        //openRepoDirButton.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);
        commitButton.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);
        addButton.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);
        checkoutFileButton.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);
        removeButton.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);
        addDeleteBranchButton.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);
        mergeButton.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);
        checkoutButton.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);
        pushButton.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);
        pushTagsButton.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);
        fetchButton.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);

        // Set minimum sizes for other fields and views
        workingTreePanelView.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);
        allFilesPanelView.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);
        indexPanelView.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);
        tagNameField.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);
    }

    /**
     * Adds context menus and properties to buttons
     */
    private void initButtons() {
        anythingChecked = new SimpleBooleanProperty(false);
        checkoutFileButton.disableProperty().bind(anythingChecked.not());
        addButton.disableProperty().bind(anythingChecked.not());
        removeButton.disableProperty().bind(anythingChecked.not());

        legendLink.setFont(new Font(12));

        pushButton.setOnMouseClicked(event -> {
            if(event.getButton() == MouseButton.SECONDARY){
                if(pushContextMenu != null){
                    pushContextMenu.show(pushButton, event.getScreenX(), event.getScreenY());
                }
            }
            event.consume();
        });

        commitButton.setOnMouseClicked(event -> {
            if(event.getButton() == MouseButton.SECONDARY){
                if(commitContextMenu != null){
                    commitContextMenu.show(commitButton, event.getScreenX(), event.getScreenY());
                }
            }
            event.consume();
        });

        fetchButton.setOnMouseClicked(event -> {
            if(event.getButton() == MouseButton.SECONDARY){
                if(fetchContextMenu != null){
                    fetchContextMenu.show(fetchButton, event.getScreenX(), event.getScreenY());
                }
            }
            event.consume();
        });

        tagNameField.setOnKeyTyped(event -> {
            if (event.getCharacter().equals("\r")) handleTagButton();
        });
    }

    /**
     * Adds graphics and tooltips to the buttons
     */
    private void setButtonIconsAndTooltips() {
        this.commitButton.setTooltip(new Tooltip(
                "ضع الملفات المحددة بالمستودع المحل"
        ));
        this.addButton.setTooltip(new Tooltip(
                "ادرج التعديلات الي الملفات المحددة"
        ));
        this.checkoutFileButton.setTooltip(new Tooltip(
                "تفحص ملفات من الفهرس (تجاهل كل التعديلات غير المدرجة)"
        ));
        this.removeButton.setTooltip(new Tooltip(
                "امسح الملفات المحددة وأزلها من جيت"
        ));
        this.fetchButton.setTooltip(new Tooltip(
                "نزل ملفات من مستودع أخر للمستودع البعيد"
        ));
        this.pushButton.setTooltip(new Tooltip(
                "تحديث مستودع بعيد بتعديلات محلية،\n انقر بالزر الأيمن للخيارات المتقدمة"
        ));

        dropdownController.loadNewRepoButton.setTooltip(new Tooltip(
                "تحميل مستودع جديد"
        ));
        this.mergeButton.setTooltip(new Tooltip(
                "دمج ايداعين سويا"
        ));
    }

    /**
     * Initializes each panel of the view
     */
    private synchronized void initPanelViews() {
        try {
            workingTreePanelView.drawDirectoryView();
            allFilesPanelView.drawDirectoryView();
            indexPanelView.drawDirectoryView();
            commitTreeModel.init();
            this.setBrowserURL();
        } catch (GitAPIException | IOException e) {
            showGenericErrorNotification();
        }
    }

    /**
     * Populates the browser image with the remote URL
     */
    private void setBrowserURL() {
        try {
            RepoHelper currentRepoHelper = this.theModel.getCurrentRepoHelper();
            if (currentRepoHelper == null) throw new NoRepoLoadedException();
            if (!currentRepoHelper.exists()) throw new MissingRepoException();
            List<String> remoteURLs = currentRepoHelper.getLinkedRemoteRepoURLs();
            if(remoteURLs.size() == 0){
                this.showNoRemoteNotification();
                return;
            }
            String URLString = remoteURLs.get(0);

            if (URLString != null) {
                if(URLString.contains("@")){
                    URLString = "https://"+URLString.replace(":","/").split("@")[1];
                }
                try {
                    remoteURL = new URL(URLString);
                    browserText.setText(remoteURL.getHost());
                } catch (MalformedURLException e) {
                    browserText.setText(URLString);
                }
            }
            Tooltip URLTooltip = new Tooltip(URLString);
            Tooltip.install(browserText, URLTooltip);

            browserText.setFill(Color.DARKCYAN);
            browserText.setUnderline(true);
        }
        catch(MissingRepoException e) {
            this.showMissingRepoNotification();
            this.setButtonsDisabled(true);
            this.refreshRecentReposInDropdown();
        }catch(NoRepoLoadedException e) {
            this.setButtonsDisabled(true);
        }
    }

    /**
     * A helper method for enabling/disabling buttons.
     *
     * @param disable a boolean for whether or not to disable the buttons.
     */
    void setButtonsDisabled(boolean disable) {
        Platform.runLater(() -> {
            dropdownController.openRepoDirButton.setDisable(disable);
            tagButton.setDisable(disable);
            commitButton.setDisable(disable);
            pushButton.setDisable(disable);
            fetchButton.setDisable(disable);
            remoteImage.setVisible(!disable);
            browserText.setVisible(!disable);
            workingTreePanelTab.setDisable(disable);
            allFilesPanelTab.setDisable(disable);
            indexPanelTab.setDisable(disable);
            dropdownController.removeRecentReposButton.setDisable(disable);
            dropdownController.repoDropdownSelector.setDisable(disable);
            addDeleteBranchButton.setDisable(disable);
            checkoutButton.setDisable(disable);
            mergeButton.setDisable(disable);
            pushTagsButton.setDisable(disable);
            needToFetch.setVisible(!disable);
            currentLocalBranchHbox.setVisible(!disable);
            currentRemoteTrackingBranchHbox.setVisible(!disable);
            statusTextPane.setVisible(!disable);
            updateMenuBarEnabledStatus(disable);
        });

        root.setOnMouseClicked(event -> {
            if (disable) showNoRepoLoadedNotification();
            if (this.notificationPaneController.isListPaneVisible()) this.notificationPaneController.toggleNotificationList();
        });
    }


    /**
     * Helper method for disabling the menu bar
     */
    private void updateMenuBarEnabledStatus(boolean disable) {
        menuController.repoMenu.setDisable(disable);
        menuController.gitIgnoreMenuItem.setDisable(disable);
    }

    /**
     * A helper helper method to enable or disable buttons/UI elements
     * depending on whether there is a repo open for the buttons to
     * interact with.
     */
    private void updateUIEnabledStatus() {
        if (this.theModel.getCurrentRepoHelper() == null && this.theModel.getAllRepoHelpers().size() >= 0) {
            // (There's no repo for buttons to interact with, but there are repos in the menu bar)
            setButtonsDisabled(true);
        } else {
            setButtonsDisabled(false);
        }
    }

     /**
      * Called when the loadNewRepoButton gets pushed, shows a menu of options
     */
    public void handleLoadNewRepoButton() {
        dropdownController.newRepoOptionsMenu.show(dropdownController.loadNewRepoButton, Side.BOTTOM ,0, 0);
    }

    /**
     * Called when the "Load existing repository" option is clicked
     */
    public void handleLoadExistingRepoOption() {
        handleLoadRepoMenuItem(new ExistingRepoHelperBuilder(this.theModel));
    }

    /**
     * Called when the "Clone repository" option is clicked
     */
    public void handleCloneNewRepoOption() {
        handleLoadRepoMenuItem(new ClonedRepoHelperBuilder(this.theModel));
    }

    /**
     * Called when a selection is made from the 'Load New Repository' menu. Creates a new repository
     * using the given builder and updates the UI
     * @param builder the builder to use to create a new repository
     */
    private synchronized void handleLoadRepoMenuItem(RepoHelperBuilder builder){
        try{
            RepoHelper repoHelper = builder.getRepoHelperFromDialogs();
            if(theModel.getCurrentRepoHelper() != null && repoHelper.localPath.equals(theModel.getCurrentRepoHelper().localPath)) {
                showSameRepoLoadedNotification();
                return;
            }

            RepositoryMonitor.pause();
            BusyWindow.show();
            BusyWindow.setLoadingText("تحميل المستودع...");
            Thread th = new Thread(new Task<Void>(){
                @Override
                protected Void call() {
                    try {
                        TreeLayout.stopMovingCells();

                        refreshRecentReposInDropdown();
                        theModel.openRepoFromHelper(repoHelper);
                        setRecentReposDropdownToCurrentRepo();

                        Platform.runLater(() -> {
                            initPanelViews();
                            updateUIEnabledStatus();
                        });
                    } catch(BackingStoreException | ClassNotFoundException e) {
                        // These should only occur when the recent repo information
                        // fails to be loaded or stored, respectively
                        // Should be ok to silently fail
                    } catch (MissingRepoException e) {
                        showMissingRepoNotification();
                        refreshRecentReposInDropdown();
                    } catch (IOException e) {
                        // Somehow, the repository failed to get properly loaded
                        // TODO: better error message?
                        showRepoWasNotLoadedNotification();
                    } catch(Exception e) {
                        showGenericErrorNotification();
                        e.printStackTrace();
                    } finally{
                        RepositoryMonitor.unpause();
                        BusyWindow.hide();
                    }
                    return null;
                }
            });
            th.setDaemon(true);
            th.setName("Loading existing/cloning repository");
            th.start();
        } catch(InvalidPathException e) {
            showRepoWasNotLoadedNotification();
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            showInvalidRepoNotification();
        } catch(JGitInternalException e){
            showNonEmptyFolderNotification(() -> handleLoadRepoMenuItem(builder));
        } catch(InvalidRemoteException e){
            showInvalidRemoteNotification(() -> handleLoadRepoMenuItem(builder));
        } catch(TransportException e){
            showTransportExceptionNotification(e);
        } catch (NoRepoSelectedException | CancelledAuthorizationException e) {
            // The user pressed cancel on the dialog box, or
            // the user pressed cancel on the authorize dialog box. Do nothing!
        } catch(IOException | GitAPIException e) {
            // Somehow, the repository failed to get properly loaded
            // TODO: better error message?
            showRepoWasNotLoadedNotification();
        }
    }

    /**
     * Gets the current RepoHelper and sets it as the selected value of the dropdown.
     */
    @FXML
    private void setRecentReposDropdownToCurrentRepo() {
        Platform.runLater(() -> {
            synchronized (this) {
                isRecentRepoEventListenerBlocked = true;
                RepoHelper currentRepo = this.theModel.getCurrentRepoHelper();
                dropdownController.repoDropdownSelector.setValue(currentRepo);
                isRecentRepoEventListenerBlocked = false;
            }
        });
    }

    /**
     * Adds all the model's RepoHelpers to the dropdown
     */
    @FXML
    private void refreshRecentReposInDropdown() {
        Platform.runLater(() -> {
            synchronized (this) {
                isRecentRepoEventListenerBlocked = true;
                List<RepoHelper> repoHelpers = this.theModel.getAllRepoHelpers();
                dropdownController.repoDropdownSelector.setItems(FXCollections.observableArrayList(repoHelpers));
                isRecentRepoEventListenerBlocked = false;
            }
        });
    }

    /**
     * Loads the given repository and updates the UI accordingly.
     * @param repoHelper the repository to open
     */
    private synchronized void handleRecentRepoMenuItem(RepoHelper repoHelper){
        if(isRecentRepoEventListenerBlocked || repoHelper == null) return;

        this.notificationPaneController.clearAllNotifications();
        logger.info("تبديل مستودعات");
        RepositoryMonitor.pause();
        BusyWindow.show();
        BusyWindow.setLoadingText("فتح المستودع...");
        Thread th = new Thread(new Task<Void>(){
            @Override
            protected Void call() throws Exception{
                try {
                    theModel.openRepoFromHelper(repoHelper);

                    Platform.runLater(() -> {
                        initPanelViews();
                        updateUIEnabledStatus();
                    });
                } catch (IOException e) {
                    // Somehow, the repository failed to get properly loaded
                    // TODO: better error message?
                    showRepoWasNotLoadedNotification();
                } catch(MissingRepoException e){
                    showMissingRepoNotification();
                    refreshRecentReposInDropdown();
                } catch (BackingStoreException | ClassNotFoundException e) {
                    // These should only occur when the recent repo information
                    // fails to be loaded or stored, respectively
                    // Should be ok to silently fail
                } catch(Exception e) {
                    showGenericErrorNotification();
                    e.printStackTrace();
                } finally{
                    RepositoryMonitor.unpause();
                    BusyWindow.hide();
                }
                return null;
            }
        });
        th.setDaemon(true);
        th.setName("Open repository from recent list");
        th.start();
    }

    /**
     * A helper method that grabs the currently selected repo in the repo dropdown
     * and loads it using the handleRecentRepoMenuItem(...) method.
     */
    public void loadSelectedRepo() {
        if (theModel.getAllRepoHelpers().size() == 0) return;
        RepoHelper selectedRepoHelper = dropdownController.repoDropdownSelector.getValue();
        this.handleRecentRepoMenuItem(selectedRepoHelper);
    }

    /**
     * Adds all files that are selected if they can be added
     */
    public void handleAddButton() {
        try {
            logger.info("نقر زر اضافة");
            if(this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();
            if(!this.theModel.getCurrentRepoHelper().exists()) throw new MissingRepoException();

            if(!workingTreePanelView.isAnyFileSelected()) throw new NoFilesSelectedToAddException();
            if(workingTreePanelView.isAnyFileStagedSelected()) throw new StagedFileCheckedException();

            BusyWindow.show();
            BusyWindow.setLoadingText("اضافة...");
            Thread th = new Thread(new Task<Void>(){
                @Override
                protected Void call() {
                    try{
                        ArrayList<Path> filePathsToAdd = new ArrayList<>();
                        ArrayList<Path> filePathsToRemove = new ArrayList<>();

                        // Try to add all files, throw exception if there are ones that can't be added
                        if (workingTreePanelView.isSelectAllChecked()) {
                            filePathsToAdd.add(Paths.get("."));
                        }
                        else {
                            for (RepoFile checkedFile : workingTreePanelView.getCheckedFilesInDirectory()) {
                                if (checkedFile.canAdd()) {
                                    filePathsToAdd.add(checkedFile.getFilePath());
                                } else if (checkedFile instanceof MissingRepoFile) {
                                    // JGit does not support adding missing files, instead remove them
                                    filePathsToRemove.add(checkedFile.getFilePath());
                                }
                                else {
                                    throw new UnableToAddException(checkedFile.filePath.toString());
                                }
                            }
                        }

                        if (filePathsToAdd.size() > 0)
                            theModel.getCurrentRepoHelper().addFilePaths(filePathsToAdd);
                        if (filePathsToRemove.size() > 0)
                            theModel.getCurrentRepoHelper().removeFilePaths(filePathsToRemove);
                        gitStatus();

                    } catch (JGitInternalException e){
                        showJGitInternalError(e);
                    } catch (UnableToAddException e) {
                        showCannotAddFileNotification(e.filename);
                    } catch (GitAPIException | IOException e) {
                        showGenericErrorNotification();
                    } finally {
                        BusyWindow.hide();
                    }
                    return null;
                }
            });
            th.setDaemon(true);
            th.setName("Git add");
            th.start();
        } catch (NoFilesSelectedToAddException e) {
            this.showNoFilesSelectedForAddNotification();
        } catch (NoRepoLoadedException e) {
            this.showNoRepoLoadedNotification();
        } catch (MissingRepoException e) {
            this.showMissingRepoNotification();
        } catch (StagedFileCheckedException e) {
            this.showStagedFilesSelectedNotification();
        }
    }

    /**
     * Removes all files from staging area that are selected if they can be removed
     */
    public void handleRemoveButton() {
        try {
            logger.info("نقر زر ازالة");
            if(this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();
            if(!this.theModel.getCurrentRepoHelper().exists()) throw new MissingRepoException();

            if(!workingTreePanelView.isAnyFileSelected()) throw new NoFilesSelectedToRemoveException();

            BusyWindow.show();
            BusyWindow.setLoadingText("ازالة...");
            Thread th = new Thread(new Task<Void>(){
                @Override
                protected Void call() {
                    try{
                        ArrayList<Path> filePathsToRemove = new ArrayList<>();
                        // Try to remove all files, throw exception if there are ones that can't be added
                        for(RepoFile checkedFile : workingTreePanelView.getCheckedFilesInDirectory()) {
                            if (checkedFile.canRemove())
                                filePathsToRemove.add(checkedFile.getFilePath());
                            else
                                throw new UnableToRemoveException(checkedFile.filePath.toString());
                        }

                        theModel.getCurrentRepoHelper().removeFilePaths(filePathsToRemove);
                        gitStatus();

                    } catch(JGitInternalException e){
                        showJGitInternalError(e);
                    } catch (UnableToRemoveException e) {
                        showCannotRemoveFileNotification(e.filename);
                    } catch (GitAPIException e) {
                        showGenericErrorNotification();
                    } finally {
                        BusyWindow.hide();
                    }
                    return null;
                }
            });
            th.setDaemon(true);
            th.setName("Git rm");
            th.start();
        } catch (NoFilesSelectedToRemoveException e) {
            this.showNoFilesSelectedForRemoveNotification();
        } catch (NoRepoLoadedException e) {
            this.showNoRepoLoadedNotification();
        } catch (MissingRepoException e) {
            this.showMissingRepoNotification();
        }
    }

    /**
     * Basic handler for the checkout button. Just checks out the given file
     * from the index
     *
     * @param filePath the path of the file to checkout from the index
     */
    public void handleCheckoutButton(Path filePath) {
        try {
            logger.info("نقر زر تفحص ملف");
            if (! PopUpWindows.showCheckoutAlert()) throw new CancelledDialogueException();
            if(this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();
            if(!this.theModel.getCurrentRepoHelper().exists()) throw new MissingRepoException();
            theModel.getCurrentRepoHelper().checkoutFile(filePath);
        } catch (NoRepoLoadedException e) {
            showNoRepoLoadedNotification();
        } catch (MissingRepoException e) {
            showMissingRepoNotification();
        } catch (GitAPIException e) {
            showGenericErrorNotification();
        } catch (CancelledDialogueException e) {
            // Do nothing if the dialogue was cancelled.
        }
    }

    /**
     * Handler for the checkout button
     */
    public void handleCheckoutButton() {
        try {
            logger.info("نقر زر تفحص");
            if(this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();
            if(!this.theModel.getCurrentRepoHelper().exists()) throw new MissingRepoException();

            if(!workingTreePanelView.isAnyFileSelected()) throw new NoFilesSelectedToAddException();
            if (! PopUpWindows.showCheckoutAlert()) throw new CancelledDialogueException();
            ArrayList<Path> filePathsToCheckout = new ArrayList<>();
            // Try to add all files, throw exception if there are ones that can't be added
            for(RepoFile checkedFile : workingTreePanelView.getCheckedFilesInDirectory()) {
                filePathsToCheckout.add(checkedFile.getFilePath());
            }
            theModel.getCurrentRepoHelper().checkoutFiles(filePathsToCheckout);
            gitStatus();
        } catch (NoFilesSelectedToAddException e) {
            this.showNoFilesSelectedForAddNotification();
        } catch (NoRepoLoadedException e) {
            this.showNoRepoLoadedNotification();
        } catch (MissingRepoException e) {
            this.showMissingRepoNotification();
        } catch (GitAPIException e) {
            this.showGenericErrorNotification();
        } catch (CancelledDialogueException e) {
            // Do nothing
        }
    }


    /**
     * Shows the تفحص ملفات dialogue for a given commit
     *
     * @param commitHelper the commit to تفحص ملفات from
     */
    public void handleCheckoutFilesButton(CommitHelper commitHelper) {
        try{
            logger.info("نقر زر تفحص ملفات من ايداع");
            if(this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();

            logger.info("فتحت نافذة تفحص ملفات");
            // Create and display the Stage:
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/elegit/fxml/CheckoutFiles.fxml"));
            fxmlLoader.load();
            CheckoutFilesController checkoutFilesController = fxmlLoader.getController();
            checkoutFilesController.setSessionController(this);
            checkoutFilesController.setCommitHelper(commitHelper);
            AnchorPane fxmlRoot = fxmlLoader.getRoot();
            checkoutFilesController.showStage(fxmlRoot);
        }catch(IOException e){
            this.showGenericErrorNotification();
            e.printStackTrace();
        }catch(NoRepoLoadedException e){
            this.showNoRepoLoadedNotification();
            setButtonsDisabled(true);
        }
    }

    private enum CommitType{NORMAL, ALL}

    public void handleCommitAll() {
        handleCommitButton(CommitType.ALL);
    }

    public void handleCommitNormal() {
        handleCommitButton(CommitType.NORMAL);
    }

    /**
     * Commits all files that have been staged with the message
     */
    public void handleCommitButton(CommitType type) {
        try {
            logger.info("نقر زر ايداع");
            if(this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();
            if(!this.theModel.getCurrentRepoHelper().exists()) throw new MissingRepoException();

            if(!workingTreePanelView.isAnyFileStaged() && type.equals(CommitType.NORMAL)) throw new NoFilesStagedForCommitException();

            if(type.equals(CommitType.NORMAL)) {
                commitNormal();
            }else {
                commitAll();
            }
        } catch(NoRepoLoadedException e){
            this.showNoRepoLoadedNotification();
            setButtonsDisabled(true);
        } catch(MissingRepoException e){
            this.showMissingRepoNotification();
            setButtonsDisabled(true);
            refreshRecentReposInDropdown();
        } catch(NoFilesStagedForCommitException e){
            this.showNoFilesStagedForCommitNotification();
        } catch(IOException e){
            showGenericErrorNotification();
            e.printStackTrace();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private void commitAll() {
        String message = PopUpWindows.getCommitMessage();
        if(message.equals("cancel")) return;

        BusyWindow.show();
        BusyWindow.setLoadingText("ايداع الكل...");

        Thread th = new Thread(new Task<Void>() {
            @Override
            protected Void call() {
                try {
                    theModel.getCurrentRepoHelper().commitAll(message);
                    gitStatus();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    BusyWindow.hide();
                }
                return null;
            }
        });
        th.setDaemon(true);
        th.setName("Git commit all");
        th.start();
    }

    private void commitNormal() throws IOException {
        logger.info("فتحت نافذة مدير الايداع");
        // Create and display the Stage:
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/elegit/fxml/CommitView.fxml"));
        fxmlLoader.load();
        CommitController commitController = fxmlLoader.getController();
        commitController.isClosed.addListener((observable, oldValue, newValue) -> {
            if (!oldValue && newValue)
                gitStatus();
        });
        GridPane fxmlRoot = fxmlLoader.getRoot();
        commitController.showStage(fxmlRoot);
    }


    /**
     * Checks things are ready for a tag, then performs a git-tag
     *
     */
    public void handleTagButton() {
        logger.info("نقر زر تزييل");
        try {
            if(this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();
            if(!this.theModel.getCurrentRepoHelper().exists()) throw new MissingRepoException();

            String tagName = tagNameField.getText();
            if (theModel.getCurrentRepoHelper().getTagModel().getTag(tagName) != null) {
                throw new TagNameExistsException();
            }

            if(tagName.length() == 0) throw new NoTagNameException();

            Thread th = new Thread(new Task<Void>(){
                @Override
                protected Void call() {
                    try {
                        theModel.getCurrentRepoHelper().getTagModel().tag(tagName, commitInfoNameText);

                        // Now clear the tag text and a view reload ( or `git status`) to show that something happened
                        tagNameField.clear();
                        gitStatus();
                    } catch (JGitInternalException e) {
                        showJGitInternalError(e);
                    } catch (MissingRepoException e) {
                        showMissingRepoNotification();
                        setButtonsDisabled(true);
                        refreshRecentReposInDropdown();
                    } catch (InvalidTagNameException e) {
                        showInvalidTagNameNotification(tagName);
                    }catch (TransportException e) {
                        showTransportExceptionNotification(e);
                    } catch(GitAPIException e){
                        // خطأ جيت
                        showGenericErrorNotification();
                        e.printStackTrace();
                    } catch(TagNameExistsException e){
                        showTagExistsNotification();
                    }
                    catch(Exception e) {
                        showGenericErrorNotification();
                        e.printStackTrace();
                    }
                    tagNameField.setText("");
                    clearSelectedCommit();
                    selectCommit(theModel.getCurrentRepoHelper().getTagModel().getTag(tagName).getCommitId());

                    return null;
                }
            });
            th.setDaemon(true);
            th.setName("Git tag");
            th.start();
        } catch(NoRepoLoadedException e){
            this.showNoRepoLoadedNotification();
            setButtonsDisabled(true);
        } catch(MissingRepoException e){
            this.showMissingRepoNotification();
            setButtonsDisabled(true);
            refreshRecentReposInDropdown();
        } catch(NoTagNameException e){
            this.showNoTagNameNotification();
        } catch(TagNameExistsException e) {
            this.showTagExistsNotification();
        }
    }

    public enum PushType {BRANCH, ALL}

    public void handlePushButton() {
        pushBranchOrAllSetup(PushType.BRANCH);
    }

    public void handlePushAllButton() {
        pushBranchOrAllSetup(PushType.ALL);
    }

    // Set up the push command. Involves querying the user to see if remote branches should be made.
    // This query is done once.
    private void pushBranchOrAllSetup(PushType pushType)  {

        try {

            logger.info("نقر زر دفع");

            if (this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();
            if (pushType == PushType.BRANCH &&
                !this.theModel.getCurrentRepoHelper().canPush()) throw new NoCommitsToPushException();

            RepoHelper helper = theModel.getCurrentRepoHelper();
            final PushCommand push;
            if (pushType == PushType.BRANCH) {
                push = helper.prepareToPushCurrentBranch(false);
            } else if (pushType == PushType.ALL) {
                push = helper.prepareToPushAll();
            } else {
                push = null;
                assert false : "PushType enum case not handled";
            }

            pushBranchOrAll(pushType, push);

        } catch (NoRepoLoadedException e) {
            this.showNoRepoLoadedNotification();
            setButtonsDisabled(true);
        } catch (NoCommitsToPushException e) {
            this.showNoCommitsToPushNotification();
        } catch (IOException e) {
            this.showGenericErrorNotification();
        } catch (PushToAheadRemoteError pushToAheadRemoteError) {
            pushToAheadRemoteError.printStackTrace();
        } catch (MissingRepoException e) {
            showMissingRepoNotification();
            setButtonsDisabled(true);
            refreshRecentReposInDropdown();
        } catch (GitAPIException e) {
            showGenericErrorNotification();
            e.printStackTrace();
        }

    }

    /**
     * Performs a `git push` on either current branch or all branches, depending on enum parameter.
     * This is recursively re-called if authentication fails.
     */
    public void pushBranchOrAll(PushType pushType, PushCommand push) {
        try {
            final RepoHelperBuilder.AuthDialogResponse credentialResponse = askUserForCredentials();

            BusyWindow.show();
            BusyWindow.setLoadingText("يدفع...");
            Thread th = new Thread(new Task<Void>(){
                @Override
                protected Void call() {
                    tryCommandAgainWithHTTPAuth = false;
                    try {
                        pushBranchOrAllDetails(credentialResponse, pushType, push);
                    } catch (TransportException e) {
                        determineIfTryAgain(e);
                    } finally {
                        BusyWindow.hide();
                    }

                    if (tryCommandAgainWithHTTPAuth) {
                        Platform.runLater(() -> {
                            pushBranchOrAll(pushType, push);
                        });
                    }

                    return null;
                }
            });
            th.setDaemon(true);
            th.setName("Git push");
            th.start();
        } catch (CancelledAuthorizationException e) {
            this.showCommandCancelledNotification();
        }
    }

    private void determineIfTryAgain(TransportException e) {
        showTransportExceptionNotification(e);

        // Don't try again with HTTP authentication if SSH prompt for authentication is canceled
        if (!e.getMessage().endsWith("Auth cancel"))
            tryCommandAgainWithHTTPAuth = true;
    }

    private void pushBranchOrAllDetails(RepoHelperBuilder.AuthDialogResponse response, PushType pushType,
                                        PushCommand push) throws
            TransportException {
        try{
            RepositoryMonitor.resetFoundNewChanges(false);
            RepoHelper helper = theModel.getCurrentRepoHelper();
            if (response != null) {
                helper.ownerAuth =
                        new UsernamePasswordCredentialsProvider(response.username, response.password);
            }
            if (pushType == PushType.BRANCH) {
                helper.pushCurrentBranch(push);
            } else if (pushType == PushType.ALL) {
                helper.pushAll(push);
            } else {
                assert false : "PushType enum case not handled";
            }
            gitStatus();
        } catch (InvalidRemoteException e) {
            showNoRemoteNotification();
        } catch (PushToAheadRemoteError e) {
            showPushToAheadRemoteNotification(e.isAllRefsRejected());
        } catch (TransportException e) {
            throw e;
        } catch(Exception e) {
            showGenericErrorNotification();
            e.printStackTrace();
        }
    }

    private RepoHelperBuilder.AuthDialogResponse askUserForCredentials() throws CancelledAuthorizationException {
        final RepoHelperBuilder.AuthDialogResponse response;
        if (tryCommandAgainWithHTTPAuth) {
            response = RepoHelperBuilder.getAuthCredentialFromDialog();
        } else {
            response = null;
        }
        return response;
    }


    /**
     * Performs a `git push --tags`
     */
    public void handlePushTagsButton() {
        try {
            logger.info("نقر زر تزييلات دفع");

            if(this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();

            final RepoHelperBuilder.AuthDialogResponse credentialResponse = askUserForCredentials();

            BusyWindow.show();
            BusyWindow.setLoadingText("دفع تزييلات...");
            Thread th = new Thread(new Task<Void>(){
                @Override
                protected Void call() {
                    tryCommandAgainWithHTTPAuth = false;
                    try {
                        handlePushTagsButtonDetails(credentialResponse);
                    } catch (TransportException e) {
                        determineIfTryAgain(e);
                    } finally {
                        BusyWindow.hide();
                    }

                    if (tryCommandAgainWithHTTPAuth) {
                        Platform.runLater(() -> {
                            handlePushTagsButton();
                        });
                    }

                    return null;
                }
            });
            th.setDaemon(true);
            th.setName("Git push --tags");
            th.start();
        }catch(NoRepoLoadedException e){
            this.showNoRepoLoadedNotification();
            setButtonsDisabled(true);
        } catch (CancelledAuthorizationException e) {
            this.showCommandCancelledNotification();
        }

    }

    private void handlePushTagsButtonDetails(RepoHelperBuilder.AuthDialogResponse response) throws TransportException {
        Iterable<PushResult> results;
        try{
            RepositoryMonitor.resetFoundNewChanges(false);
            RepoHelper helper = theModel.getCurrentRepoHelper();
            if (response != null) {
                helper.ownerAuth =
                        new UsernamePasswordCredentialsProvider(response.username, response.password);
            }
            results = helper.pushTags();
            gitStatus();

            boolean upToDate = true;

            if (results == null)
                upToDate = false;
            else
                for (PushResult result : results)
                    for (RemoteRefUpdate update : result.getRemoteUpdates())
                        if (update.getStatus() == RemoteRefUpdate.Status.OK)
                            upToDate=false;

            if (upToDate)
                showTagsUpToDateNotification();
            else
                showTagsUpdatedNotification();

        } catch(InvalidRemoteException e){
            showNoRemoteNotification();
        } catch(PushToAheadRemoteError e) {
            showPushToAheadRemoteNotification(e.isAllRefsRejected());
        } catch(MissingRepoException e) {
            showMissingRepoNotification();
            setButtonsDisabled(true);
            refreshRecentReposInDropdown();
        } catch (TransportException e) {
            throw e;

        } catch(Exception e) {
            showGenericErrorNotification();
            e.printStackTrace();
        }
    }


    /**
     * Checks out the selected local branch
     * @param selectedBranch the branch to check out
     * @return true if the checkout successfully happens, false if there is an error
     */
    public boolean checkoutBranch(BranchHelper selectedBranch) {
        if(selectedBranch == null) return false;
        // Track the branch if it is a remote branch that we're not yet tracking
        if (selectedBranch instanceof RemoteBranchHelper) {
            try {
                theModel.getCurrentRepoHelper().getBranchModel().trackRemoteBranch((RemoteBranchHelper) selectedBranch);
            } catch (RefAlreadyExistsException e) {
                showRefAlreadyExistsNotification();
            } catch (Exception e) {
                showGenericErrorNotification();
            }
        }
        try {
            selectedBranch.checkoutBranch();

            // If the checkout worked, update the branch heads and focus on that commit
            CommitTreeController.setBranchHeads(CommitTreeController.getCommitTreeModel(), theModel.getCurrentRepoHelper());
            CommitTreeController.focusCommitInGraph(theModel.getCurrentRepoHelper().getBranchModel().getCurrentBranchHead());
            gitStatus();
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
     * Deletes the selected branch
     *
     * @param selectedBranch the branch selected to delete
     */
    public void deleteBranch(BranchHelper selectedBranch) {
        BranchModel branchModel = theModel.getCurrentRepoHelper().getBranchModel();
        boolean authorizationSucceeded = true;
        try {
            if (selectedBranch != null) {
                RemoteRefUpdate.Status deleteStatus;

                if (selectedBranch instanceof LocalBranchHelper) {
                    branchModel.deleteLocalBranch((LocalBranchHelper) selectedBranch);
                    updateUser(selectedBranch.getRefName() + " deleted.");
                }else {
                    deleteRemoteBranch(selectedBranch, branchModel,
                                       (String message) -> updateUser(message));
                }
            }
        } catch (NotMergedException e) {
            logger.warn("تحذير: لايمكن حذف التفريعة لانها لم تدمج");
            /*Platform.runLater(() -> {
                if(PopUpWindows.showForceDeleteBranchAlert() && selectedBranch instanceof LocalBranchHelper) {
                    // If we need to force delete, then it must be a local branch
                    forceDeleteBranch((LocalBranchHelper) selectedBranch);
                }
            });*/
            this.showNotMergedNotification(selectedBranch);
        } catch (CannotDeleteCurrentBranchException e) {
            logger.warn("تحذير: لايمكن حذف التفريعة الحالية");
            this.showCannotDeleteBranchNotification(selectedBranch);
        } catch (TransportException e) {
            this.showTransportExceptionNotification(e);
            authorizationSucceeded = false;
        } catch (GitAPIException e) {
            logger.warn("IO error");
            this.showGenericErrorNotification();
        } finally {
            gitStatus();
            if (authorizationSucceeded) {
                tryCommandAgainWithHTTPAuth = false;
            } else {
                tryCommandAgainWithHTTPAuth = true;
                deleteBranch(selectedBranch);
            }
        }
    }

    void deleteRemoteBranch(BranchHelper selectedBranch, BranchModel branchModel, Consumer<String> updateFn) {
        try {
            final RepoHelperBuilder.AuthDialogResponse credentialResponse = askUserForCredentials();

            BusyWindow.show();
            BusyWindow.setLoadingText("حذف تفريعة محلية...");

            Thread th = new Thread(new Task<Void>() {
                @Override
                protected Void call() {
                    tryCommandAgainWithHTTPAuth = false;
                    try {
                        deleteRemoteBranchDetails(credentialResponse, selectedBranch, branchModel, updateFn);
                    } catch (TransportException e) {
                        determineIfTryAgain(e);
                    } finally {
                        BusyWindow.hide();
                    }

                    if (tryCommandAgainWithHTTPAuth) {
                        Platform.runLater(() -> {
                            deleteRemoteBranch(selectedBranch, branchModel, updateFn);
                        });
                    }

                    return null;
                }
            });
            th.setDaemon(true);
            th.setName("Git delete remote branch");
            th.start();

        } catch (CancelledAuthorizationException e) {
            this.showCommandCancelledNotification();
        }
    }

    private void deleteRemoteBranchDetails(RepoHelperBuilder.AuthDialogResponse response, BranchHelper selectedBranch,
                                           BranchModel branchModel, Consumer<String> updateFn) throws TransportException {

        try {
            if (response != null) {
                selectedBranch.repoHelper.ownerAuth =
                        new UsernamePasswordCredentialsProvider(response.username, response.password);
            }
            RemoteRefUpdate.Status deleteStatus = branchModel.deleteRemoteBranch((RemoteBranchHelper) selectedBranch);
            String updateMessage = selectedBranch.getRefName();
            // There are a number of possible cases, see JGit's documentation on RemoteRefUpdate.Status
            // for the full list.
            switch (deleteStatus) {
                case OK:
                    updateMessage += " deleted.";
                    break;
                case NON_EXISTING:
                    updateMessage += " no longer\nexists on the server.\nFetch -p to remove " + updateMessage;
                default:
                    updateMessage += " deletion\nfailed.";
            }
            updateFn.accept(updateMessage);
        } catch (TransportException e) {
            throw e;
        } catch (GitAPIException | IOException e) {
            logger.warn("خطأ دخل خرج");
            this.showGenericErrorNotification();
        }
    }

    /**
     * force deletes a branch
     * @param branchToDelete LocalBranchHelper
     */
    private void forceDeleteBranch(LocalBranchHelper branchToDelete) {
        BranchModel branchModel = theModel.getCurrentRepoHelper().getBranchModel();
        logger.info("حذف تفريعة حالية");

        try {
            if (branchToDelete != null) {
                // Local delete:
                branchModel.forceDeleteLocalBranch(branchToDelete);

                // Reset the branch heads
                CommitTreeController.setBranchHeads(commitTreeModel, theModel.getCurrentRepoHelper());

                updateUser(" deleted.");
            }
        } catch (CannotDeleteCurrentBranchException e) {
            this.showCannotDeleteBranchNotification(branchToDelete);
        } catch (GitAPIException e) {
            this.showGenericErrorNotification();
        }finally {
            gitStatus();
        }
    }

    /**
     * Adds a commit reverting the selected commits
     * @param commits the commits to revert
     */
    public void handleRevertMultipleButton(List<CommitHelper> commits) {
        try {
            logger.info("نقر زر عكس");

            if(this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();

            BusyWindow.show();
            BusyWindow.setLoadingText("يعكس...");
            Thread th = new Thread(new Task<Void>(){
                @Override
                protected Void call() {
                    try{
                        theModel.getCurrentRepoHelper().revertHelpers(commits);
                        gitStatus();
                    } catch(MultipleParentsNotAllowedException e) {
                        for (CommitHelper commit : commits) {
                            if (commit.getParents().size() > 1) {
                                showCantRevertMultipleParentsNotification();
                            }
                            if (commit.getParents().size() == 0) {
                                showCantRevertZeroParentsNotification();
                            }
                        }
                    } catch(InvalidRemoteException e){
                        showNoRemoteNotification();
                    } catch (TransportException e) {
                        showTransportExceptionNotification(e);
                    } catch(MissingRepoException e){
                        showMissingRepoNotification();
                        setButtonsDisabled(true);
                        refreshRecentReposInDropdown();
                    } catch(Exception e) {
                        showGenericErrorNotification();
                        e.printStackTrace();
                    }finally {
                        BusyWindow.hide();
                    }
                    return null;
                }
            });
            th.setDaemon(true);
            th.setName("Git revert");
            th.start();
        }catch(NoRepoLoadedException e){
            this.showNoRepoLoadedNotification();
            setButtonsDisabled(true);
        }
    }

    /**
     * Reverts the tree to remove the changes in the most recent commit
     * @param commit: the commit to revert
     */
    public void handleRevertButton(CommitHelper commit) {
        try {
            logger.info("نقر زر عكس");

            if(this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();

            BusyWindow.show();
            BusyWindow.setLoadingText("يعكس...");
            Thread th = new Thread(new Task<Void>(){
                @Override
                protected Void call() {
                    try{
                        theModel.getCurrentRepoHelper().revert(commit);
                        gitStatus();
                    } catch(MultipleParentsNotAllowedException e) {
                        if(commit.getParents().size() > 1) {
                            showCantRevertMultipleParentsNotification();
                        }
                        if (commit.getParents().size() == 0) {
                            showCantRevertZeroParentsNotification();
                        }
                    } catch(InvalidRemoteException e){
                        showNoRemoteNotification();
                    } catch (TransportException e) {
                        showTransportExceptionNotification(e);
                    } catch(MissingRepoException e){
                        showMissingRepoNotification();
                        setButtonsDisabled(true);
                        refreshRecentReposInDropdown();
                    } catch(Exception e) {
                        showGenericErrorNotification();
                        e.printStackTrace();
                    }finally {
                        BusyWindow.hide();
                    }
                    return null;
                }
            });
            th.setDaemon(true);
            th.setName("Git revert");
            th.start();
        }catch(NoRepoLoadedException e){
            this.showNoRepoLoadedNotification();
            setButtonsDisabled(true);
        }
    }

    /**
     * Resets the tree to a given commit with default settings
     *
     * @param commit the commit to reset to
     */
    public void handleResetButton(CommitHelper commit) {
        handleAdvancedResetButton(commit, ResetCommand.ResetType.MIXED);
    }

    /**
     * Resets the tree to the given commit, given a specific type
     * @param commit CommitHelper
     * @param type the type of reset to perform
     */
    public void handleAdvancedResetButton(CommitHelper commit, ResetCommand.ResetType type) {
        try {
            logger.info("نقر زر اعادة ضبط");

            if(this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();

            BusyWindow.show();
            BusyWindow.setLoadingText("اعادة ضبط...");
            Thread th = new Thread(new Task<Void>(){
                @Override
                protected Void call() {
                    try{
                        theModel.getCurrentRepoHelper().reset(commit.getId(), type);
                        gitStatus();
                    }catch(InvalidRemoteException e){
                        showNoRemoteNotification();
                    } catch (TransportException e) {
                        showTransportExceptionNotification(e);
                    } catch(MissingRepoException e){
                        showMissingRepoNotification();
                        setButtonsDisabled(true);
                        refreshRecentReposInDropdown();
                    } catch(Exception e) {
                        showGenericErrorNotification();
                        e.printStackTrace();
                    }finally {
                        BusyWindow.hide();
                    }
                    return null;
                }
            });
            th.setDaemon(true);
            th.setName("Git reset");
            th.start();
        }catch(NoRepoLoadedException e){
            this.showNoRepoLoadedNotification();
            setButtonsDisabled(true);
        }
    }

    /**
     * Brings up a window that allows the user to stash changes with options
     */
    public void handleStashSaveButton() {
        try {
            logger.info("نقر زر حفظ اخفاء");

            if (this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();

            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/elegit/fxml/StashSave.fxml"));
            fxmlLoader.load();
            StashSaveController stashSaveController = fxmlLoader.getController();
            stashSaveController.setSessionController(this);
            AnchorPane fxmlRoot = fxmlLoader.getRoot();
            stashSaveController.showStage(fxmlRoot);
        } catch (IOException e) {
            this.showGenericErrorNotification();
            e.printStackTrace();
        } catch (NoRepoLoadedException e) {
            this.showNoRepoLoadedNotification();
            setButtonsDisabled(true);
        }
    }

    public void quickStashSave() {
        try {
            logger.info("نقر زر حفظ اخفاء سريع");

            if (this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();

            this.theModel.getCurrentRepoHelper().stashSave(false);
            gitStatus();
        } catch (GitAPIException e) {
            this.showGenericErrorNotification();
            e.printStackTrace();
        } catch (NoFilesToStashException e) {
            this.showNoFilesToStashNotification();
        } catch (NoRepoLoadedException e) {
            this.setButtonsDisabled(true);
        }
    }

    /**
     * Applies the most recent stash
     */
    public void handleStashApplyButton() {
        // TODO: make it clearer which stash this applies
        logger.info("نقر زر تطبيق اخفاء");
        try {
            CommitHelper topStash = theModel.getCurrentRepoHelper().stashList().get(0);
            this.theModel.getCurrentRepoHelper().stashApply(topStash.getId(), false);
            gitStatus();
        } catch (StashApplyFailureException e) {
            showStashConflictsNotification();
        } catch (GitAPIException e) {
            showGenericErrorNotification();
        } catch (IOException e) {
            showGenericErrorNotification();
        }
    }

    /**
     * Shows the stash window
     */
    public void handleStashListButton() {
        try {
            logger.info("نقر زر لائحة اخفاء");

            if (this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();

            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/elegit/fxml/StashList.fxml"));
            fxmlLoader.load();
            StashListController stashListController = fxmlLoader.getController();
            stashListController.setSessionController(this);
            AnchorPane fxmlRoot = fxmlLoader.getRoot();
            stashListController.showStage(fxmlRoot);
        } catch (IOException e) {
            this.showGenericErrorNotification();
            e.printStackTrace();
        } catch (NoRepoLoadedException e) {
            this.showNoRepoLoadedNotification();
            setButtonsDisabled(true);
        }
    }

    /**
     * Drops the most recent stash
     */
    public void handleStashDropButton() {
        logger.info("نقر زر اخفاء قطرةStash drop button clicked");
        try {
            // TODO: implement droping something besides 0
            this.theModel.getCurrentRepoHelper().stashDrop(0);
        } catch (GitAPIException e) {
            showGenericErrorNotification();
        }
    }

    /**
     * Calls git fetch
     * @param prune boolean should prune
     */
    public void handleFetchButton(boolean prune, boolean pull) {
        logger.info("نقر زر جلب");
        RepositoryMonitor.pause();
        gitFetch(prune, pull);
        RepositoryMonitor.unpause();
        submitLog();
    }

    /**
     * Handles a click on Fetch -p
     */
    public void handlePruneFetchButton() {
        handleFetchButton(true, false);
    }

    /**
     * Handles a click on the "Fetch" button. Calls gitFetch()
     */
    public void handleNormalFetchButton(){
        handleFetchButton(false, false);
    }

    /**
     * Peforms a git pull
     */
    public void handlePullButton() {
        handleFetchButton(false, true);
    }

    /**
     * Queries the remote for new commits, and updates the local
     * remote as necessary.
     * Equivalent to `git fetch`
     */
    private synchronized void gitFetch(boolean prune, boolean pull){
        try{

            if(this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();

            final RepoHelperBuilder.AuthDialogResponse response = askUserForCredentials();

            BusyWindow.show();
            BusyWindow.setLoadingText("يجلب...");
            Thread th = new Thread(new Task<Void>(){
                @Override
                protected Void call() {
                    tryCommandAgainWithHTTPAuth = false;
                    try{
                        RepositoryMonitor.resetFoundNewChanges(false);
                        RepoHelper helper = theModel.getCurrentRepoHelper();
                        if (response != null) {
                            helper.ownerAuth =
                                    new UsernamePasswordCredentialsProvider(response.username, response.password);
                        }
                        if(!helper.fetch(prune)){
                            showNoCommitsFetchedNotification();
                        } if (pull) {
                            mergeFromFetch();
                        }
                        gitStatus();
                    } catch(InvalidRemoteException e){
                        showNoRemoteNotification();
                    } catch (TransportException e) {
                        determineIfTryAgain(e);
                    } catch(MissingRepoException e){
                        showMissingRepoNotification();
                        setButtonsDisabled(true);
                        refreshRecentReposInDropdown();
                    } catch(Exception e) {
                        showGenericErrorNotification();
                        e.printStackTrace();
                    }finally {
                        BusyWindow.hide();
                    }

                    if (tryCommandAgainWithHTTPAuth)
                        Platform.runLater(() -> {
                            gitFetch(prune, pull);
                        });
                    return null;
                }
            });
            th.setDaemon(true);
            th.setName("Git fetch");
            th.start();
        }catch(NoRepoLoadedException e) {
            this.showNoRepoLoadedNotification();
            setButtonsDisabled(true);
        } catch (CancelledAuthorizationException e) {
            this.showCommandCancelledNotification();
        }
    }

    /**
     * Does a merge from fetch
     */
    public void mergeFromFetch() {
        mergeFromFetch(notificationPaneController, null);
    }

    /**
     * merges the remote-tracking branch associated with the current branch into the current local branch
     */
    public void mergeFromFetch(NotificationController notificationController, Stage stageToClose) {
        try{
            logger.info("نقر زر دمج من مجلوب");
            if(theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();
            if(theModel.getCurrentRepoHelper().getBehindCount()<1) throw new NoCommitsToMergeException();

            BusyWindow.show();
            BusyWindow.setLoadingText("يدمج...");
            Thread th = new Thread(new Task<Void>(){
                @Override
                protected Void call() throws GitAPIException, IOException {
                    try{
                        if(!theModel.getCurrentRepoHelper().mergeFromFetch().isSuccessful()){
                            showUnsuccessfulMergeNotification(notificationController);
                        } else {
                            if(stageToClose != null) Platform.runLater(stageToClose::close);
                        }
                        gitStatus();
                    } catch(InvalidRemoteException e){
                        showNoRemoteNotification(notificationController);
                    } catch(TransportException e){
                        showTransportExceptionNotification(notificationController, e);
                    } catch (NoMergeBaseException | JGitInternalException e) {
                        // Merge conflict
                        e.printStackTrace();
                        // todo: figure out rare NoMergeBaseException.
                        //  Has something to do with pushing conflicts.
                        //  At this point in the stack, it's caught as a JGitInternalException.
                    } catch(CheckoutConflictException e){
                        showMergingWithChangedFilesNotification(notificationController);
                    } catch(ConflictingFilesException e){
                        showMergeConflictsNotification(notificationController);
                        Platform.runLater(() -> PopUpWindows.showMergeConflictsAlert(e.getConflictingFiles()));
                        ConflictingFileWatcher.watchConflictingFiles(theModel.getCurrentRepoHelper());
                    } catch(MissingRepoException e){
                        showMissingRepoNotification(notificationController);
                        setButtonsDisabled(true);
                    } catch(GitAPIException | IOException e){
                        showGenericErrorNotification(notificationController);
                        e.printStackTrace();
                    } catch(NoTrackingException e) {
                        showNoRemoteTrackingNotification(notificationController);
                    }catch (Exception e) {
                        showGenericErrorNotification(notificationController);
                        e.printStackTrace();
                    }finally {
                        BusyWindow.hide();
                    }
                    return null;
                }
            });
            th.setDaemon(true);
            th.setName("Git merge FETCH_HEAD");
            th.start();
        }catch(NoRepoLoadedException e){
            this.showNoRepoLoadedNotification(notificationController);
            this.setButtonsDisabled(true);
        }catch(NoCommitsToMergeException e){
            this.showNoCommitsToMergeNotification(notificationController);
        }catch(IOException e) {
            this.showGenericErrorNotification(notificationController);
        }
    }


    public void handleLoggingOff() {
        changeLogging(Level.OFF);
    }

    public void handleLoggingOn() {
        changeLogging(Level.INFO);
        logger.log(Level.INFO, "تغيير الولوج");
    }

    // why are the commitSort methods so slow?
    public void handleCommitSortTopological() {
        TreeLayout.commitSortTopological = true;
        try {
            commitTreeModel.updateView();
        } catch (Exception e) {
            e.printStackTrace();
            showGenericErrorNotification();
        }
    }

    public void handleCommitSortDate() {
        TreeLayout.commitSortTopological = false;
        try {
            commitTreeModel.updateView();
        } catch (Exception e) {
            e.printStackTrace();
            showGenericErrorNotification();
        }
    }

    public void handleAbout() {
        try{
            logger.info("نقر حول");
            // Create and display the Stage:
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/elegit/fxml/About.fxml"));
            GridPane fxmlRoot = fxmlLoader.load();
            AboutController aboutController = fxmlLoader.getController();
            aboutController.setVersion(getVersion());

            Stage stage = new Stage();
            javafx.scene.image.Image img = new javafx.scene.image.Image(getClass().getResourceAsStream("/elegit/images/masadiry.png"));
            stage.getIcons().add(img);
            stage.setTitle("حول");
            stage.setScene(new Scene(fxmlRoot));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setOnCloseRequest(event -> logger.info("اغلق حول"));
            stage.show();
        }catch(IOException e) {
            this.showGenericErrorNotification();
            e.printStackTrace();
        }
    }

    String getVersion() {
        String path = "/version.prop";
        InputStream stream = getClass().getResourceAsStream(path);
        if (stream == null)
            return "UNKNOWN";
        Properties props = new Properties();
        try {
            props.load(stream);
            stream.close();
            return (String) props.get("version");
        } catch (IOException e) {
            return "UNKNOWN";
        }
    }


    /**
     * Opens an editor for the .gitignore
     */
    public void handleGitIgnoreMenuItem() {
        GitIgnoreEditor.show(SessionModel.getSessionModel().getCurrentRepoHelper(), null);
    }


    public void handleNewBranchButton() {
        handleCreateOrDeleteBranchButton("create");
    }

    public void handleDeleteLocalBranchButton() {
        handleCreateOrDeleteBranchButton("local");
    }

    public void handleDeleteRemoteBranchButton() {
        handleCreateOrDeleteBranchButton("remote");
    }


    public void handleCreateOrDeleteBranchButton() {
        handleCreateOrDeleteBranchButton("create");
    }


    /**
     * Pops up a window where the user can create a new branch
     */
    public void handleCreateOrDeleteBranchButton(String tab) {
        try{
            logger.info("نقر زر انشاء/حذف تفريعة");
            if(this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();

            logger.info("فتحت نافذت انشاء/حذف تفريعة");
            // Create and display the Stage:
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/elegit/fxml/CreateDeleteBranchWindow.fxml"));
            fxmlLoader.load();
            CreateDeleteBranchWindowController createDeleteBranchController = fxmlLoader.getController();
            createDeleteBranchController.setSessionController(this);
            AnchorPane fxmlRoot = fxmlLoader.getRoot();
            createDeleteBranchController.showStage(fxmlRoot, tab);
        }catch(IOException e){
            this.showGenericErrorNotification();
            e.printStackTrace();
        }catch(NoRepoLoadedException e){
            this.showNoRepoLoadedNotification();
            setButtonsDisabled(true);
        }
    }

    /**
     * Copies the commit hash onto the clipboard
     */
    public void handleCommitNameCopyButton(){
        logger.info("نسخ اسم ايداع");
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(commitInfoNameText);
        clipboard.setContent(content);
    }

    /**
     * Jumps to the selected commit in the tree display
     */
    public void handleGoToCommitButton(){
        logger.info("نقر زر اذهب لتفريعة");
        String id = commitInfoNameText;
        CommitTreeController.focusCommitInGraph(id);
    }

    public void handleMergeFromFetchButton(){
        handleGeneralMergeButton(false);
    }

    public void handleBranchMergeButton() {
        handleGeneralMergeButton(true);
    }

    /**
     * shows the merge window
     */
    public void handleGeneralMergeButton(boolean localTabOpen) {
        try{
            logger.info("نقر زر دمج");
            if(this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();

            logger.info("فتحت نافذة دمج");
            // Create and display the Stage:
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/elegit/fxml/MergeWindow.fxml"));
            fxmlLoader.load();
            MergeWindowController mergeWindowController = fxmlLoader.getController();
            mergeWindowController.setSessionController(this);
            AnchorPane fxmlRoot = fxmlLoader.getRoot();
            mergeWindowController.showStage(fxmlRoot, localTabOpen);
        }catch(IOException e){
            this.showGenericErrorNotification();
            e.printStackTrace();
        }catch(NoRepoLoadedException e){
            this.showNoRepoLoadedNotification();
            setButtonsDisabled(true);
        }
    }

    /**
     * Updates the panel views when the "git status" button is clicked.
     * Highlights the current HEAD.
     */
//    public void onRefreshButton(){
//        logger.info("Git status button clicked");
//        showUpdatingText(true);
//        this.gitStatus();
//        showUpdatingText(false);
//        CommitTreeController.focusCommitInGraph(theModel.getCurrentRepoHelper().getBranchModel().getCurrentBranchHead());
//    }

    /**
     * Replaces branch status text with "updating" for 0.75 seconds OR the duration of gitStatus()
     */
//    private void showUpdatingText(boolean setVisible) {
//        if(setVisible){
//            isGitStatusDone = false;
//            isTimerDone = false;
//            updatingText.setVisible(true);
//
//            Timer timer = new Timer(true);
//            timer.schedule(new TimerTask() {
//                @Override
//                public void run() {
//                    if(isGitStatusDone){
//                        updatingText.setVisible(false);
//                    }
//                    isTimerDone = true;
//                }
//            }, 750);
//        }else {
//            isGitStatusDone = true;
//            if(isTimerDone) {
//                updatingText.setVisible(false);
//            }
//        }
//    }

    /**
     * Updates the trees, changed files, and branch information. Equivalent
     * to 'git status'
     */
    public void gitStatus(){
        RepositoryMonitor.pause();

        Platform.runLater(() -> {
            // If the layout is still going, don't run
            if (commitTreePanelView.isLayoutThreadRunning) {
                RepositoryMonitor.unpause();
                return;
            }
            try{
                theModel.getCurrentRepoHelper().getBranchModel().updateAllBranches();
                commitTreeModel.update();
                workingTreePanelView.drawDirectoryView();
                allFilesPanelView.drawDirectoryView();
                indexPanelView.drawDirectoryView();
                this.theModel.getCurrentRepoHelper().getTagModel().updateTags();
                updateStatusText();
            } catch(Exception e) {
                showGenericErrorNotification();
                e.printStackTrace();
            } finally{
                RepositoryMonitor.unpause();
            }
        });
    }

    /**
     * When the image representing the remote repo is clicked, go to the
     * corresponding remote url
     * @param event the mouse event corresponding to the click
     */
    public void handleRemoteMouseClick(MouseEvent event){
        if(event.getButton() != MouseButton.PRIMARY) return;
        try {
            if(this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();
            if(!this.theModel.getCurrentRepoHelper().exists()) throw new MissingRepoException();

            List<String> remoteURLs = this.theModel.getCurrentRepoHelper().getLinkedRemoteRepoURLs();

            if(remoteURLs.size() == 0){
                this.showNoRemoteNotification();
            }

            for (String remoteURL : remoteURLs) {
                if(remoteURL.contains("@")){
                    remoteURL = "https://"+remoteURL.replace(":","/").split("@")[1];
                }
                // Use desktop if the system isn't linux
                if (!SystemUtils.IS_OS_LINUX) {
                    Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
                    if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE))
                        desktop.browse(new URI(remoteURL));
                }else {
                    Runtime runtime = Runtime.getRuntime();
                    String[] args = {"xdg-open", remoteURL};
                    runtime.exec(args);
                }
            }
        }catch(URISyntaxException | IOException e){
            this.showGenericErrorNotification();
        }catch(MissingRepoException e){
            this.showMissingRepoNotification();
            this.setButtonsDisabled(true);
            this.refreshRecentReposInDropdown();
        }catch(NoRepoLoadedException e){
            this.showNoRepoLoadedNotification();
            this.setButtonsDisabled(true);
        }
    }

    /**
     * Helper method that tells the user a local branch was created
     * @param type String
     */
    private void updateUser(String type) {
        Platform.runLater(() -> {
            Text txt = new Text(" تفريعة " + type);
            PopOver popOver = new PopOver(txt);
            popOver.setTitle("");
            popOver.show(commitTreePanelView);
            popOver.detach();
            popOver.setAutoHide(true);
        });
    }


    /**
     * Opens the current repo directory (e.g. in Finder or Windows Explorer).
     */
    public void openRepoDirectory(){
        if (Desktop.isDesktopSupported()) {
            try{
                logger.info("يفتح حاوية مستودع");
                if(this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();
                // Use Desktop to open the current directory unless it's Linux
                if (!SystemUtils.IS_OS_LINUX) {
                    Desktop.getDesktop().open(this.theModel.getCurrentRepoHelper().localPath.toFile());
                }
                else {
                    Runtime runtime = Runtime.getRuntime();
                    String[] args = {"nautilus",this.theModel.getCurrentRepoHelper().localPath.toFile().toString()};
                    runtime.exec(args);
                }
            }catch(IOException | IllegalArgumentException e){
                this.showFailedToOpenLocalNotification();
                e.printStackTrace();
            }catch(NoRepoLoadedException e){
                this.showNoRepoLoadedNotification();
                setButtonsDisabled(true);
            }
        }
    }

    /**
     * Shows a popover with all repos in a checklist
     */
    public void chooseRecentReposToDelete() {
        logger.info("نقر زر حذف مستودعات");

        // creates a CheckListView with all the repos in it
        List<RepoHelper> repoHelpers = this.theModel.getAllRepoHelpers();
        CheckListView<RepoHelper> repoCheckListView = new CheckListView<>(FXCollections.observableArrayList(repoHelpers));

        // creates a popover with the list and a button used to remove repo shortcuts
        Button removeSelectedButton = new Button("حذف اختصارات مستودع من مصادري");
        PopOver popover = new PopOver(new VBox(repoCheckListView, removeSelectedButton));
        popover.setTitle("ادارة اخر مستتودعات");

        // shows the popover
        popover.show(dropdownController.removeRecentReposButton);

        removeSelectedButton.setOnAction(e -> {
            this.handleRemoveReposButton(repoCheckListView.getCheckModel().getCheckedItems());
            popover.hide();
        });
    }

    /**
     * removes selected repo shortcuts
     * @param checkedItems list of selected repos
     */
    private void handleRemoveReposButton(List<RepoHelper> checkedItems) {
        logger.info("حذف مستودعات");
        this.theModel.removeRepoHelpers(checkedItems);

        // If there are repos that aren't the current one, and the current repo is being removed, load a different repo
        if (!this.theModel.getAllRepoHelpers().isEmpty() && !this.theModel.getAllRepoHelpers().contains(theModel.getCurrentRepoHelper())) {
            int newIndex = this.theModel.getAllRepoHelpers().size()-1;
            RepoHelper newCurrentRepo = this.theModel.getAllRepoHelpers()
                    .get(newIndex);

            handleRecentRepoMenuItem(newCurrentRepo);
            dropdownController.repoDropdownSelector.setValue(newCurrentRepo);

            this.refreshRecentReposInDropdown();

            // If there are no repos, reset everything
        } else if (this.theModel.getAllRepoHelpers().isEmpty()){
            TreeLayout.stopMovingCells();
            theModel.resetSessionModel();
            workingTreePanelView.resetFileStructurePanelView();
            allFilesPanelView.resetFileStructurePanelView();
            initialize();

            // The repos have been removed, this line just keeps the current repo loaded
        }else {
            try {
                theModel.openRepoFromHelper(theModel.getCurrentRepoHelper());
            } catch (BackingStoreException | IOException | MissingRepoException | ClassNotFoundException e1) {
                e1.printStackTrace();
            }
        }

        this.refreshRecentReposInDropdown();
    }

    /**
     * Opens up the current repo helper's Branch Manager window after
     * passing in this SessionController object, so that the
     * BranchCheckoutController can update the main window's views.
     */
    public void showBranchCheckout() {
        try{
            logger.info("نقر زر تفحص تفريعة");
            if(this.theModel.getCurrentRepoHelper() == null) throw new NoRepoLoadedException();

            logger.info("فتحت نافذة تفحص تفريعة");
            // Create and display the Stage:
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/elegit/fxml/BranchCheckout.fxml"));
            fxmlLoader.load();
            BranchCheckoutController branchCheckoutController = fxmlLoader.getController();
            AnchorPane fxmlRoot = fxmlLoader.getRoot();
            branchCheckoutController.showStage(fxmlRoot);
        }catch(IOException e){
            this.showGenericErrorNotification();
            e.printStackTrace();
        }catch(NoRepoLoadedException e){
            this.showNoRepoLoadedNotification();
            setButtonsDisabled(true);
        }
    }

    /**
     * Opens up the help page to inform users about what symbols mean
     */
    public void showLegend() {
        try{
            logger.info("نقر مفتاح");
            // Create and display the Stage:
            GridPane fxmlRoot = FXMLLoader.load(getClass().getResource("/elegit/fxml/Legend.fxml"));

            Stage stage = new Stage();
            stage.setTitle("مفتاح");
            stage.setScene(new Scene(fxmlRoot));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setOnCloseRequest(event -> logger.info("Closed Legend"));
            stage.show();
        }catch(IOException e) {
            this.showGenericErrorNotification();
            e.printStackTrace();
        }
    }

    /**
     * Displays information about the commit with the given id
     * @param id the selected commit
     */
    public void selectCommit(String id){
        Platform.runLater(() -> {
            CommitHelper commit = this.theModel.getCurrentRepoHelper().getCommit(id);
            commitInfoNameText = commit.getName();

            commitInfoController.setCommitInfoMessageText(theModel.getCurrentRepoHelper().getCommitDescriptorString(commit, true));

            tagNameField.setVisible(true);
            tagButton.setVisible(true);
            infoTagBox.toFront();
        });
    }

    /**
     * Stops displaying commit information
     */
    public void clearSelectedCommit(){
        Platform.runLater(() -> {
            commitInfoController.clearCommit();
            commitTreePanelView.toFront();

            tagNameField.setText("");
            tagNameField.setVisible(false);
            tagButton.setVisible(false);
            pushTagsButton.setVisible(false);
            infoTagBox.toBack();
        });
    }

    /// ******************************************************************************
    /// ********                 BEGIN: ERROR NOTIFICATIONS:                  ********
    /// ******************************************************************************

    private void showGenericErrorNotification(NotificationController nc) {
        Platform.runLater(()-> {
            logger.warn("تحذير: خطأ عام.");
            nc.addNotification("نأسف يوجد خطأ ما");
        });
    }

    void showGenericErrorNotification() {
        Platform.runLater(()-> {
            logger.warn("تحذير: خطأ عام.");
            notificationPaneController.addNotification("نأسف يوجد خطأ ما");
        });
    }

    private void showJGitInternalError(JGitInternalException e) {
        Platform.runLater(()-> {
            if (e.getCause().toString().contains("LockFailedException")) {
                logger.warn("تحذير: فشل الاغلاق");
                this.notificationPaneController.addNotification(e.getCause().getMessage()+". If no other git processes are running, manually remove all .lock files.");
            } else {
                logger.warn("تحذير: جي جيت عام داخلي");
                this.notificationPaneController.addNotification("نأسف يوجد خطأ جيت.");
            }
        });
    }

    private void showNoRepoLoadedNotification(NotificationController nc) {
        Platform.runLater(() -> {
            logger.warn("تحذير لم يحمل مستودع.");
            nc.addNotification("عليك تحميل مستودع قبل قيامك بهذه العملية عليه. انقر علي علامة زائد في اعلي اليمين!");
        });
    }

    private void showNoRepoLoadedNotification() {
        Platform.runLater(() -> {
            logger.warn("تحذير: لم يحمل مستودع.");
            notificationPaneController.addNotification("عليك تحميل مستودع قبل قيامك بهذه العملية عليه. انقر علي علامة زائد في اعلي اليمين!");
        });
    }

    private void showInvalidRepoNotification() {
        Platform.runLater(() -> {
            logger.warn("تحذير: مستودع غير صالح.");
            this.notificationPaneController.addNotification("تأكد ان الحاوية التي اخترتها تحوي حاوية المستودع الحالي.");
        });
    }

    private void showMissingRepoNotification(NotificationController nc){
        Platform.runLater(()-> {
            logger.warn("تحذير: مستودع مفقود");
            nc.addNotification("لم يعد هذا المستودع موجودا.");
        });
    }

    private void showMissingRepoNotification(){
        Platform.runLater(()-> {
            logger.warn("تحذير: مستودع مفقود");
            notificationPaneController.addNotification("لم يعد المستودع موجودا.");
        });
    }

    private void showNoRemoteNotification(NotificationController nc){
        Platform.runLater(()-> {
            logger.warn("تحذير: لايوجد مستودع بعيد");
            String name = this.theModel.getCurrentRepoHelper() != null ? this.theModel.getCurrentRepoHelper().toString() : "المستودع الحالي";

            nc.addNotification("لايوجد مستودع بعيد مرتبط مع " + name);
        });
    }

    private void showNoRemoteNotification(){
        Platform.runLater(()-> {
            logger.warn("تحذير: لايوجد مستودع بعيد");
            String name = this.theModel.getCurrentRepoHelper() != null ? this.theModel.getCurrentRepoHelper().toString() : "المستودع الحالي";

            notificationPaneController.addNotification("لايوجد مستودع بعيد مرتبط مع " + name);
        });
    }

    private void showFailedToOpenLocalNotification(){
        Platform.runLater(()-> {
            logger.warn("تحذير: فشل تحميل المستودع المحلي");
            String path = this.theModel.getCurrentRepoHelper() != null ? this.theModel.getCurrentRepoHelper().getLocalPath().toString() : "the location of the local repository";

            this.notificationPaneController.addNotification("لايمكن فتح الحاوية في  " + path);
        });
    }

    private void showNonEmptyFolderNotification(Runnable callback) {
        Platform.runLater(()-> {
            logger.warn("تحذير: الحاوية موجودة بالفعل");
            this.notificationPaneController.addNotification("تأكد من عدم وجود حاوية بهذا الاسم في هذا المكان");
        });
    }

    private void showInvalidRemoteNotification(Runnable callback) {
        Platform.runLater(() -> {
            logger.warn("تحذير: مستودع بعيد غير صالح");
            this.notificationPaneController.addNotification("تأكد من ادخال الرابط الصحيح للمستودع.");
        });
    }

    private void showInvalidTagNameNotification(String tagName) {
        Platform.runLater(() -> {
            logger.warn("استثناء اسم تنزيل غير صالح");
            this.notificationPaneController.addNotification("اسم التزييل '"+tagName+"' غير صالح.\n احذف اي محرف .~^:?*[]{}@  ثم حاول مجدد.");
        });
    }

    private void showTransportExceptionNotification(TransportException e) {
        Platform.runLater(() -> {
            showTransportExceptionNotification(notificationPaneController, e);
        });

    }

    private void showTransportExceptionNotification(NotificationController nc, TransportException e) {
        Platform.runLater(() -> {

            if (e.getMessage().endsWith("git-receive-pack not permitted")) {
                logger.warn("تحذي: تفويض غير صالح للمستودع");
                notificationPaneController.addNotification("ولوجك صحيح، لكن ليس لديك" +
                                                           " صلاحية للقيام بهذه " +
                                                           "العملية علي هذا المستودع.");
            } else if (e.getMessage().endsWith("git-receive-pack not found")) {
                logger.warn("تحذير: لايمكن ايجاد المستودع البعيد");
                this.notificationPaneController.addNotification("فشل الدفع لتعزر ايجاد المستودع البعيد.");
            } else if (e.getMessage().endsWith("not authorized")) {
                logger.warn("تحذير: تفويض فاسد ");
                nc.addNotification("توليفة اسم المستخدم/كلمة المرور التي ادخلتها غير صحيحة. " +
                                   "جرب اعادة ادخال كلمة المرور.");
            } else {
                logger.warn("استثناء نقل");
                nc.addNotification("خطأ في الاتصال: " + e.getMessage());
            }

        });
    }

    private void showRepoWasNotLoadedNotification() {
        Platform.runLater(() -> {
            logger.warn("تحذير: المستودع لم يحمل");
            this.notificationPaneController.addNotification("شيء ما خطأ, لذا لم يحمل اي مستودع.");
        });
    }

    private void showPushToAheadRemoteNotification(boolean allRefsRejected){
        Platform.runLater(() -> {
            logger.warn("تحذير: المستودع البعيد متقدم علي المحلي");
            if(allRefsRejected) this.notificationPaneController.addNotification("المستودع البعيد متقدم علي المحلي. عليك جلب ثم دمج (سحب pull) قبل الدفع.");
            else this.notificationPaneController.addNotification("عليك جلب/دمج لتستطيع دفع كل تعديلاتك.");
        });
    }

    private void showLostRemoteNotification() {
        Platform.runLater(() -> {
            logger.warn("تحذير تعذر ايجاد المستودع البعيد");
            this.notificationPaneController.addNotification("فشل الدفع لتعزر ايجاد المستودع البعيد.");
        });
    }

    private void showSameRepoLoadedNotification() {
        Platform.runLater(() -> {
            logger.warn("حمل نفس المستودع");
            this.notificationPaneController.addNotification("المستودع مفتوح بالفعل");
        });
    }

    private void showNoFilesStagedForCommitNotification(){
        Platform.runLater(() -> {
            logger.warn("تحذير: لاتوجد ملفات مدرجة للايداع");
            this.notificationPaneController.addNotification("عليك اضافة ملفات قبل الايداع");
        });
    }


    private void showNoFilesSelectedForAddNotification(){
        Platform.runLater(() -> {
            logger.warn("تحذير: لم تحدد ملفات للاضافة");
            this.notificationPaneController.addNotification("عليك تحديد ملفات حتي تضيفها");
        });
    }

    private void showStagedFilesSelectedNotification(){
        Platform.runLater(() -> {
            logger.warn("تحذير: ملفات مدرجة حددت للايداع");
            this.notificationPaneController.addNotification("لايمكنك اضافة ملفات مدرجة!");
        });
    }


    private void showNoFilesSelectedForRemoveNotification(){
        Platform.runLater(() -> {
            logger.warn("تحذير: لم تدرج ملفات للحذف");
            this.notificationPaneController.addNotification("عليك تحديد ملفات للحذف");
        });
    }


    private void showCannotAddFileNotification(String filename) {
        Platform.runLater(() -> {
            logger.warn("تنبيه: تعذر اضافة ملف");
            this.notificationPaneController.addNotification("لايمكن اضافة "+filename+". قد يكون مدرجا بالفعل.");
        });
    }

    private void showCannotRemoveFileNotification(String filename) {
        Platform.runLater(() -> {
            logger.warn("تنبيه: تعزر حذف ملف");
            this.notificationPaneController.addNotification("لايمكن حذف "+filename+" لانه ادرج مسبقا.");
        });
    }

    private void showNoTagNameNotification(){
        Platform.runLater(() -> {
            logger.warn("تحذير: لايوجد اسم تزييل");
            this.notificationPaneController.addNotification("عليك كتابة اسم تزييل لتزيل الايداع");
        });
    }

    private void showNoCommitsToPushNotification(){
        Platform.runLater(() -> {
            logger.warn("تحذير: عدم وجود ايداعات محلية لدفعها");
            this.notificationPaneController.addNotification("لاتوجد اي ايداعات محلية لتدفع");
        });
    }

    private void showTagsUpToDateNotification(){
        Platform.runLater(() -> {
            logger.warn("تنبيه: التزييلات محدثة");
            this.notificationPaneController.addNotification("التزييلات محدثة مع المستودع البعيد");
        });
    }

    private void showTagsUpdatedNotification(){
        Platform.runLater(() -> {
            logger.warn("تنبيه تزييلات محدثة");
            this.notificationPaneController.addNotification("حدثت التعديلات مسبقا");
        });
    }

    private void showNoCommitsFetchedNotification(){
        Platform.runLater(() -> {
            logger.warn("تحذير: لم تجلب ايداعات");
            this.notificationPaneController.addNotification("لم تجلب ايداعات جديدة");
        });
    }

    private void showTagExistsNotification() {
        Platform.runLater(()-> {
            logger.warn("تحذير: التزييلات موجودة مسبقا.");
            this.notificationPaneController.addNotification("نأسف، هذا التزييل موجود في المستودع.");
        });
    }

    private void showCantRevertMultipleParentsNotification() {
        Platform.runLater(() -> {
            logger.warn("حاول عكس ايداع باباء متعددين.");
            this.notificationPaneController.addNotification("لايمكنك عكس هذا الايداع لان لديه اكثر من اب.");
        });
    }

    private void showCantRevertZeroParentsNotification() {
        Platform.runLater(() -> {
            logger.warn("حاول عكس ايداع بلا أب.");
            this.notificationPaneController.addNotification("لايمكنك عكس هذا الايداع لانه بلا أب.");
        });
    }

    private void showCommandCancelledNotification() {
        Platform.runLater(() -> {
            logger.warn("الغي الأمر.");
            this.notificationPaneController.addNotification("الغي الأمر.");
        });
    }

    private void showCannotDeleteBranchNotification(BranchHelper branch) {
        Platform.runLater(() -> {
            logger.warn("تنبيه: لايمكن حذف التفريعة الحالية");
            notificationPaneController.addNotification(String.format("نأسف لايمكن حذف %s الان.  " +
                    "حاول الانتقال لتفريعة أخري أولا", branch.getRefName()));
        });
    }

    private void showNotMergedNotification(BranchHelper nonmergedBranch) {
        logger.warn("تنبيه: لم يدرج");
        notificationPaneController.addNotification("يجب دمج هذه التفريعة قبل القيام بذلك.");
    }

    private void showStashConflictsNotification() {
        Platform.runLater(() -> {
            logger.warn("تحذير: تضارب تطبيق اخفاء");

            EventHandler handler = event -> quickStashSave();
            this.notificationPaneController.addNotification("لايمكنك تطبيق ه    ذا الاخفاء لانه سيحدث تضارب. " +
                    "اخف تعديلاتك. او حل التضاربات أولا.", "stash", handler);
        });
    }

    private void showCheckoutConflictsNotification(List<String> conflictingPaths) {
        Platform.runLater(() -> {
            logger.warn("تحذير: تضارب تفحص");

            EventHandler handler = event -> quickStashSave();
            this.notificationPaneController.addNotification("لايمكنك الانتقال لتلك التفريعة لانه سيوجد تضارب دمج. " +
                    "اخف تعديلاتك. او حل التضاربات أولا.", "stash", handler);
        });
    }

    private void showRefAlreadyExistsNotification() {
        logger.info("تنبيه: التفريعة موجودة بالفعل");
        notificationPaneController.addNotification("يبدو ان هذه التفريعة موجودة محليا");
    }

    private void showUnsuccessfulMergeNotification(NotificationController nc){
        Platform.runLater(() -> {
            logger.warn("تحذير: فشل دمج");
            nc.addNotification("فشل الدمج");
        });
    }

    private void showMergingWithChangedFilesNotification(NotificationController nc){
        Platform.runLater(() -> {
            logger.warn("تحذير: لايمكن دمج ملفات معدلة");
            nc.addNotification("لايمكن الدمج مع الملفات المعدلة حاليا. رجاء أودع/أضف قبل الدمج.");
        });
    }

    private void showMergeConflictsNotification(NotificationController nc){
        Platform.runLater(() -> {
            logger.warn("تحذير: تضارب دمج");
            nc.addNotification("لا يمكن اكمال الدمج بسبب التضارب. حل التضارب أولا ثم أودع كل التعديلات لاكمال الدمج");
        });
    }

    private void showNoRemoteTrackingNotification(NotificationController nc) {
        Platform.runLater(() -> {
            logger.warn("تنبيه: لايوجد تتبع بعيد للتفريعة الحالية.");
            nc.addNotification("لايوجد تتبع بعيد للتفريعة الحالية.");
        });
    }

    private void showNoCommitsToMergeNotification(NotificationController nc){
        Platform.runLater(() -> {
            logger.warn("تحذير: لايوجد ايداعات لتدمج");
            nc.addNotification("لاتوجد اي ايداعات لادماجها قم بالجلب أولا");
        });
    }

    private void showNoFilesToStashNotification() {
        Platform.runLater(() -> {
            logger.warn("تحذير: لاتوجد ملفات لاخفائها");
            notificationPaneController.addNotification("لاتوجد ملفات لاخفائها.");
        });
    }

    // END: ERROR NOTIFICATIONS ^^^

    private void submitLog() {
        try {
            String lastUUID = theModel.getLastUUID();
            theModel.setLastUUID(d.submitData(lastUUID));
        } catch (BackingStoreException | ClassNotFoundException | IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            try { theModel.setLastUUID(""); }
            catch (Exception f) { // This shouldn't happen
            }
        }
    }

    /**
     * Initialization method that loads the level of logging from preferences
     * This will show a popup window if there is no preference
     * @throws BackingStoreException
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void loadLogging() {
        Platform.runLater(() -> {
            Level storedLevel = getLoggingLevel();
            if (storedLevel == null) {
                storedLevel = PopUpWindows.getLoggingPermissions() ? Level.INFO : Level.OFF;
            }
            changeLogging(storedLevel);
            menuController.loggingToggle.setSelected(storedLevel.equals(org.apache.logging.log4j.Level.INFO));
            logger.info("يبدأ.");
        });
    }

    /**
     * Helper method to change whether or not this session is logging, also
     * stores this in preferences
     * @param level the level to set the logging to
     */
    void changeLogging(Level level) {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
        loggerConfig.setLevel(level);
        ctx.updateLoggers();

        setLoggingLevelPref(level);
    }

    Level getLoggingLevel() {
        try {
            return (Level) PrefObj.getObject(this.preferences, LOGGING_LEVEL_KEY);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    void setLoggingLevelPref(Level level) {
        try {
            PrefObj.putObject(this.preferences, LOGGING_LEVEL_KEY, level);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
