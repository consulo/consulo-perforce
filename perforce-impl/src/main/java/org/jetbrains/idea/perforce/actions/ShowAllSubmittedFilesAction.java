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
package org.jetbrains.idea.perforce.actions;

import consulo.application.dumb.DumbAware;
import consulo.localize.LocalizeValue;
import consulo.perforce.localize.PerforceLocalize;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.awt.Messages;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.AbstractVcsHelper;
import consulo.versionControlSystem.VcsDataKeys;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.history.VcsFileRevision;
import consulo.versionControlSystem.util.VcsUtil;
import org.jetbrains.idea.perforce.ChangeListData;
import org.jetbrains.idea.perforce.application.PerforceFileRevision;
import org.jetbrains.idea.perforce.application.PerforceVcsRevisionNumber;
import org.jetbrains.idea.perforce.perforce.PerforceChangeCache;
import org.jetbrains.idea.perforce.perforce.PerforceChangeList;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jspecify.annotations.Nullable;

import java.time.ZoneId;
import java.util.Date;

public class ShowAllSubmittedFilesAction extends AnAction implements DumbAware, AnActionWithSyncUpdate {
    public ShowAllSubmittedFilesAction() {
        super(PerforceLocalize.actionTextShowAllSubmitted(), LocalizeValue.empty(), PlatformIconGroup.toolwindowsToolwindowchanges());
    }

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null) {
            e.getPresentation().setEnabled(false);
            return;
        }
        e.getPresentation().setEnabled(e.getData(VcsDataKeys.VCS_FILE_REVISION) != null);
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null) {
            return;
        }
        VcsFileRevision revision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
        if (revision != null) {
            PerforceFileRevision perfRevision = ((PerforceFileRevision) revision);

            showAllSubmittedFiles(project, ((PerforceVcsRevisionNumber) perfRevision.getRevisionNumber()).getChangeNumber(),
                perfRevision.getCommitMessage(),
                perfRevision.getRevisionDate(),
                perfRevision.getAuthor(),
                perfRevision.getConnection());
        }
    }

    public static void showAllSubmittedFiles(Project project,
                                             long number,
                                             @Nullable String submitMessage,
                                             Date date,
                                             @Nullable String user,
                                             P4Connection connection) {
        try {
            PerforceChangeList changeList = getSubmittedChangeList(project, number, submitMessage, date, user, connection);
            if (changeList != null) {
                if (changeList.getChanges().size() > 300) {
                    Messages.showInfoMessage(PerforceLocalize.showAllFilesFromChangeListTooManyFilesAffectedErrorMessage().get(),
                        getTitle(number).get());
                }
                else {
                    AbstractVcsHelper.getInstance(project).showChangesListBrowser(changeList, getTitle(number));
                }
            }
        }
        catch (VcsException ex) {
            Messages.showErrorDialog(PerforceLocalize.messageTextCannotShowRevisions(StringUtil.notNullize(ex.getLocalizedMessage())).get(),
                getTitle(number).get());
        }
    }

    public static @Nullable PerforceChangeList getSubmittedChangeList(Project project,
                                                                      long number,
                                                                      @Nullable String submitMessage,
                                                                      Date date,
                                                                      @Nullable String user,
                                                                      P4Connection connection) throws VcsException {
        ChangeListData data = new ChangeListData();
        data.NUMBER = number;
        data.USER = StringUtil.notNullize(user);
        data.DATE = ChangeListData.DATE_FORMAT.format(date.toInstant().atZone(ZoneId.systemDefault()));
        data.DESCRIPTION = StringUtil.notNullize(submitMessage);

        PerforceChangeList changeList = new PerforceChangeList(data, project, connection, new PerforceChangeCache(project));

        boolean result = VcsUtil.runVcsProcessWithProgress(
            changeList::getChanges,
            PerforceLocalize.showAllFilesFromChangeListSearchingForChangedFilesProgressTitle().get(),
            true,
            project
        );

        return result ? changeList : null;
    }

    private static LocalizeValue getTitle(long changeListNumber) {
        return PerforceLocalize.dialogTitleShowAllRevisionsInChangelist(changeListNumber);
    }
}
