package org.infernus.idea.checkstyle.toolwindow;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.infernus.idea.checkstyle.CheckStyleConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * The tool window for CheckStyle scans.
 *
 * @author James Shiell
 * @version 1.0
 */
public class ToolWindowPanel extends JPanel {

    private final MouseListener treeMouseListener = new ToolWindowMouseListener();
    private final TreeSelectionListener treeSelectionListener
            = new ToolWindowSelectionListener();
    private final Project project;
    private final JTree resultsTree;
    private final DefaultMutableTreeNode visibleRootNode;

    private DefaultTreeModel treeModel;
    private boolean scrollToSource;

    /**
     * Create a tool window for the given project.
     *
     * @param project the project.
     */
    public ToolWindowPanel(final Project project) {
        super(new BorderLayout());

        this.project = project;

        final ResourceBundle resources = ResourceBundle.getBundle(
                CheckStyleConstants.RESOURCE_BUNDLE);

        setBorder(new EmptyBorder(1, 1, 1, 1));

        // Create the toolbar
        final ActionGroup actionGroup = (ActionGroup)
                ActionManager.getInstance().getAction(CheckStyleConstants.ACTION_GROUP);
        final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(
                CheckStyleConstants.ID_TOOL_WINDOW, actionGroup, false);
        add(toolbar.getComponent(), BorderLayout.WEST);

        // Create the tree
        final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(
                new ToolWindowTreeNode("root"));
        treeModel = new DefaultTreeModel(rootNode);

        resultsTree = new JTree(treeModel);
        resultsTree.addTreeSelectionListener(treeSelectionListener);
        resultsTree.addMouseListener(treeMouseListener);
        resultsTree.setCellRenderer(new ToolWindowCellRenderer());

        add(new JScrollPane(resultsTree), BorderLayout.CENTER);

        visibleRootNode = new DefaultMutableTreeNode(new ToolWindowTreeNode(
                resources.getString("plugin.results.no-scan")));
        rootNode.add(visibleRootNode);

        expandTree();

        ToolTipManager.sharedInstance().registerComponent(resultsTree);
        toolbar.getComponent().setVisible(true);
    }

    /**
     * Scroll to the error specified by the given tree path, or do nothing
     * if no error is specified.
     *
     * @param treePath the tree path to scroll to.
     */
    private void scrollToError(final TreePath treePath) {
        final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) treePath.getLastPathComponent();
        if (treeNode == null) {
            return;
        }

        final ToolWindowTreeNode nodeInfo = (ToolWindowTreeNode) treeNode.getUserObject();
        if (nodeInfo.getFile() == null || nodeInfo.getProblem() == null) {
            return; // no problem here :-)
        }

        final FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
        final FileEditor[] editor = fileEditorManager.openFile(
                nodeInfo.getFile().getVirtualFile(), true);

        if (editor != null && editor.length > 0 && editor[0] instanceof TextEditor) {
            final int offset = nodeInfo.getProblem().getStartElement().getStartOffsetInParent();
            ((TextEditor) editor[0]).getEditor().getCaretModel().moveToOffset(offset);
            ((TextEditor) editor[0]).getEditor().getScrollingModel().scrollToCaret(ScrollType.CENTER);
        }
    }

    /**
     * Should we scroll to the selected error in the editor automatically?
     *
     * @param scrollToSource true if the error should be scrolled to automatically.
     */
    public void setScrollToSource(final boolean scrollToSource) {
        this.scrollToSource = scrollToSource;
    }


    /**
     * Listen for clicks and scroll to the error's source as necessary.
     */
    protected class ToolWindowMouseListener extends MouseAdapter {

        /**
         * {@inheritDoc}
         */
        @Override
        public void mouseClicked(final MouseEvent e) {
            if (!scrollToSource || e.getClickCount() < 2) {
                return;
            }

            final TreePath treePath = resultsTree.getPathForLocation(
                    e.getX(), e.getY());

            if (treePath != null) {
                scrollToError(treePath);
            }
        }

    }

    /**
     * Listen for tree selection events and scroll to the error's source as necessary.
     */
    protected class ToolWindowSelectionListener implements TreeSelectionListener {

        /**
         * {@inheritDoc}
         */
        public void valueChanged(final TreeSelectionEvent e) {
            if (e.getPath() != null) {
                scrollToError(e.getPath());
            }
        }

    }

    /**
     * Collapse the tree so that only the root node is visible.
     */
    public void collapseTree() {
        resultsTree.collapseRow(1);
    }

    /**
     * Expand the error tree to the fullest.
     */
    public void expandTree() {
        expandTree(4);
    }

    /**
     * Expand the given tree to the given level, starting from the given node
     * and path.
     *
     * @param tree  The tree to be expanded
     * @param node  The node to start from
     * @param path  The path to start from
     * @param level The number of levels to expand to
     */
    private static void expandNode(final JTree tree,
                                   final TreeNode node,
                                   final TreePath path,
                                   final int level) {
        if (level <= 0) {
            return;
        }

        tree.expandPath(path);

        for (int i = 0; i < node.getChildCount(); ++i) {
            final TreeNode childNode = node.getChildAt(i);
            expandNode(tree, childNode, path.pathByAddingChild(childNode), level - 1);
        }
    }

    /**
     * Expand the error tree to the given level.
     *
     * @param level The level to expand to
     */
    private void expandTree(int level) {
        expandNode(resultsTree, (TreeNode) resultsTree.getModel().getRoot(),
                new TreePath(visibleRootNode), level);
    }

    /**
     * Display the passed results.
     *
     * @param results the map of checked files to problem descriptors.
     */
    public void displayResults(final Map<PsiFile, List<ProblemDescriptor>> results) {
        visibleRootNode.removeAllChildren();

        final ResourceBundle resources = ResourceBundle.getBundle(
                CheckStyleConstants.RESOURCE_BUNDLE);

        if (results == null || results.size() == 0) {
            ((ToolWindowTreeNode) visibleRootNode.getUserObject()).setText(
                    resources.getString("plugin.results.scan-no-results"));
        } else {
            final MessageFormat fileResultMessage = new MessageFormat(
                    resources.getString("plugin.results.scan-file-result"));

            int itemCount = 0;
            for (final PsiFile file : results.keySet()) {
                final DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode();
                final List<ProblemDescriptor> problems = results.get(file);
                for (final ProblemDescriptor problem : problems) {
                    final DefaultMutableTreeNode problemNode = new DefaultMutableTreeNode(
                            new ToolWindowTreeNode(file, problem));
                    fileNode.add(problemNode);
                }

                fileNode.setUserObject(new ToolWindowTreeNode(
                        fileResultMessage.format(new Object[]{file.getName(), problems.size()})));
                itemCount += problems.size();

                visibleRootNode.add(fileNode);
            }

            final MessageFormat resultsMessage = new MessageFormat(
                    resources.getString("plugin.results.scan-no-results"));
            ((ToolWindowTreeNode) visibleRootNode.getUserObject()).setText(
                    resultsMessage.format(new Object[]{itemCount, results.size()}));

            treeModel.reload();
        }

    }

}
