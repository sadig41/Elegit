package elegit;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.Dialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.controlsfx.control.CheckListView;

import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by connellyj on 7/7/16.
 *
 * Class that initializes a given pop up window
 */
public class PopUpWindows {

    static final Logger logger = LogManager.getLogger();

    /**
     * Informs the user that they are about to commit a conflicting file
     *
     * @return String user's response to the dialog
     */
    static String showCommittingConflictingFileAlert() {
        String resultType;

        Alert alert = new Alert(Alert.AlertType.WARNING);

        ButtonType resolveButton = new ButtonType("Open Editor");
        ButtonType addButton = new ButtonType("Add");
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType helpButton = new ButtonType("Help", ButtonBar.ButtonData.HELP);

        alert.getButtonTypes().setAll(helpButton, resolveButton, addButton, cancelButton);

        alert.setResizable(true);
        alert.getDialogPane().setPrefSize(450, 200);

        alert.setTitle("تحذير: ملف متضارب");
        alert.setHeaderText("لقد اضفت ملفا نتضاربا");
        alert.setContentText("يمكنك فتح المحرر لحل هذا التعارض، أو  اضف التعديلات علي كل حال. ماذا ستفعل؟");

        ImageView img = new ImageView(new javafx.scene.image.Image("/elegit/images/conflict.png"));
        img.setFitHeight(40);
        img.setFitWidth(80);
        img.setPreserveRatio(true);
        alert.setGraphic(img);

        Optional<ButtonType> result = alert.showAndWait();

        if (result.orElse(null) == resolveButton) {
            logger.info("اختر حل التضاربات");
            resultType = "resolve";
        } else if (result.orElse(null) == addButton) {
            logger.info("اختر اضافة ملف");
            resultType = "add";
        } else if (result.orElse(null) == helpButton) {
            logger.info("اختر الحصول علي مساعدة");
            resultType = "help";
        } else {
            // User cancelled the dialog
            logger.info("الغي صندوق الحوار");
            resultType = "cancel";
        }

        return resultType;
    }

    /**
     * Asks the user for permission to log anonymous usage data
     * @return true if the user selected yes to
     */
    public static boolean getLoggingPermissions() {
        Alert window = new Alert(Alert.AlertType.INFORMATION);

        ButtonType okButton = new ButtonType("Share");
        ButtonType buttonTypeCancel = new ButtonType("Don't Share", ButtonBar.ButtonData.CANCEL_CLOSE);

        window.getButtonTypes().setAll(okButton, buttonTypeCancel);
        window.setResizable(true);
        window.getDialogPane().setPrefSize(300, 200);
        window.setTitle("استخدام البيانات");
        window.setHeaderText("مشاركة مجهولة لاستخدام البيانات");
        window.setContentText("انقر مشاركة اذا اردت مشاركة استخدام البيانات بشكل مجهول معنا, " +
                "وهذا سيساعدنا علي تحسين هذا البرنامج. يمكنك تغيير هذا في اي لحظة من " +
                "قائمة تفضيلات.");
        Optional<ButtonType> result = window.showAndWait();

        return result.orElse(null) == okButton;
    }

    /**
     * Shows a window with instructions on how to fix a conflict
     */
    static void showConflictingHelpAlert() {
        Platform.runLater(() -> {
            Alert window = new Alert(Alert.AlertType.INFORMATION);
            window.setResizable(true);
            window.getDialogPane().setPrefSize(550, 350);
            window.setTitle("كيف تحل الملفات المتضاربة");
            window.setHeaderText("كيف تحل الملفات المتضاربة");
            window.setContentText("1. افتح اولا الملف المعلم كمتضارب.\n" +
                                  "2. في الملف ستري شيء مثل هذا:\n\n" +
                                  "\t<<<<<< <branch_name>\n" +
                                  "\tالتعديلات التي تمت علي التفريعة التي تدمج اليها.\n" +
                                  "\t في معظم الحالات ستكون هذه هي التفريعة التي خرجت منها للتو (i.e. HEAD).\n" +
                                  "\t=======\n" +
                                  "\tالتعديلات التي تمت بالتفريعة التي تدمج لها.\n" +
                                  "\t>>>>>>> <branch name>\n\n" +
                                  "3. احذف المحتوي الذي لاتريده بعد الدمج\n" +
                                  "4. ازل العلامات  التي وضعها جيت في الملف (<<<<<<<, =======, >>>>>>>) \n" +
                                  "5. الان يمكنك الاضافة والايداع بامان في هذا الملف");
            window.showAndWait();
        });
    }

