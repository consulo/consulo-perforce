// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce;

import consulo.project.Project;
import org.jspecify.annotations.Nullable;

public class PerforcePhysicalConnectionParameters implements PerforcePhysicalConnectionParametersI {
    private final String myPathToExecute;
    private final @Nullable String myPathToIgnore;
    private final Project myProject;
    private final int myServerTimeout;
    private final String myCharsetName;

    public PerforcePhysicalConnectionParameters(String pathToExecute, @Nullable String pathToIgnore, Project project, int serverTimeout, String charsetName) {
        myPathToExecute = pathToExecute;
        myPathToIgnore = pathToIgnore;
        myProject = project;
        myServerTimeout = serverTimeout;
        myCharsetName = charsetName;
    }

    @Override
    public String getPathToExec() {
        return myPathToExecute;
    }

    @Override
    public @Nullable String getPathToIgnore() {
        return myPathToIgnore;
    }

    @Override
    public Project getProject() {
        return myProject;
    }

    @Override
    public void disable() {
    }

    @Override
    public int getServerTimeout() {
        return myServerTimeout;
    }

    @Override
    public String getCharsetName() {
        return myCharsetName;
    }
}
