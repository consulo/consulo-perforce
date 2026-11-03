// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.perforce.checkout;

import consulo.annotation.component.ExtensionImpl;
import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.perforce.icon.PerforceIconGroup;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.Image;
import consulo.versionControlSystem.checkout.CheckoutPage;
import consulo.versionControlSystem.checkout.CheckoutProvider;

@ExtensionImpl
public final class PerforceCheckoutProvider implements CheckoutProvider {
    @Override
    public LocalizeValue getName() {
        return PerforceLocalize.perforce();
    }

    @Override
    public Image getIcon() {
        return PerforceIconGroup.perforcep4();
    }

    @Override
    public LocalizeValue getActionName() {
        return PerforceLocalize.checkoutButtonSync();
    }

    @Override
    @RequiredUIAccess
    public CheckoutPage createPage(Project project, Disposable uiDisposable) {
        return new PerforceCloneDialogComponent(project, uiDisposable);
    }
}
