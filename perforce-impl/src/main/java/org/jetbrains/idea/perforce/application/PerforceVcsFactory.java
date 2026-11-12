/*
 * Copyright 2013-2026 consulo.io
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

import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import consulo.perforce.icon.PerforceIconGroup;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.image.Image;
import consulo.versionControlSystem.AbstractVcs;
import consulo.versionControlSystem.VcsFactory;
import jakarta.inject.Inject;

/**
 * Registers the Perforce VCS and creates {@link PerforceVcs} instances for a project.
 */
@ExtensionImpl
public class PerforceVcsFactory implements VcsFactory {
    private final Project myProject;

    @Inject
    public PerforceVcsFactory(Project project) {
        myProject = project;
    }

    @Override
    public String getId() {
        return PerforceVcs.NAME;
    }

    @Override
    public LocalizeValue getDisplayName() {
        return PerforceLocalize.perforce();
    }

    @Override
    public Image getIcon() {
        return PerforceIconGroup.perforcep4();
    }

    @Override
    public String getAdministrativeAreaName() {
        // Perforce keeps no metadata directory inside the workspace
        return "";
    }

    @Override
    public AbstractVcs<?> createVcs() {
        return new PerforceVcs(myProject);
    }
}
