package elegit;

import javafx.scene.control.*;
import elegit.treefx.Cell;
import elegit.treefx.TreeGraph;
import elegit.treefx.TreeGraphModel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.IO;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles the conversion/creation of a list of commit helpers into a nice
 * tree structure. It also takes care of updating the view its given to
 * display the new tree whenever the graph is updated.
 */
public abstract class CommitTreeModel{

    // The view corresponding to this model
    CommitTreePanelView view;

    // The model from which this class pulls its commits
    SessionModel sessionModel;
    // The graph corresponding to this model
    TreeGraph treeGraph;

    // A list of all branches this tree knows about
    private List<BranchHelper> branches;
    // A map from branch names to the branches themselves
    private Map<String, BranchHelper> branchMap;

    // A list of commits in this model
    private List<CommitHelper> commitsInModel;
    private List<BranchHelper> branchesInModel;
    private List<TagHelper> tagsInModel;

    // A list of tags that haven't been pushed yet
    public List<TagHelper> tagsToBePushed;

    static final Logger logger = LogManager.getLogger();

    /**
     * Constructs a new commit tree model that supplies the data for the given
     * view
     * @param model the model with which this class accesses the commits
     * @param view the view that will be updated with the new graph
     */
    public CommitTreeModel(SessionModel model, CommitTreePanelView view){
        this.sessionModel = model;
        this.view = view;
        this.view.setName("Generic commit tree");
        CommitTreeController.allCommitTreeModels.add(this);
        branchMap = new HashMap<>();
        this.commitsInModel = new ArrayList<>();
    }

    /**
     * Gets all commits tracked by this model and updates branch information
     * @return a list of all commits tracked by this model
     */
    private List<CommitHelper> getAllCommits(){
        if(this.sessionModel != null){
            RepoHelper repo = this.sessionModel.getCurrentRepoHelper();
            if(repo != null){
                List<CommitHelper> commits = getAllCommits(repo);
                this.branches = getAllBranches(repo);
                branchMap = new HashMap<>();
                for(BranchHelper branch : branches) branchMap.put(branch.getBranchName(),branch);
                return commits;
            }
        }
        return new ArrayList<>();
    }

    /**
     * Gets any commits tracked by this model that haven't yet been recorded, and
     * updates branch information
     * @return a list of all new commits since the last update
     * @throws GitAPIException
     * @throws IOException
     */
    private List<CommitHelper> getNewCommits() throws GitAPIException, IOException{
        if(this.sessionModel != null){
            RepoHelper repo = this.sessionModel.getCurrentRepoHelper();
            if(repo != null){
                List<CommitHelper> commits = getNewCommits(repo, branchMap);
                this.branches = getAllBranches(repo);
                branchMap = new HashMap<>();
                for(BranchHelper branch : branches) branchMap.put(branch.getBranchName(),branch);
                return commits;
            }
        }
        return new ArrayList<>();
    }

    /**
     * @param repoHelper the repository to get the branches from
     * @return a list of all branches tracked by this model
     */
    protected abstract List<BranchHelper> getAllBranches(RepoHelper repoHelper);

    /**
     * Gets all local and remote branches from the repo helper, used for making the tree pretty
     * @param repoHelper the helper to get all branches from
     * @return a list of all local and remote branches
     */
    public List<BranchHelper> getLocalRemoteBranches(RepoHelper repoHelper) {
        return repoHelper.getBranchModel().getAllBranches();
    }

    /**
     * @param repoHelper the repository to get the commits from
     * @return a list of all commits tracked by this model
     */
    protected abstract List<CommitHelper> getAllCommits(RepoHelper repoHelper);

    /**
     * @param repoHelper the repository to get the commits from
     * @param oldBranches the branches already known about
     * @return a list of all commits tracked by this model that haven't been added to the tree
     * @throws GitAPIException
     * @throws IOException
     */
    protected abstract List<CommitHelper> getNewCommits(RepoHelper repoHelper, Map<String, BranchHelper> oldBranches) throws GitAPIException, IOException;

    /**
     * @param id the id to check
     * @return true if the given id corresponds to a commit in the tree, false otherwise
     */
    public boolean containsID(String id){
        return treeGraph != null && treeGraph.treeGraphModel.containsID(id);
    }