    /**
     * Shows a window with some info about git reset
     */
    static void showResetHelpAlert() {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.getDialogPane().setPrefSize(300, 300);
            alert.setTitle("مساعدة اعادة الضبط");
            alert.setHeaderText("ماهو اعادة الضبط?");
            ImageView img = new ImageView(new Image("/elegit/images/undo.png"));
            img.setFitHeight(60);
            img.setFitWidth(60);
            alert.setGraphic(img);
            alert.setContentText("حرك ملاحظة التفريعة الحالية للخلف لاختيار ايداع, " +
                                 "اعد ضبط منطقة الادراج للمطابقة, " +
                                 "لكن اترك مسار العمل كما هو. " +
                                 "سيتم اعادة كل التعديلات منذ الايداع المحدد في مسار العمل.");
            alert.showAndWait();
        });
    }

    /**
     * Show a window with info about git revert
     */
    static void showRevertHelpAlert() {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.getDialogPane().setPrefWidth(500);
            alert.setTitle("مساعدة العكس");
            alert.setHeaderText("ماهو العكس revert؟");
            ImageView img = new ImageView(new Image("/elegit/images/undo.png"));
            img.setFitHeight(60);
            img.setFitWidth(60);
            alert.setGraphic(img);
            alert.setContentText("اساسا يقوم git revert باخذ ملفاتك الحالية، " +
                    "ثم يحذف اي تعديلات من الايداع(ات) الذي اخترته، وينشيء ايداعا جديدا. " +
                    "راجع \n\nhttp://dmusican.github.io/Elegit/jekyll/update/2016/08/04/what-is-revert.html\n\n" +
                    "للمزيد من المعلومات");
            alert.showAndWait();
        });
    }

    /**
     * Shows a warning about checking out files from the index
     *
     * @return
     */
    public static boolean showCheckoutAlert() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        ButtonType checkout = new ButtonType("Checkout");
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(checkout, cancel);

        alert.setTitle("تحذير تفحص");
        alert.setContentText("هل تريد فعلا تفحص الملفات المحددة؟\n" +
                "سيلغي هذا اي تعديلات لم تتم اضافتها (ادراجها).");
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == checkout;
    }

    /**
     * Informs the user that they are adding a previously conflicting file
     *
     * @return String result from user input
     */
    static String showAddingingConflictingThenModifiedFileAlert() {
        String resultType;

        Alert alert = new Alert(Alert.AlertType.INFORMATION);

        ButtonType commitButton = new ButtonType("Add");
        ButtonType buttonTypeCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(commitButton, buttonTypeCancel);

        alert.setResizable(true);
        alert.getDialogPane().setPrefSize(300, 200);

        alert.setTitle("اضافة ملف متعارض سابقا");
        alert.setHeaderText("لقد اضفت ملفا متعارضا تم تعديله سابقا للادراج");
        alert.setContentText("اذا كان هذا ما تريده في الملف، عليك ايداعه. والا فقم بتعديل الملف كما تريد.");

        Optional<ButtonType> result = alert.showAndWait();

        if (result.orElse(null) == commitButton) {
            logger.info("اختيار الاضافة");
            resultType = "add";
        } else {
            // User cancelled the dialog
            logger.info("الغي صندوق الحوار");
            resultType = "cancel";
        }

        return resultType;
    }

    /**
     * Informs the user that they are tracking ignored files
     *
     * @param trackedIgnoredFiles collections of files being ignored
     */
    static void showTrackingIgnoredFilesWarning(Collection<String> trackedIgnoredFiles) {
        Platform.runLater(() -> {
            if (trackedIgnoredFiles.size() > 0) {
                String fileStrings = "";
                for (String s : trackedIgnoredFiles) {
                    fileStrings += "\n" + s;
                }
                Alert alert = new Alert(Alert.AlertType.WARNING, "تم تتبع الملفات التالية بواسطة جيت, " +
                                                                 "لكنها تطابق نمط التجاهل ايضا. اذا اردت تجاهل هذه الملفات، ازلها من جيت.\n" + fileStrings);
                alert.showAndWait();
            }
        });
    }

    /**
     * Informs the user that there are ملفات متضاربة so they can't checkout a different branch
     *
     * @param conflictingPaths ملفات متضاربة
     */
    public static void showCheckoutConflictsAlert(List<String> conflictingPaths) {
        logger.warn("تحذير تضاربات تفحص");
        String conflictList = "";
        for (String pathName : conflictingPaths) {
            conflictList += "\n" + pathName;
        }
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("ملفات متضاربة");
        alert.setHeaderText("لايمكن تفحص هذه التفريعة");
        alert.setContentText("لايمكنك الانتقال لهذه التفريعة لتضارب الملفات التالية بين تلك التفريعة وتفريعتك الحالية: "
                             + conflictList);

        alert.showAndWait();
    }

    /**
     * Informs the user that there were conflicts
     *
     * @param conflictingPaths ملفات متضاربة
     */
    public static void showMergeConflictsAlert(List<String> conflictingPaths) {
        logger.warn("تحذير تعارضات دمج");
        String conflictList = "";
        for (String pathName : conflictingPaths) {
            conflictList += "\n" + pathName;
        }
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("ملفات متضاربة");
        alert.setHeaderText("لا يمكن اكمال الدمج");
        alert.setContentText("توجد تضاربات بالملفات التالية: "
                             + conflictList);

        alert.showAndWait();
    }

    public static RemoteBranchHelper showTrackDifRemoteBranchDialog(ObservableList<RemoteBranchHelper> remoteBranches) {
        Dialog dialog = new Dialog();
        dialog.getDialogPane().setPrefSize(320, 100);
        dialog.setTitle("تتبع التفريعة البعيدة محليا");

        Text trackText = new Text("تتبع ");
        Text localText = new Text(" محلي.");

        ComboBox<RemoteBranchHelper> dropdown = new ComboBox<>(remoteBranches);
        dropdown.setPromptText("حدد التفريعة البعيدة...");

        HBox hBox = new HBox(trackText, dropdown, localText);
        hBox.setSpacing(10);
        hBox.setAlignment(Pos.CENTER);

        dialog.getDialogPane().setContent(hBox);

        ButtonType trackButton = new ButtonType("Track");
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.getDialogPane().getButtonTypes().addAll(trackButton, cancelButton);

        Optional<?> result = dialog.showAndWait();

        if (result.orElse(null) == trackButton) {
            dialog.close();
            return dropdown.getSelectionModel().getSelectedItem();
        }

        return null;
    }

    public static boolean showForceDeleteBranchAlert() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("حذف تفريعة غير مدموجة");
        alert.setHeaderText("التفريعة التي تود حذفها غير مدموجة");
        alert.setContentText("العمل الذي تم بهذه التفريعة غير ممثل حاليا في تفريعتك المحلية. " +
                             "اذا حذفته، ستفقد اي عمل قمت به محليا علي هذه التفريعة. " +
                             "ما الذي تود فعله؟");

        ButtonType deleteButton = new ButtonType("Force delete branch");
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().clear();
        alert.getButtonTypes().addAll(deleteButton, cancelButton);

        Optional<?> result = alert.showAndWait();

        return result.orElse(null) == deleteButton;
    }

    static String pickRemoteToPushTo(Set<String> remotes) {
        ReentrantLock lock = new ReentrantLock();
        Condition finishedAlert = lock.newCondition();

        final String[] result = new String[1];

        Platform.runLater(() -> {
            try {
                lock.lock();

                Alert alert = new Alert(Alert.AlertType.NONE);
                alert.setTitle("وجود اكثر من مستودع بعيد");
                alert.setHeaderText("يوجد أكثر من مستودع بعيد مرتبط بهذا المستودع.\nاختر واحد للدفع اليه.");

                ComboBox<String> remoteRepos = new ComboBox<>();
                remoteRepos.setPromptText("اختيار مستودع بعيد...");
                remoteRepos.setItems(FXCollections.observableArrayList(remotes));

                ButtonType okButton = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
                ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

                alert.getDialogPane().setContent(remoteRepos);
                alert.getButtonTypes().addAll(cancelButton, okButton);

                Optional<?> alertResult = alert.showAndWait();

                if (alertResult.isPresent()) {
                    if (alertResult.get() == okButton) {
                        result[0] = remoteRepos.getSelectionModel().getSelectedItem();
                    }
                }

                finishedAlert.signal();
            } finally {
                lock.unlock();
            }
        });

        lock.lock();

        try {
            finishedAlert.await();

            if (result[0] != null) {
                return result[0];
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }

        return "cancel";
    }

    public static String getCommitMessage() {
        Alert alert = new Alert(Alert.AlertType.NONE);
        alert.setTitle("رسالة ايداع");
        alert.setResizable(true);

        TextArea textArea = new TextArea();
        textArea.setPromptText("رسالة ايداع...");
        textArea.setWrapText(true);
        textArea.setPrefSize(250, 150);
        textArea.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);

        HBox hBox = new HBox(textArea);
        hBox.setAlignment(Pos.CENTER);

        ButtonType okButton = new ButtonType("Commit", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getDialogPane().setContent(hBox);
        alert.getButtonTypes().addAll(cancelButton, okButton);

        Optional<?> alertResult = alert.showAndWait();

        if (alertResult.isPresent()) {
            if (alertResult.get() == okButton && !textArea.getText().equals("")) {
                return textArea.getText();
            }
        }
        return "cancel";
    }

    static ArrayList<LocalBranchHelper> getUntrackedBranchesToPush(ArrayList<LocalBranchHelper> branches) {

        final ArrayList<LocalBranchHelper> result = new ArrayList<>(branches.size());

        Alert alert = new Alert(Alert.AlertType.NONE);
        alert.setTitle("تفريعات محلية غير متابعة");
        alert.setHeaderText("التفريعات ادناه غير متابعا بمستودع بعيد.\n" +
                            "اختر التفريعات التي تريد انشا تدفق لمستودع بعيد لها.");

        CheckListView<LocalBranchHelper> untrackedBranches = new CheckListView<>(FXCollections.observableArrayList(branches));

        ButtonType okButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType trackButton = new ButtonType("Track Branches", ButtonBar.ButtonData.APPLY);
        alert.getDialogPane().setContent(untrackedBranches);
        alert.getButtonTypes().addAll(trackButton, okButton);

        Optional<?> alertResult = alert.showAndWait();

        if (alertResult.isPresent()) {
            if (alertResult.get() == trackButton) {
                result.addAll(untrackedBranches.getCheckModel().getCheckedItems());
            }
        }

        if (result.size() > 0)
            return result;
        else
            return null;
    }

    static boolean trackCurrentBranchRemotely(String branchName) {

        final boolean[] result = new boolean[1];
        result[0] = false;

        Alert alert = new Alert(Alert.AlertType.NONE);
        alert.setTitle("push -u");

        Label branchLabel = new Label(branchName);
        HBox branchBox = new HBox(branchLabel);
        // The CSS style classes weren't working here
        branchBox.setStyle("    -fx-background-color: #1E90FF;\n" +
                           "    -fx-background-radius: 5;\n" +
                           "    -fx-padding: 0 3 0 3;");
        branchLabel.setStyle("    -fx-text-fill: #FFFFFF;\n" +
                             "    -fx-font-size: 14px;\n" +
                             "    -fx-font-weight: bold;\n" +
                             "    -fx-text-align: center;");

        Text txt1 = new Text(" غير متابع بعيد حاليا.");
        Text txt2 = new Text("هل تريد انشاء تدفق بمستودع بعيد له?");
        txt1.setFont(new Font(14));
        txt2.setFont(new Font(14));

        HBox hBox = new HBox(branchBox, txt1);

        VBox vBox = new VBox(hBox, txt2);
        vBox.setSpacing(10);
        vBox.setAlignment(Pos.CENTER);

        ButtonType okButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType trackButton = new ButtonType("Yes", ButtonBar.ButtonData.APPLY);

        alert.getDialogPane().setContent(vBox);
        alert.getButtonTypes().addAll(trackButton, okButton);

        Optional<?> alertResult = alert.showAndWait();

        if (alertResult.isPresent()) {
            if (alertResult.get() == trackButton) {
                result[0] = true;
            }
        }

        return result[0];
    }
}
