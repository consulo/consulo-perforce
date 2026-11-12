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
package org.jetbrains.idea.perforce.application;

import consulo.application.dumb.DumbAware;
import consulo.localize.LocalizeValue;
import consulo.perforce.localize.PerforceLocalize;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.ToggleAction;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.ChangesUtil;
import consulo.versionControlSystem.history.DiffFromHistoryHandler;
import consulo.versionControlSystem.history.HistoryAsTreeProvider;
import consulo.versionControlSystem.history.VcsAbstractHistorySession;
import consulo.versionControlSystem.history.VcsAppendableHistorySessionPartner;
import consulo.versionControlSystem.history.VcsDependentHistoryComponents;
import consulo.versionControlSystem.history.VcsFileRevision;
import consulo.versionControlSystem.history.VcsHistoryColumn;
import consulo.versionControlSystem.history.VcsHistoryProvider;
import consulo.versionControlSystem.history.VcsHistorySession;
import consulo.versionControlSystem.history.VcsRevisionNumber;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.perforce.actions.ShowAllSubmittedFilesAction;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.P4Revision;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PerforceVcsHistoryProvider implements VcsHistoryProvider {
    private final PerforceVcs myVcs;

    private static final VcsHistoryColumn REVISION = new VcsHistoryColumn() {
        @Override
        public LocalizeValue getName() {
            return PerforceLocalize.fileHistoryRevisionColumnName();
        }

        @Override
        public String getValue(VcsFileRevision vcsFileRevision) {
            if (!(vcsFileRevision instanceof PerforceFileRevision)) {
                return "";
            }
            return String.valueOf(((PerforceFileRevision) vcsFileRevision).getVersionNumber());
        }
    };

    private static final VcsHistoryColumn ACTION = new VcsHistoryColumn() {
        @Override
        public LocalizeValue getName() {
            return PerforceLocalize.fileHistoryActionColumnName();
        }

        @Override
        public String getValue(VcsFileRevision vcsFileRevision) {
            if (!(vcsFileRevision instanceof PerforceFileRevision)) {
                return "";
            }
            return ((PerforceFileRevision) vcsFileRevision).getAction();
        }
    };

    private static final VcsHistoryColumn CLIENT = new VcsHistoryColumn() {
        @Override
        public LocalizeValue getName() {
            return PerforceLocalize.fileHistoryClientColumnName();
        }

        @Override
        public String getValue(VcsFileRevision vcsFileRevision) {
            if (!(vcsFileRevision instanceof PerforceFileRevision)) {
                return "";
            }
            return ((PerforceFileRevision) vcsFileRevision).getClient();
        }
    };

    private final PerforceRunner myRunner;

    public PerforceVcsHistoryProvider(PerforceVcs vcs) {
        myVcs = vcs;
        myRunner = PerforceRunner.getInstance(vcs.getProject());
    }

    @Override
    public VcsDependentHistoryComponents getUICustomization(VcsHistorySession session) {
        return VcsDependentHistoryComponents.createOnlyColumns(List.of(REVISION, ACTION, CLIENT));
    }

    @Override
    public AnAction[] getAdditionalActions(Runnable refresher) {
        return new AnAction[]{new ShowBranchesAction(refresher), new ShowAllSubmittedFilesAction()};
    }

    @Override
    public boolean isDateOmittable() {
        return false;
    }

    @Override
    public @Nullable String getHelpId() {
        return null;
    }

    @Override
    public VcsHistorySession createSessionFor(FilePath filePath) throws VcsException {
        FilePath committedPath = ChangesUtil.getCommittedPath(myVcs.getProject(), filePath);

        P4Connection connection = myVcs.getSettings().getConnectionForFile(committedPath);
        if (connection == null) {
            throw new VcsException(PerforceLocalize.errorInvalidPerforceSettings().get());
        }
        P4File p4File = P4File.create(committedPath);
        p4File.invalidateFstat();
        List<VcsFileRevision> revisions = new ArrayList<>();
        PerforceRunner runner = PerforceRunner.getInstance(myVcs.getProject());
        for (P4Revision p4Revision : runner.filelog(p4File, PerforceSettings.getSettings(myVcs.getProject()).SHOW_BRANCHES_HISTORY)) {
            revisions.add(new PerforceFileRevision(p4Revision, connection, myVcs.getProject()));
        }
        return createSession(p4File, revisions, myRunner.getCurrentRevision(p4File));
    }

    private VcsAbstractHistorySession createSession(
        P4File p4File,
        List<VcsFileRevision> revisions,
        @Nullable VcsRevisionNumber currentRevisionNumber
    ) {
        return new VcsAbstractHistorySession(revisions, currentRevisionNumber) {
            @Override
            protected @Nullable VcsRevisionNumber calcCurrentRevisionNumber() {
                return myRunner.getCurrentRevision(p4File);
            }

            @Override
            public boolean isCurrentRevision(VcsRevisionNumber rev) {
                if (!(rev instanceof PerforceVcsRevisionNumber p4rev)) {
                    return false;
                }
                PerforceVcsRevisionNumber currentRev = (PerforceVcsRevisionNumber) getCachedRevision();
                if (currentRev != null && p4rev.getRevisionNumber() == currentRev.getRevisionNumber() && !p4rev.isBranched()) {
                    String curPath = currentRev.getDepotPath();
                    String revPath = p4rev.getDepotPath();
                    return curPath.equals(revPath);
                }
                return false;
            }

            @Override
            public synchronized boolean shouldBeRefreshed() {
                VcsRevisionNumber oldValue = getCachedRevision();
                VcsRevisionNumber newNumber = calcCurrentRevisionNumber();
                // if perforce error, it's likely due to connection(?) problem, no sense in refreshing -> as if not changed
                if (newNumber == null) {
                    return false;
                }
                setCachedRevision(newNumber);
                return oldValue == null ||
                    ((PerforceVcsRevisionNumber) oldValue).getRevisionNumber() != ((PerforceVcsRevisionNumber) newNumber).getRevisionNumber();
            }

            @Override
            public @Nullable HistoryAsTreeProvider getHistoryAsTreeProvider() {
                return null;
            }

            @Override
            public VcsHistorySession copy() {
                return createSession(p4File, getRevisionList(), getCurrentRevisionNumber());
            }
        };
    }

    @Override
    public void reportAppendableHistory(FilePath path, VcsAppendableHistorySessionPartner partner) throws VcsException {
        // no need to make faster=)
        VcsHistorySession session = createSessionFor(path);
        partner.reportCreatedEmptySession((VcsAbstractHistorySession) session);
    }

    @Override
    public boolean supportsHistoryForDirectories() {
        return true;
    }

    @Override
    public @Nullable DiffFromHistoryHandler getHistoryDiffHandler() {
        return null;
    }

    @Override
    public boolean canShowHistoryFor(VirtualFile file) {
        return true;
    }

    class ShowBranchesAction extends ToggleAction implements DumbAware {
        private final Runnable myPanelRefresher;

        ShowBranchesAction(Runnable panelRefresher) {
            super(PerforceLocalize.actionNameShowBranches(), LocalizeValue.empty(), PlatformIconGroup.vcsBranch());
            myPanelRefresher = panelRefresher;
        }

        @Override
        public boolean isSelected(AnActionEvent e) {
            return PerforceSettings.getSettings(myVcs.getProject()).SHOW_BRANCHES_HISTORY;
        }

        @Override
        @RequiredUIAccess
        public void setSelected(AnActionEvent e, boolean state) {
            PerforceSettings.getSettings(myVcs.getProject()).SHOW_BRANCHES_HISTORY = state;
            myPanelRefresher.run();
        }
    }
}
