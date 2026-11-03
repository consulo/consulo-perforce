// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.jobs;

import consulo.application.Application;
import consulo.application.progress.ProgressManager;
import consulo.localize.LocalizeValue;
import consulo.perforce.localize.PerforceLocalize;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.ActionToolbar;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.awt.ColumnInfo;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.ScrollPaneFactory;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.table.ListTableModel;
import consulo.ui.ex.awt.table.TableView;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.ex.popup.JBPopup;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.image.Image;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.Lists;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.SimpleReference;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.CommitContext;
import consulo.versionControlSystem.change.LocalChangeList;
import consulo.versionControlSystem.checkin.CheckinChangeListSpecificComponent;
import org.jetbrains.idea.perforce.application.ConnectionKey;
import org.jetbrains.idea.perforce.application.PerforceVcs;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jspecify.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.jetbrains.idea.perforce.application.PerforceCheckinEnvironment.LINKED_JOBS_KEY;

public class PerforceCheckinComponent implements CheckinChangeListSpecificComponent, JobsTablePresentation {
    private final Project myProject;
    private final CommitContext myCommitContext;
    private final JPanel myPanel;
    private consulo.ui.@Nullable Component myUIComponent;

    private @Nullable LocalChangeList myCurrent;
    private final Map<LocalChangeList, Map<ConnectionKey, List<PerforceJob>>> myCache;
    private final PerforceVcs myVcs;
    private final Map<ConnectionKey, P4JobsLogicConn> myConnMap;
    private final AdderRemover myAdderRemover;

    private final JobsMasterDetails myDetails;

    @RequiredUIAccess
    public PerforceCheckinComponent(Project project, CommitContext commitContext) {
        myProject = project;
        myCommitContext = commitContext;
        myVcs = PerforceVcs.getInstance(myProject);
        myConnMap = new HashMap<>();
        myCache = new HashMap<>();

        myDetails = new JobsMasterDetails(myProject) {
            @Override
            protected Dimension getPanelPreferredSize() {
                return JBUI.size(200, 70);
            }
        };
        myDetails.onlyMain();

        myPanel = new JPanel(new GridBagLayout());
        initUI();
        myAdderRemover = new WiseAdderRemover(myProject, this);
    }

    @Override
    public void refreshJobs(@Nullable PerforceJob job) {
        Application application = Application.get();
        if (application.isDispatchThread()) {
            throw new IllegalStateException("Must not be called on the UI thread");
        }
        LocalChangeList current = myCurrent;
        if (current != null && !current.hasDefaultName()) {
            Map<ConnectionKey, List<PerforceJob>> data = loadOnSelect(current);
            myCache.put(current, data);
            application.invokeLater(() -> setItems(ContainerUtil.flatten(data.values())));
        }
    }

    @RequiredUIAccess
    @Override
    public void addJob(PerforceJob job) {
        // todo todo 2
        Map<ConnectionKey, List<PerforceJob>> data = getCurrentListJobs();
        if (data != null) {
            List<PerforceJob> jobs = data.computeIfAbsent(job.getConnectionKey(), k -> new ArrayList<>());
            jobs.add(job);
            saveJobsInCache(data);
            setItems(ContainerUtil.flatten(data.values()));
        }
    }

    private @Nullable Map<ConnectionKey, List<PerforceJob>> getCurrentListJobs() {
        LocalChangeList current = myCurrent;
        if (current == null) {
            return null;
        }
        if (current.hasDefaultName()) {
            return myVcs.getDefaultAssociated();
        }
        return myCache.get(current);
    }

    @RequiredUIAccess
    @Override
    public void removeSelectedJobs() {
        List<PerforceJob> jobs = myDetails.getSelectedJobs();
        if (jobs.isEmpty()) {
            return;
        }
        myDetails.removeSelectedJobs();

        Map<ConnectionKey, List<PerforceJob>> data = getCurrentListJobs();
        if (data != null) {
            for (PerforceJob job : jobs) {
                List<PerforceJob> connectionJobs = data.get(job.getConnectionKey());
                if (connectionJobs != null) {
                    connectionJobs.remove(job);
                }
            }
            setItems(ContainerUtil.flatten(data.values()));
        }
    }

