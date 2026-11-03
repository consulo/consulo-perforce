/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.perforce.changesBrowser;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import consulo.localize.LocalizeValue;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.CheckBox;
import consulo.ui.ValueComponent;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.event.ComponentEventListener;
import consulo.ui.event.ValueComponentEvent;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.TextFieldWithBrowseButton;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.util.VcsUtil;
import consulo.versionControlSystem.versionBrowser.ui.awt.StandardVersionFilterComponent;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jspecify.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import java.util.function.Function;

public class PerforceVersionFilterComponent extends StandardVersionFilterComponent<PerforceChangeBrowserSettings> {
    private final TextFieldWithBrowseButton myUserField;
    private final TextFieldWithBrowseButton myClientField;
    private final CheckBox myUseUserFilter;
    private final CheckBox myUseClientFilter;

    private final JPanel myPanel;
    private final Project myProject;
    private final P4Connection myConnection;

    @RequiredUIAccess
    public PerforceVersionFilterComponent(Project project, P4Connection connection, boolean showDateFilter) {
        super(showDateFilter);
        myProject = project;
        myConnection = connection;

        myPanel = new JPanel();
        myPanel.setLayout(new GridLayoutManager(2, 1, JBUI.emptyInsets(), -1, -1));
        JPanel filterPanel = new JPanel();
        filterPanel.setLayout(new GridLayoutManager(1, 4, JBUI.emptyInsets(), -1, -1));
        myPanel.add(filterPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        myUseUserFilter = CheckBox.create(PerforceLocalize.labelBrowseChangesFilterUser());
        filterPanel.add(TargetAWT.to(myUseUserFilter), new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myUserField = new TextFieldWithBrowseButton();
        filterPanel.add(myUserField, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
            new Dimension(100, -1), null, 0, false));
        myUseClientFilter = CheckBox.create(PerforceLocalize.labelBrowseChangesFilterClient());
        filterPanel.add(TargetAWT.to(myUseClientFilter), new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myClientField = new TextFieldWithBrowseButton();
        filterPanel.add(myClientField, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
            new Dimension(100, -1), null, 0, false));
        JPanel standardPanel = new JPanel(new BorderLayout());
        myPanel.add(standardPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
            null, null, 0, false));
        standardPanel.add(getStandardPanel(), BorderLayout.CENTER);

        init(new PerforceChangeBrowserSettings());
    }

    @Override
    @RequiredUIAccess
    protected void init(PerforceChangeBrowserSettings settings) {
        super.init(settings);
        installBrowseAction(myClientField, runner -> runner.getClients(myConnection),
            PerforceLocalize.messageTextCannotFindClient(), PerforceLocalize.dialogTitleSelectClient(),
            PerforceLocalize::messageTextCannotLoadClients);
        installBrowseAction(myUserField, runner -> runner.getUsers(myConnection),
            PerforceLocalize.messageTextCannotFindUser(), PerforceLocalize.dialogTitleSelectUser(),
            PerforceLocalize::messageTextCannotLoadUsers);
    }

    private interface ValuesLoader {
        List<String> load(PerforceRunner runner) throws VcsException;
    }

    /**
     * Loads the available values under a modal progress (the p4 command is not run on the UI thread) and lets the user choose one.
     */
    private void installBrowseAction(TextFieldWithBrowseButton field,
                                     ValuesLoader loader,
                                     LocalizeValue notFoundMessage,
                                     LocalizeValue title,
                                     Function<Object, LocalizeValue> loadErrorMessage) {
        field.addActionListener(e -> {
            try {
                SimpleReference<List<String>> values = new SimpleReference<>();
                boolean completed = VcsUtil.runVcsProcessWithProgress(
                    () -> values.set(loader.load(PerforceRunner.getInstance(myProject))),
                    title.get(),
                    true,
                    myProject
                );
                List<String> loaded = values.get();
                if (!completed || loaded == null) {
                    return;
                }
                if (loaded.isEmpty()) {
                    Messages.showMessageDialog(notFoundMessage.get(), title.get(), Messages.getInformationIcon());
                    return;
                }
                String[] items = loaded.toArray(String[]::new);
                int selected = Messages.showChooseDialog(myProject, "", title.get(), Messages.getQuestionIcon(), items, items[0]);
                if (selected >= 0) {
                    field.setText(items[selected]);
                }
            }
            catch (VcsException e1) {
                Messages.showErrorDialog(loadErrorMessage.apply(StringUtil.notNullize(e1.getLocalizedMessage())).get(), title.get());
            }
        });
    }

    @Override
    protected void installCheckBoxListener(ComponentEventListener<ValueComponent<Boolean>, ValueComponentEvent<Boolean>> filterListener) {
        super.installCheckBoxListener(filterListener);
        myUseClientFilter.addValueListener(filterListener);
        myUseUserFilter.addValueListener(filterListener);
    }

    @Override
    @RequiredUIAccess
    protected void initValues(PerforceChangeBrowserSettings settings) {
        super.initValues(settings);
        myUseClientFilter.setValue(settings.USE_CLIENT_FILTER, false);
        myUseUserFilter.setValue(settings.USE_USER_FILTER, false);
        myClientField.setText(settings.CLIENT);
        myUserField.setText(settings.USER);
    }

    @Override
    public void saveValues(PerforceChangeBrowserSettings settings) {
        super.saveValues(settings);

        settings.USE_CLIENT_FILTER = myUseClientFilter.getValue();
        settings.USE_USER_FILTER = myUseUserFilter.getValue();

        settings.CLIENT = myClientField.getText();
        settings.USER = myUserField.getText();
    }

    @Override
    protected void updateAllEnabled(@Nullable ValueComponentEvent<Boolean> e) {
        super.updateAllEnabled(e);
        updatePair(myUseUserFilter, myUserField, e);
        updatePair(myUseClientFilter, myClientField, e);
    }

    @Override
    public JComponent getComponent() {
        return myPanel;
    }
}