    /**
     * Initializes the treeGraph, unselects any previously selected commit,
     * and then adds all commits tracked by this model to the tree
     */
    public synchronized void init(){
        treeGraph = this.createNewTreeGraph();

        CommitTreeController.resetSelection();

        this.addAllCommitsToTree();
        this.initView();
    }

    /**
     * Checks for new commits to add to the tree, and notifies the
     * CommitTreeController that an update is needed if there are any
     * @throws GitAPIException
     * @throws IOException
     */
    public synchronized void update1() throws GitAPIException, IOException{
        if(this.addNewCommitsToTree()){
            this.updateView();
        }
    }

    public synchronized void update() throws GitAPIException, IOException {
        // Get the changes between this model and the repo after updating the repo
        System.out.println("update called");
        this.sessionModel.getCurrentRepoHelper().updateModel();
        UpdateModel updates = this.getChanges();

        if (!updates.hasChanges()) return;
        System.out.println("there are some changes!");
        for (CommitHelper helper: updates.getCommitsToAdd())
            System.out.println(helper.getId());
        this.addCommitsToTree(updates.getCommitsToAdd());
        this.removeCommitsFromTree(updates.getCommitsToRemove());

        this.updateView();
    }


    /**
     * Helper method that checks for differences between a commit tree model and a repo model
     *
     * @return an update model that has all the differences between these
     *
     * TODO: tags and branches
     */
    public UpdateModel getChanges() {
        UpdateModel updateModel = new UpdateModel();
        System.out.println("changes called");
        // Added commits are all commits in the current repo helper that aren't in the model's list
        List<CommitHelper> commitsToAdd = new ArrayList<>(this.getAllCommits());
        commitsToAdd.removeAll(this.getCommitsInModel());
        updateModel.setCommitsToAdd(commitsToAdd);

        // Removed commits are those in the model, but not in the current repo helper
        List<CommitHelper> commitsToRemove = new ArrayList<>(this.commitsInModel);
        commitsToRemove.removeAll(this.getAllCommits());
        updateModel.setCommitsToRemove(commitsToRemove);

        return updateModel;
    }

    /**
     * Updates the view for the commit tree model.
     * WARNING: This is expensive, use only when necessary
     * @throws IOException
     */
    public synchronized void forceUpdate() throws GitAPIException, IOException {
        this.addNewCommitsToTree();
        this.updateView();
    }

    /**
     * Adds a pseudo-cell of type InvisibleCell to the treeGraph.
     * @param id the id of the cell to add
     */
    public void addInvisibleCommit(String id){
        CommitHelper invisCommit = sessionModel.getCurrentRepoHelper().getCommit(id);

        if (invisCommit != null) {

            for(CommitHelper c : invisCommit.getParents()){
                if(!treeGraph.treeGraphModel.containsID(c.getId())){
                    addInvisibleCommit(c.getId());
                }
            }

            this.addCommitToTree(invisCommit, invisCommit.getParents(),
                    treeGraph.treeGraphModel, false);

            // If there are tags in the repo that haven't been pushed, allow them to be pushed
            if (invisCommit.getTags() != null) {
                if (tagsToBePushed == null)
                    tagsToBePushed = new ArrayList<>();
                tagsToBePushed.addAll(invisCommit.getTags());
            }
        }
    }

    /**
     * Gets all commits tracked by this model and adds them to the tree
     * @return true if the tree was updated, otherwise false
     */
    private boolean addAllCommitsToTree() {
        return this.addCommitsToTree(this.getAllCommits());
    }

    /**
     * Gets all commits tracked by this model that haven't been added to the tree,
     * and adds them
     * @return true if the tree was updated, otherwise false
     * @throws GitAPIException
     * @throws IOException
     */
    private boolean addNewCommitsToTree() throws GitAPIException, IOException{
        return this.addCommitsToTree(this.getNewCommits());
    }

    /**
     * Adds the given list of commits to the treeGraph
     * @param commits the commits to add
     * @return true if commits where added, else false
     */
    private boolean addCommitsToTree(List<CommitHelper> commits){
        if(commits.size() == 0) return false;

        for(CommitHelper curCommitHelper : commits){
            List<CommitHelper> parents = curCommitHelper.getParents();
            this.addCommitToTree(curCommitHelper, parents, treeGraph.treeGraphModel, true);
        }

        return true;
    }

