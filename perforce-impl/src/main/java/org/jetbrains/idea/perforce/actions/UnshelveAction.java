// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.actions;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.perforce.localize.PerforceLocalize;

@ActionImpl(
    id = "Perforce.Unshelve",
    parents = @ActionParentRef(
        value = @ActionRef(id = "ChangesViewPopupMenu"),
        anchor = ActionRefAnchor.AFTER,
        relatedToAction = @ActionRef(type = ShelveAction.class)
    )
)
public class UnshelveAction extends UnshelveActionBase {
    public UnshelveAction() {
        super(false, PerforceLocalize.actionPerforceUnshelveText(), PerforceLocalize.actionPerforceUnshelveDescription(), null);
    }
}
