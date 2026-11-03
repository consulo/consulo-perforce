// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.actions;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.perforce.localize.PerforceLocalize;
import consulo.platform.base.icon.PlatformIconGroup;

@ActionImpl(
    id = "Perforce.Unshelve.And.Delete",
    parents = @ActionParentRef(
        value = @ActionRef(id = "ChangesViewPopupMenu"),
        anchor = ActionRefAnchor.AFTER,
        relatedToAction = @ActionRef(type = UnshelveAction.class)
    )
)
public class UnshelveAndDeleteAction extends UnshelveActionBase {
    public UnshelveAndDeleteAction() {
        super(true,
            PerforceLocalize.actionPerforceUnshelveAndDeleteText(),
            PerforceLocalize.actionPerforceUnshelveAndDeleteDescription(),
            PlatformIconGroup.vcsUnshelve());
    }
}