    /**
     * Removes the given list of commits from the treeGraph
     * @param commits the commits to remove
     * @return true if commits were removed, else false
     */
    private boolean removeCommitsFromTree(List<CommitHelper> commits){
        if(commits.size() == 0) return false;

        for(CommitHelper curCommitHelper : commits)
            this.removeCommitFromTree(curCommitHelper, treeGraph.treeGraphModel);

        return true;
    }

    /**
     * Creates a new TreeGraph with a new model. Updates the list
     * of all models accordingly
     * @return the newly created graph
     */
    private TreeGraph createNewTreeGraph(){
        TreeGraphModel graphModel = new TreeGraphModel();
        treeGraph = new TreeGraph(graphModel);
        return treeGraph;
    }

    /**
     * Adds a single commit to the tree with the given parents. Ensures the given parents are
     * already added to the tree, and if they aren't, adds them
     * @param commitHelper the commit to be added
     * @param parents a list of this commit's parents
     * @param graphModel the treeGraphModel to add the commit to
     */
    private void addCommitToTree(CommitHelper commitHelper, List<CommitHelper> parents, TreeGraphModel graphModel, boolean visible){
        List<String> parentIds = new ArrayList<>(parents.size());
        this.commitsInModel.add(commitHelper);

        for(CommitHelper parent : parents){
            if(!graphModel.containsID(RepoHelper.getCommitId(parent))){
                addCommitToTree(parent, parent.getParents(), graphModel, visible);
            }
            parentIds.add(RepoHelper.getCommitId(parent));
        }

        String commitID = RepoHelper.getCommitId(commitHelper);
        if(graphModel.containsID(commitID) && graphModel.isVisible(commitID)){
            return;
        }

        RepoHelper repo = sessionModel.getCurrentRepoHelper();
        String displayLabel = repo.getCommitDescriptorString(commitHelper, false);
        List<String> branchLabels = repo.getBranchModel().getBranchesWithHead(commitHelper);

        graphModel.addCell(commitID, commitHelper.getWhen().getTime(), displayLabel, branchLabels, getContextMenu(commitHelper), parentIds, visible);
    }


    /**
     * Removes a single commit from the tree
     *
     * @param commitHelper the commit to remove
     * @param graphModel the graph model to remove the commit from
     */
    private void removeCommitFromTree(CommitHelper commitHelper, TreeGraphModel graphModel){
        String commitID = RepoHelper.getCommitId(commitHelper);
        this.commitsInModel.remove(commitHelper);

        if(graphModel.containsID(commitID) && graphModel.isVisible(commitID))
            graphModel.removeCell(commitID);
    }


    /**
     * Constructs and returns a context menu corresponding to the given commit. Will
     * be shown on right click in the tree diagram
     * @param commit the commit for which this context menu is for
     * @return the context menu for the commit
     */
    private ContextMenu getContextMenu(CommitHelper commit){
        ContextMenu contextMenu = new ContextMenu();

        MenuItem infoItem = new MenuItem("Show Info");
        infoItem.setOnAction(event -> {
            logger.info("Showed info");
            CommitTreeController.selectCommit(commit.getId(), false, false, false);
        });
        infoItem.disableProperty().bind(CommitTreeController.selectedIDProperty().isEqualTo(commit.getId()));

        Menu relativesMenu = new Menu("Show Relatives");

        CheckMenuItem showEdgesItem = new CheckMenuItem("Show Only Relatives' Connections");
        showEdgesItem.setDisable(true);

        MenuItem parentsItem = new MenuItem("Parents");
        parentsItem.setOnAction(event -> {
            logger.info("Selected see parents");
            CommitTreeController.selectCommit(commit.getId(), true, false, false);
        });

        MenuItem childrenItem = new MenuItem("Children");
        childrenItem.setOnAction(event -> {
            logger.info("Selected see children");
            CommitTreeController.selectCommit(commit.getId(), false, true, false);
        });

        MenuItem parentsAndChildrenItem = new MenuItem("Both");
        parentsAndChildrenItem.setOnAction(event -> {
            logger.info("Selected see children and parents");
            CommitTreeController.selectCommit(commit.getId(), true, true, false);
        });

        MenuItem allAncestorsItem = new MenuItem("Ancestors");
        allAncestorsItem.setOnAction(event -> {
            logger.info("Selected see ancestors");
            CommitTreeController.selectCommit(commit.getId(), true, false, true);
        });

        MenuItem allDescendantsItem = new MenuItem("Descendants");
        allDescendantsItem.setOnAction(event -> {
            logger.info("Selected see descendants");
            CommitTreeController.selectCommit(commit.getId(), false, true, true);
        });

        relativesMenu.getItems().setAll(parentsItem, childrenItem, parentsAndChildrenItem,
                new SeparatorMenuItem(), allAncestorsItem, allDescendantsItem,
                new SeparatorMenuItem(), showEdgesItem);

        MenuItem mergeItem = new MenuItem("Merge with...");
        mergeItem.setDisable(true);

        MenuItem branchItem = new MenuItem("Branch from...");
        branchItem.setDisable(true);

        contextMenu.getItems().addAll(infoItem, relativesMenu,
                new SeparatorMenuItem(), mergeItem, branchItem);

        return contextMenu;
    }