    private JComponent createToolbar() {
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(new UnlinkSelectedAction());

        group.add(new AnAction(PerforceLocalize.jobEditAssociatedTitle(), PerforceLocalize.jobEditAssociated(),
            PlatformIconGroup.actionsEditsource()) {
            @RequiredUIAccess
            @Override
            public void actionPerformed(AnActionEvent e) {
                LocalChangeList current = myCurrent;
                Map<ConnectionKey, List<PerforceJob>> data = getCurrentListJobs();
                if (current == null || data == null) {
                    return;
                }
                ensureDefaultConnections();

                EditChangelistJobsDialog dialog = new EditChangelistJobsDialog(myProject, current, current.hasDefaultName(), myConnMap, data);
                dialog.show();
                Map<ConnectionKey, List<PerforceJob>> jobs = dialog.getJobs();
                saveJobsInCache(jobs);
                setItems(ContainerUtil.flatten(jobs.values()));
            }
        });
        group.add(new MyTextFieldAction(PerforceLocalize.jobSearchInViewTitle(), PerforceLocalize.jobSearchInView(),
            PlatformIconGroup.generalAdd()));

        ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("PerforceCheckIn", group, true);
        actionToolbar.setTargetComponent(myPanel);
        return actionToolbar.getComponent();
    }

    private final class UnlinkSelectedAction extends AnAction implements AnActionWithSyncUpdate {
        private UnlinkSelectedAction() {
            super(PerforceLocalize.jobUnlinkSelectedTitle(), PerforceLocalize.jobUnlinkSelected(), PlatformIconGroup.generalRemove());
        }

        @RequiredUIAccess
        @Override
        public void actionPerformed(AnActionEvent e) {
            LocalChangeList current = myCurrent;
            if (current == null) {
                return;
            }
            for (PerforceJob job : myDetails.getSelectedJobs()) {
                VcsException vcsException = myAdderRemover.remove(job, current, myProject);
                if (vcsException != null) {
                    new ErrorReporter(PerforceLocalize.jobRemovingFromChangelist()).report(myProject, vcsException);
                }
            }
        }

        @Override
        public void update(AnActionEvent e) {
            e.getPresentation().setEnabled(!myDetails.getSelectedJobs().isEmpty());
        }
    }

    private final class MyTextFieldAction extends TextFieldAction {
        private MyTextFieldAction(LocalizeValue text, LocalizeValue description, Image icon) {
            super(text, description, icon, 20);
        }

        @Override
        public void perform() {
            searchByJobviewAndFilter();
        }

        private void searchByJobviewAndFilter() {
            String text = myField.getText().trim();
            if (!text.isEmpty()) {
                ensureDefaultConnections();

                LocalChangeList current = myCurrent;
                if (current != null) {
                    Map<ConnectionKey, P4Connection> p4ConnectionMap = ConnectionSelector.getConnections(myProject, current);
                    if (p4ConnectionMap.isEmpty()) {
                        return;
                    }
                    if (p4ConnectionMap.size() == 1) {
                        Map.Entry<ConnectionKey, P4Connection> entry = p4ConnectionMap.entrySet().iterator().next();
                        addImpl(text, Pair.create(entry.getKey(), entry.getValue()));
                    }
                    else {
                        ConnectionSelector.selectConnection(p4ConnectionMap, connectionKey -> {
                            if (connectionKey != null) {
                                P4Connection connection = p4ConnectionMap.get(connectionKey);
                                if (connection != null) {
                                    addImpl(text, Pair.create(connectionKey, connection));
                                }
                            }
                        });
                    }
                }
            }
        }

