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

import org.jspecify.annotations.NullMarked;

@NullMarked
open module consulo.perforce.impl {
    requires consulo.application.api;
    requires consulo.base.icon.library;
    requires consulo.base.localize.library;
    requires consulo.code.editor.api;
    requires consulo.component.api;
    requires consulo.configurable.api;
    requires consulo.container.api;
    requires consulo.credential.storage.api;
    requires consulo.datacontext.api;
    requires consulo.diff.api;
    requires consulo.disposer.api;
    requires consulo.document.api;
    requires consulo.execution.api;
    requires consulo.file.chooser.api;
    requires consulo.file.editor.api;
    requires consulo.ide.api;
    requires consulo.language.api;
    requires consulo.language.editor.api;
    requires consulo.localize.api;
    requires consulo.logging.api;
    requires consulo.platform.api;
    requires consulo.process.api;
    requires consulo.project.api;
    requires consulo.project.ui.api;
    requires consulo.proxy;
    requires consulo.ui.api;
    requires consulo.ui.ex.api;
    requires consulo.ui.ex.awt.api;
    requires consulo.util.collection;
    requires consulo.util.collection.primitive;
    requires consulo.util.concurrent;
    requires consulo.util.dataholder;
    requires consulo.util.io;
    requires consulo.util.lang;
    requires consulo.util.xml.serializer;
    requires consulo.version.control.system.api;
    requires consulo.virtual.file.system.api;
    requires consulo.virtual.file.status.api;

    requires com.google.common;
    requires forms.rt;

    // TODO remove in future
    requires java.desktop;

    exports consulo.perforce.icon;
    exports consulo.perforce.localize;
    exports org.jetbrains.idea.perforce;
    exports org.jetbrains.idea.perforce.actions;
    exports org.jetbrains.idea.perforce.application;
    exports org.jetbrains.idea.perforce.application.annotation;
    exports org.jetbrains.idea.perforce.changesBrowser;
    exports org.jetbrains.idea.perforce.checkout;
    exports org.jetbrains.idea.perforce.merge;
    exports org.jetbrains.idea.perforce.operations;
    exports org.jetbrains.idea.perforce.perforce;
    exports org.jetbrains.idea.perforce.perforce.connections;
    exports org.jetbrains.idea.perforce.perforce.jobs;
    exports org.jetbrains.idea.perforce.perforce.login;
    exports org.jetbrains.idea.perforce.util;
    exports org.jetbrains.idea.perforce.util.process;
    exports org.jetbrains.idea.perforce.util.tracer;
}
