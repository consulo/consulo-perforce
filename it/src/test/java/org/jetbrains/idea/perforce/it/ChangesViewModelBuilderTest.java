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
package org.jetbrains.idea.perforce.it;

import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.it.HeadlessProjectExtension;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangeList;
import consulo.versionControlSystem.change.SimpleContentRevision;
import consulo.versionControlSystem.impl.internal.change.ui.ChangeListTreeItem;
import consulo.versionControlSystem.impl.internal.change.ui.ChangeTreeItem;
import consulo.versionControlSystem.impl.internal.change.ui.ChangesGroupTreeItem;
import consulo.versionControlSystem.impl.internal.change.ui.ChangesTreeItem;
import consulo.versionControlSystem.impl.internal.change.ui.ChangesTreeSnapshot;
import consulo.versionControlSystem.impl.internal.change.ui.ChangesTreeSnapshotBuilder;
import consulo.versionControlSystem.util.VcsUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The Local Changes tree builder as {@link consulo.versionControlSystem.change.ChangesViewModifier}s see it: an inserted
 * group sits under its change list, but is not part of the list's changes.
 */
@ExtendWith(HeadlessProjectExtension.class)
public class ChangesViewModelBuilderTest {
    private record TestChangeList(String name, List<Change> changes) implements ChangeList {
        @Override
        public Collection<Change> getChanges() {
            return changes;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getComment() {
            return "";
        }
    }

    @Test
    public void testInsertChangesGroup(Project project) {
        Change local = change("/work/a.txt");
        Change shelved = change("/work/b.txt");
        ChangeList list = new TestChangeList("Default", List.of(local));

        ChangesTreeSnapshotBuilder builder = new ChangesTreeSnapshotBuilder(project, true).changeLists(List.of(list));
        builder.insertChangesGroup(list, LocalizeValue.of("Shelved Changes"), List.of(shelved));
        builder.insertChangesGroup(new TestChangeList("Missing", List.of()), LocalizeValue.of("ignored"), List.of(shelved));
        builder.insertChangesGroup(list, LocalizeValue.of("ignored"), List.of());
        ChangesTreeSnapshot snapshot = builder.build();

        ChangeListTreeItem listItem = new ChangeListTreeItem(list);
        assertEquals(List.of(listItem), snapshot.getRoots());

        List<ChangesTreeItem> children = snapshot.getChildren(listItem);
        assertEquals(2, children.size());
        assertEquals(new ChangeTreeItem(local), children.get(0));
        ChangesGroupTreeItem group = assertInstanceOf(ChangesGroupTreeItem.class, children.get(1));
        assertEquals("Shelved Changes", group.title().get());
        assertEquals(List.of(new ChangeTreeItem(shelved)), snapshot.getChildren(group));
        assertEquals(1, snapshot.getFileCount(group));

        // selecting the change list must not select the group's changes, selecting the group must
        assertEquals(List.of(new ChangeTreeItem(local)), snapshot.collectLeaves(listItem));
        assertEquals(List.of(new ChangeTreeItem(shelved)), snapshot.collectLeaves(group));
    }

    private static Change change(String path) {
        return new Change(null, new SimpleContentRevision("", VcsUtil.getFilePath(path, false), "1"));
    }
}