        private void addImpl(String text, Pair<ConnectionKey, P4Connection> pair) {
            P4JobsLogicConn connMap = myConnMap.get(pair.getFirst());
            if (connMap != null) {
                JobsWorker worker = new JobsWorker(myProject);
                JobViewSearchSpecificator searchSpecificator = new JobViewSearchSpecificator(connMap.getJobView(), text);
                P4Connection connection = pair.getSecond();
                ConnectionKey key = pair.getFirst();
                PerforceJobSpecification spec = connMap.getSpec();
                List<PerforceJob> jobs = getJobsUnderProgress(worker, searchSpecificator, connection, key, spec);
                if (jobs == null) {
                    return;
                }
                if (jobs.isEmpty()) {
                    Messages.showMessageDialog(myProject, PerforceLocalize.jobNoMatchingPattern().get(), PerforceLocalize.jobAdd().get(),
                        Messages.getInformationIcon());
                    return;
                }

                Consumer<PerforceJob> consumer = perforceJob -> {
                    LocalChangeList current = myCurrent;
                    if (current != null) {
                        //noinspection ThrowableNotThrown
                        myAdderRemover.add(perforceJob, current, myProject);
                    }
                    myField.setText("");
                };

                if (jobs.size() > 1) {
                    showListPopup(jobs, myField, consumer, searchSpecificator.getMaxCount());
                }
                else {
                    consumer.accept(jobs.get(0));
                }
            }
        }
    }

    /**
     * @return found jobs, or {@code null} if the search was canceled or failed
     */
    public static @Nullable List<PerforceJob> getJobsUnderProgress(
        JobsWorker worker,
        JobsSearchSpecificator searchSpecificator,
        P4Connection connection,
        ConnectionKey key,
        PerforceJobSpecification spec
    ) {
        try {
            return ProgressManager.getInstance().runProcessWithProgressSynchronously(
                () -> worker.getJobs(spec, searchSpecificator, connection, key),
                PerforceLocalize.jobSearchingJobs(),
                true,
                worker.getProject()
            );
        }
        catch (VcsException e1) {
            new ErrorReporter(PerforceLocalize.jobSearchingJobsToAdd()).report(worker.getProject(), e1);
            return null;
        }
    }

    @RequiredUIAccess
    private void initUI() {
        GridBagConstraints gb = DefaultGb.create();
        gb.insets = new Insets(0, 0, 0, 0);
        gb.anchor = GridBagConstraints.WEST;

        // todo prompt message when jobs ought to exist for CL
        JLabel jobsPrompt = new JLabel(PerforceLocalize.jobJobs().get());
        gb.gridwidth = 3;
        myPanel.add(jobsPrompt, gb);

        ++gb.gridy;
        gb.fill = GridBagConstraints.HORIZONTAL;
        myPanel.add(createToolbar(), gb);

        ++gb.gridy;
        gb.fill = GridBagConstraints.BOTH;
        JComponent comp = myDetails.createComponent();
        myPanel.add(comp, gb);
    }

    private void saveJobsInCache(Map<ConnectionKey, List<PerforceJob>> jobs) {
        LocalChangeList current = myCurrent;
        if (current == null) {
            return;
        }
        if (current.hasDefaultName()) {
            myVcs.setDefaultAssociated(jobs);
        }
        else {
            myCache.put(current, jobs);
        }
    }

