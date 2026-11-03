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

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.ex.action.AnSeparator;
import consulo.versionControlSystem.AbstractVcs;
import consulo.versionControlSystem.action.StandardVcsGroup;
import org.jetbrains.idea.perforce.application.PerforceVcs;

@ActionImpl(
    id = "PerforceDirect.Menu",
    children = {
        @ActionRef(id = "CheckinFiles"),
        @ActionRef(id = "UpdateFiles"),
        @ActionRef(id = "IntegrateFiles"),
        @ActionRef(type = RevertAllUnchangedFilesAction.class),
        @ActionRef(id = "Annotate"),
        @ActionRef(id = "Show.Current.Revision"),
        @ActionRef(id = "Compare.SameVersion"),
        @ActionRef(id = "Compare.LastVersion"),
        @ActionRef(id = "Compare.Selected"),
        @ActionRef(type = ShelveAction.class),
        @ActionRef(type = ActionEdit.class),
        @ActionRef(id = "ChangesView.AddUnversioned"),
        @ActionRef(id = "ChangesView.Revert"),
        @ActionRef(id = "Vcs.ShowTabbedFileHistory"),
        @ActionRef(id = "Vcs.ShowHistoryForBlock"),
        @ActionRef(type = ResolveAction.class),
        @ActionRef(type = ResolveAllAction.class),
        @ActionRef(id = "ChangesView.Browse"),
        @ActionRef(type = RevisionGraphAction.class),
        @ActionRef(type = TimeLapseViewAction.class),
        @ActionRef(type = AnSeparator.class),
        @ActionRef(type = EnableAction.class),
        @ActionRef(type = CheckLoginStateAction.class)
    },
    parents = {
        @ActionParentRef(value = @ActionRef(id = "VcsGroup"), anchor = ActionRefAnchor.LAST),
        @ActionParentRef(
            value = @ActionRef(id = "VcsGlobalGroup"),
            anchor = ActionRefAnchor.AFTER,
            relatedToAction = @ActionRef(id = "Vcs.Specific")
        )
    }
)
public final class PerforceMenu extends StandardVcsGroup {
    public PerforceMenu() {
        getTemplatePresentation().setText(PerforceLocalize.groupPerforcedirectMenuText());
        setPopup(true);
    }

    @Override
    public AbstractVcs getVcs(Project project) {
        return PerforceVcs.getInstance(project);
    }
}
