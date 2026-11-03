// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.operations;

import consulo.language.util.ProcessingContext;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangeListManager;
import consulo.versionControlSystem.change.ChangeListManagerGate;
import consulo.versionControlSystem.change.LocalChangeList;
import org.jetbrains.idea.perforce.application.PerforceNumberNameSynchronizer;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class VcsOperation implements Cloneable {
    protected @Nullable String myChangeList;

    public final void execute(Project project) throws VcsException {
        execute(project, new ProcessingContext());
    }

    public abstract void execute(Project project, ProcessingContext context) throws VcsException;

    protected VcsOperation() {
    }

    protected VcsOperation(@Nullable String changeList) {
        myChangeList = changeList;
    }

    abstract String getInputPath();

    abstract String getOutputPath();

    public @Nullable String getChangeList() {
        return myChangeList;
    }

    public void setChangeList(@Nullable String changeList) {
        myChangeList = changeList;
    }

    public @Nullable Change getChange(Project project, ChangeListManagerGate addGate) {
        return null;
    }

    public void fillReopenedPaths(Map<String, @Nullable String> result) {
    }

    /**
     * Checks if this operation modifies or reverts the specified operation.
     *
     * @param oldOp the operation to check for replacement.
     * @return null if this operation cancels {@code oldOp}; {@code oldOp} if the operations
     * are independent
     */
    public @Nullable VcsOperation checkMerge(VcsOperation oldOp) {
        return oldOp;
    }

    protected long getPerforceChangeList(Project project, P4File p4File, ProcessingContext context) throws VcsException {
        ChangeListManager listManager = ChangeListManager.getInstance(project);
        LocalChangeList list = myChangeList == null ? null : listManager.findChangeList(myChangeList);
        if (list == null) {
            list = listManager.getDefaultChangeList();
        }
        P4Connection connection = PerforceConnectionManager.getInstance(project).getConnectionForFile(p4File);
        if (connection == null) {
            return -1;
        }

        for (Long number : PerforceNumberNameSynchronizer.getInstance(project).findOrCreate(connection, list)) {
            if (number > 0 && isValidPendingNumber(project, connection, number, context)) {
                return number;
            }
        }
        return -1;
    }

    private static final Key<Map<P4Connection, Map<Long, Boolean>>> PENDING_CACHE = Key.create("PENDING_CACHE");

    private static boolean isValidPendingNumber(Project project, P4Connection connection, long number, ProcessingContext context) {
        Map<P4Connection, Map<Long, Boolean>> map = context.get(PENDING_CACHE);
        if (map == null) {
            map = new HashMap<>();
            context.put(PENDING_CACHE, map);
        }

        Map<Long, Boolean> cache = map.computeIfAbsent(connection, k -> new HashMap<>());

        Boolean valid = cache.get(number);
        if (valid == null) {
            valid = PerforceRunner.getInstance(project).isValidPendingNumber(connection, number);
            cache.put(number, valid);
        }

        return valid;
    }

    public void prepareOffline(Project project) {
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public abstract List<String> getAffectedPaths();
}