    /**
     * Updates the corresponding view if possible
     */
    private void updateView() throws IOException{
        if(this.sessionModel != null && this.sessionModel.getCurrentRepoHelper() != null){
            CommitTreeController.update(sessionModel.getCurrentRepoHelper());
        }else{
            view.displayEmptyView();
        }
    }

    /**
     * Initializes the corresponding view if possible
     */
    private void initView(){
        if(this.sessionModel != null && this.sessionModel.getCurrentRepoHelper() != null){
            CommitTreeController.init(this);
        }else{
            view.displayEmptyView();
        }
    }

    /**
     * Marks the commit with the given id as the head of a tracked branch in the tree
     * @param commitId the id of the commit to mark
     */
    public void setCommitAsTrackedBranch(String commitId){
        treeGraph.treeGraphModel.setCellShape(commitId, Cell.TRACKED_BRANCH_HEAD_SHAPE);

        RepoHelper repo = sessionModel.getCurrentRepoHelper();
        String displayLabel = repo.getCommitDescriptorString(commitId, false);
        List<String> branchLabels = repo.getBranchModel().getBranchesWithHead(commitId);
        treeGraph.treeGraphModel.setCellLabels(commitId, displayLabel, branchLabels);
    }

    /**
     * Marks the commit with the given id as the head of a tracked branch in the tree
     * @param commitId the id of the commit to mark
     */
    public void setCommitAsTrackedBranch(ObjectId commitId){
        setCommitAsTrackedBranch(commitId.getName());
    }

    /**
     * Marks the commit with the given id as the head of an untracked branch in the tree
     * @param commitId the id of the commit to mark
     */
    public void setCommitAsUntrackedBranch(String commitId){
        treeGraph.treeGraphModel.setCellShape(commitId, Cell.UNTRACKED_BRANCH_HEAD_SHAPE);

        RepoHelper repo = sessionModel.getCurrentRepoHelper();
        String displayLabel = repo.getCommitDescriptorString(commitId, false);
        List<String> branchLabels = repo.getBranchModel().getBranchesWithHead(commitId);
        treeGraph.treeGraphModel.setCellLabels(commitId, displayLabel, branchLabels);
    }

    /**
     * Marks the commit with the given id as the head of an untracked branch in the tree
     * @param commitId the id of the commit to mark
     */
    public void setCommitAsUntrackedBranch(ObjectId commitId){
        setCommitAsUntrackedBranch(commitId.getName());
    }

    /**
     * Forgets information about tracked/untracked branch heads in the tree
     */
    public void resetBranchHeads(boolean updateLabels){
        List<String> resetIDs = treeGraph.treeGraphModel.resetCellShapes();
        RepoHelper repo = sessionModel.getCurrentRepoHelper();
        if(updateLabels){
            for(String id : resetIDs){
                String displayLabel = repo.getCommitDescriptorString(id, false);
                List<String> branchLabels = repo.getBranchModel().getBranchesWithHead(id);
                treeGraph.treeGraphModel.setCellLabels(id, displayLabel, branchLabels);
            }
        }
    }

    /**
     * @return the branches tracked by this model
     */
    public List<BranchHelper> getBranches(){
        return branches;
    }

    public List<TagHelper> getTagsToBePushed() {
        return tagsToBePushed;
    }

    public abstract String getViewName();



    public List<CommitHelper> getCommitsInModel() { return this.commitsInModel; }

    public List<BranchHelper> getBranchesInModel() { return this.branchesInModel; }

    public List<TagHelper> getTagsInModel() { return this.tagsInModel; }
}
