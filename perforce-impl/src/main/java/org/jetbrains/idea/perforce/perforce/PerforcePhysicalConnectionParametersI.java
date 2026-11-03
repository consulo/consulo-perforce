// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce;

import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.encoding.EncodingManager;
import consulo.perforce.localize.PerforceLocalize;
import org.jspecify.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;

public interface PerforcePhysicalConnectionParametersI {
    String getPathToExec();
    @Nullable String getPathToIgnore();
    Project getProject();
    void disable();

    int getServerTimeout();
    String getCharsetName();

    default Charset getConsoleCharset() {
        String charsetName = getCharsetName();
        if (StringUtil.isEmptyOrSpaces(charsetName) || charsetName.equals(getCharsetNone())) {
            return EncodingManager.getInstance().getDefaultConsoleEncoding();
        }

        // Manual says that with utf16 or utf32 charsets, command line charset should be set to some other value
        // Since we do not support P4COMMANDCHARSET variable yet, let's pretend it's utf8
        // see https://www.perforce.com/manuals/v15.2/p4sag/chapter.configuring.html
        if (charsetName.startsWith("utf16") || charsetName.startsWith("utf32")) {
            return StandardCharsets.UTF_8;
        }

        try {
            return Charset.forName(charsetName);
        }
        catch (IllegalCharsetNameException | UnsupportedCharsetException ex) {
            return StandardCharsets.UTF_8;
        }
    }


    static String getCharsetNone() {
        return PerforceLocalize.noneCharsetPresentation().get();
    }
}