    private static final ColumnInfo<PerforceJob, String> JOB = new ColumnInfo<>(PerforceLocalize.job()) {
        @Override
        public String valueOf(PerforceJob perforceJob) {
            return perforceJob.getName();
        }
    };
    private static final ColumnInfo<PerforceJob, String> STATUS = new ColumnInfo<>(PerforceLocalize.jobStatus()) {
        @Override
        public @Nullable String valueOf(PerforceJob perforceJob) {
            PerforceJobFieldValue field = perforceJob.getValueForStandardField(StandardJobFields.status);
            return field == null ? null : field.getValue();
        }
    };
    private static final ColumnInfo<PerforceJob, String> USER = new ColumnInfo<>(PerforceLocalize.jobUser()) {
        @Override
        public @Nullable String valueOf(PerforceJob perforceJob) {
            PerforceJobFieldValue field = perforceJob.getValueForStandardField(StandardJobFields.user);
            return field == null ? null : field.getValue();
        }
    };
    private static final ColumnInfo<PerforceJob, String> DATE = new ColumnInfo<>(PerforceLocalize.jobDate()) {
        @Override
        public @Nullable String valueOf(PerforceJob perforceJob) {
            PerforceJobFieldValue field = perforceJob.getValueForStandardField(StandardJobFields.date);
            return field == null ? null : field.getValue();
        }
    };
    private static final ColumnInfo<PerforceJob, String> DESCRIPTION = new ColumnInfo<>(PerforceLocalize.jobDescription()) {
        @Override
        public @Nullable String valueOf(PerforceJob perforceJob) {
            PerforceJobFieldValue field = perforceJob.getValueForStandardField(StandardJobFields.description);
            return field == null ? null : field.getValue();
        }
    };

    private static final ColumnInfo[] COLUMNS = new ColumnInfo[]{JOB, STATUS, USER, DATE, DESCRIPTION};

    private JPanel createSouthPanel(TableView<PerforceJob> table, @Nullable String warningText) {
        JPanel southPanel = new JPanel(new BorderLayout());

        JPanel stuffPanel = new JPanel(new BorderLayout());

        if (warningText != null) {
            stuffPanel.setPreferredSize(JBUI.size(300, 120));
            southPanel.add(stuffPanel, BorderLayout.NORTH);
            JLabel warningLabel = new JLabel(warningText);
            warningLabel.setFont(warningLabel.getFont().deriveFont(Font.BOLD));
            southPanel.add(warningLabel, BorderLayout.SOUTH);
        }
        else {
            southPanel.add(stuffPanel);
        }

        southPanel.setPreferredSize(JBUI.size(300, 140));
        table.getSelectionModel().addListSelectionListener(e -> {
            PerforceJob job = table.getSelectedObject();
            if (job == null) {
                stuffPanel.removeAll();
            }
            else {
                SelfLoadingJobDetailsPanel panel = new SelfLoadingJobDetailsPanel(myProject, job);
                stuffPanel.removeAll();
                JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(panel.getPanel());
                stuffPanel.add(scrollPane);
            }
            southPanel.revalidate();
            southPanel.repaint();
        });
        return southPanel;
    }

    /**
     * Shows a popup with a table of the found jobs and a details panel of the selected job below it;
     * Enter or a click on a row chooses the job.
     */
    @SuppressWarnings("unchecked")
    private void showListPopup(List<PerforceJob> jobs, Component component, Consumer<PerforceJob> consumer, int maxCount) {
        List<PerforceJob> jobsNarrowed = Lists.getFirstItems(jobs, maxCount);
        TableView<PerforceJob> table = new TableView<>(new ListTableModel<>(COLUMNS, jobsNarrowed, 0));
        table.setShowHorizontalLines(false);
        table.setTableHeader(null);

        if (table.getModel().getRowCount() == 0) {
            table.clearSelection();
        }

        table.setMinimumSize(JBUI.size(300, 50));

        JPanel content = new JPanel(new BorderLayout());
        content.add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER);
        content.add(
            createSouthPanel(table, (jobs.size() > maxCount) ? PerforceLocalize.perforceJobsSearchLimitExceededWarning(maxCount).get() : null),
            BorderLayout.SOUTH
        );

        JBPopup popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(content, table)
            .setTitle(PerforceLocalize.perforceJobsSelectOne())
            .setResizable(true)
            .setMovable(true)
            .setRequestFocus(true)
            .setDimensionServiceKey(myProject, "org.jetbrains.idea.perforce.perforce.jobs.PerforceCheckinComponent.SelectOneJob", false)
            .setMinSize(JBUI.size(300, 300))
            .createPopup();

        SimpleReference<Boolean> chosen = SimpleReference.create(false);
        Runnable runnable = () -> {
            if (chosen.requiredGet()) {
                return;
            }
            PerforceJob job = table.getSelectedObject();
            if (job != null) {
                chosen.set(true);
                popup.cancel();
                consumer.accept(job);
            }
        };

