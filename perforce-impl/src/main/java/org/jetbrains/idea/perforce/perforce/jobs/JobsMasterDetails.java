// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

import consulo.configurable.ConfigurationException;
import consulo.configurable.NamedConfigurable;
import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.MasterDetailsComponent;
import consulo.ui.ex.awt.MasterDetailsStateService;
import consulo.ui.ex.awt.tree.ColoredTreeCellRenderer;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.event.ListSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class JobsMasterDetails extends MasterDetailsComponent {
    private final Project myProject;
    private @Nullable ListSelectionListener myListSelectionListener;

    @RequiredUIAccess
    public JobsMasterDetails(Project project) {
        super(() -> MasterDetailsStateService.getInstance(project));
        myProject = project;
        initTree();
        init();

        getSplitter().setProportion(0.3f);
    }

    public void onlyMain() {
        getSplitter().setProportion(0f);
        getSplitter().setSecondComponent(null);
    }

    public JComponent getPreferredFocusTarget() {
        return myTree;
    }

    public void setSelectionListener(ListSelectionListener selectionListener) {
        myListSelectionListener = selectionListener;
    }

    /**
     * Creates the Swing component of this master-details panel.
     * The job nodes are kept after the owning dialog is closed, so that the selected/linked jobs can still be read.
     */
    @RequiredUIAccess
    public JComponent createComponent() {
        initialize();
        Disposable uiDisposable = Disposable.newDisposable();
        return (JComponent)Objects.requireNonNull(TargetAWT.to(createUIComponent(uiDisposable)));
    }

    @Override
    public LocalizeValue getDisplayName() {
        return PerforceLocalize.configurableJobsmasterdetailsDisplayName();
    }

    @Override
    protected void processRemovedItems() {
    }

    @Override
    protected boolean wasObjectStored(Object editableObject) {
        return false;
    }

    public List<PerforceJob> getSelectedJobs() {
        MyNode[] nodes = myTree.getSelectedNodes(MyNode.class, null);
        List<PerforceJob> result = new ArrayList<>();
        for (MyNode node : nodes) {
            result.add((PerforceJob)node.getConfigurable().getEditableObject());
        }
        return result;
    }

    @RequiredUIAccess
    public void removeSelectedJobs() {
        TreePath[] paths = myTree.getSelectionPaths();
        if (paths != null) {
            removePaths(paths);
        }
        if (myTree.getRowCount() > 0) {
            myTree.setSelectionRow(0);
        }
    }

    @RequiredUIAccess
    public void addJob(PerforceJob job) {
        MyConfigurable configurable = new MyConfigurable(myProject, job);
        myRoot.add(new MyNode(configurable));

        ((DefaultTreeModel)myTree.getModel()).reload(myRoot);

        myTree.setSelectionRow(myTree.getRowCount() - 1);
    }

    public List<PerforceJob> getJobs() {
        List<PerforceJob> result = new ArrayList<>();

        int childCount = myRoot.getChildCount();
        for (int i = 0; i < childCount; i++) {
            MyNode node = (MyNode)myRoot.getChildAt(i);
            result.add((PerforceJob)node.getConfigurable().getEditableObject());
        }
        return result;
    }

    @Override
    protected Dimension getPanelPreferredSize() {
        return JBUI.size(400, 200);
    }

    private void init() {
        myTree.setCellRenderer(new PerforceJobCellRenderer());
        myTree.setShowsRootHandles(false);
        myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        myTree.addTreeSelectionListener(e -> {
            ListSelectionListener listener = myListSelectionListener;
            if (listener != null) {
                listener.valueChanged(null);
            }
        });
    }

    @RequiredUIAccess
    public void fillTree(List<PerforceJob> jobs, @Nullable PerforceJob selectJob) {
        myRoot.removeAllChildren();

        int idx = -1;
        for (int i = 0; i < jobs.size(); i++) {
            PerforceJob job = jobs.get(i);
            MyConfigurable configurable = new MyConfigurable(myProject, job);
            myRoot.add(new MyNode(configurable));
            if (selectJob != null && job.getName().equals(selectJob.getName())) {
                idx = i;
            }
        }

        ((DefaultTreeModel)myTree.getModel()).reload(myRoot);
        if (idx >= 0) {
            myTree.setSelectionRow(idx);
        }
    }

    private static final class PerforceJobCellRenderer implements TreeCellRenderer {
        private final ColoredTreeCellRenderer myTop;
        private final JPanel myPanel;

        @Override
        public Component getTreeCellRendererComponent(
            JTree tree,
            Object value,
            boolean selected,
            boolean expanded,
            boolean leaf,
            int row,
            boolean hasFocus
        ) {
            if (!(value instanceof MyNode)) {
                return myPanel;
            }

            return myTop.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        }

        private PerforceJobCellRenderer() {
            myPanel = new JPanel(new GridBagLayout());
            myPanel.setBackground(JBColor.WHITE);

            myTop = new ColoredTreeCellRenderer() {
                @Override
                public void customizeCellRenderer(
                    JTree tree,
                    Object value,
                    boolean selected,
                    boolean expanded,
                    boolean leaf,
                    int row,
                    boolean hasFocus
                ) {
                    if (!(value instanceof MyRootNode) && value instanceof MyNode node) {
                        MyConfigurable configurable = (MyConfigurable)node.getConfigurable();
                        PerforceJob job = configurable.getEditableObject();

                        append(job.getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);

                        PerforceJobFieldValue status = job.getValueForStandardField(StandardJobFields.status);
                        append(
                            " <" + (status == null ? PerforceLocalize.unknownStatus().get() : status.getValue()) + ">",
                            SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES
                        );

                        PerforceJobFieldValue desc = job.getValueForStandardField(StandardJobFields.description);
                        if (desc != null) {
                            String text = desc.getValue();
                            String shortText = text.length() > 30 ? text.substring(0, 30) + "..." : text;
                            append(" " + shortText, SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES);
                        }
                    }
                }
            };
        }
    }

    private static final class MyConfigurable extends NamedConfigurable<PerforceJob> {
        private final SelfLoadingJobDetailsPanel mySelfLoadingJobDetailsPanel;
        private final PerforceJob myJob;

        @RequiredUIAccess
        private MyConfigurable(Project project, PerforceJob job) {
            mySelfLoadingJobDetailsPanel = new SelfLoadingJobDetailsPanel(project, job);
            myJob = job;
        }

        @Override
        public void setDisplayName(String name) {
        }

        @Override
        public PerforceJob getEditableObject() {
            return myJob;
        }

        @Override
        public LocalizeValue getBannerSlogan() {
            return LocalizeValue.of(myJob.getName());
        }

        @Override
        public @Nullable Image getIcon() {
            return null;
        }

        @RequiredUIAccess
        @Override
        public consulo.ui.Component createOptionsPanel(Disposable parentDisposable) {
            return TargetAWT.wrap(mySelfLoadingJobDetailsPanel.getPanel());
        }

        @Override
        public LocalizeValue getDisplayName() {
            return LocalizeValue.of(myJob.getName());
        }

        @Override
        public @Nullable String getHelpTopic() {
            return null;
        }

        @RequiredUIAccess
        @Override
        public boolean isModified() {
            return false;
        }

        @RequiredUIAccess
        @Override
        public void apply() throws ConfigurationException {
        }
    }
}