        table.registerKeyboardAction(e -> runnable.run(), KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED) && !e.isConsumed()) {
                    runnable.run();
                }
            }
        });

        popup.showUnderneathOf(component);
    }

    private void ensureDefaultConnections() {
        LocalChangeList current = myCurrent;
        if (current != null && current.hasDefaultName()) {
            JobDetailsLoader loader = new JobDetailsLoader(myProject);
            loader.fillConnections(current, myConnMap);
        }
    }

    @RequiredUIAccess
    @Override
    public consulo.ui.Component getUIComponent() {
        consulo.ui.Component component = myUIComponent;
        if (component == null) {
            component = TargetAWT.wrap(myPanel);
            myUIComponent = component;
        }
        return component;
    }

    @Override
    public void refresh() {
    }

    @Override
    public void saveState() {
        LocalChangeList current = myCurrent;
        if (current != null && current.hasDefaultName()) {
            keepDefaultListJobs();
        }
        saveJobsToContext(getCurrentListJobs());
    }

    @Override
    public void restoreState() {
    }

    @RequiredUIAccess
    @Override
    public void onChangeListSelected(LocalChangeList list) {
        if (Comparing.equal(list, myCurrent)) {
            return;
        }

        LocalChangeList previous = myCurrent;
        if (!list.hasDefaultName() && previous != null && previous.hasDefaultName()) {
            keepDefaultListJobs();
        }
        myCurrent = list;

        Map<ConnectionKey, List<PerforceJob>> data;
        if (list.hasDefaultName()) {
            correctDefaultAssociated(list);
            data = myVcs.getDefaultAssociated();

            List<PerforceJob> filtered = new ArrayList<>();
            for (Map.Entry<ConnectionKey, List<PerforceJob>> entry : data.entrySet()) {
                filtered.addAll(entry.getValue());
            }

            setItems(filtered);
        }
        else {
            data = myCache.get(list);
            if (data == null) {
                data = loadOnSelect(list);
                myCache.put(list, data);
            }
            setItems(ContainerUtil.flatten(data.values()));
        }

        saveJobsToContext(data);
    }

    private void saveJobsToContext(@Nullable Map<ConnectionKey, List<PerforceJob>> data) {
        myCommitContext.putUserData(LINKED_JOBS_KEY, data == null ? null : ContainerUtil.flatten(data.values()));
    }

    private void correctDefaultAssociated(LocalChangeList defaultList) {
        Map<ConnectionKey, List<PerforceJob>> data = myVcs.getDefaultAssociated();

        Map<ConnectionKey, P4Connection> map = ConnectionSelector.getConnections(myProject, defaultList);

        Map<ConnectionKey, List<PerforceJob>> filtered = new HashMap<>();
        for (Map.Entry<ConnectionKey, List<PerforceJob>> entry : data.entrySet()) {
            if (map.containsKey(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }

        myVcs.setDefaultAssociated(filtered);
    }

    private void keepDefaultListJobs() {
        List<PerforceJob> perforceJobs = myDetails.getJobs();
        Map<ConnectionKey, List<PerforceJob>> jobs = new HashMap<>();
        for (PerforceJob job : perforceJobs) {
            jobs.computeIfAbsent(job.getConnectionKey(), k -> new ArrayList<>()).add(job);
        }
        myVcs.setDefaultAssociated(jobs);
    }

    private Map<ConnectionKey, List<PerforceJob>> loadOnSelect(LocalChangeList list) {
        JobDetailsLoader loader = new JobDetailsLoader(myProject);
        Map<ConnectionKey, List<PerforceJob>> perforceJobs = new HashMap<>();
        loader.loadJobsForList(list, myConnMap, perforceJobs);
        return perforceJobs;
    }

    @RequiredUIAccess
    private void setItems(List<PerforceJob> items) {
        myDetails.fillTree(items, items.isEmpty() ? null : items.get(0));
    }
}
