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
package org.jetbrains.idea.perforce.perforce;

import consulo.logging.Logger;
import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.FileAttribute;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Remembers the last unchanged (checked-in) content of a file so it can be restored or diffed while offline.
 * <p>
 * The content bytes are kept in a persistent VFS file attribute together with the time stamp they were taken at.
 */
public final class LastUnchangedContentTracker {
    private static final Logger LOG = Logger.getInstance(LastUnchangedContentTracker.class);
    private static final Key<Long> LAST_TS_KEY = Key.create("PERFORCE_LAST_TS_KEY");
    private static final FileAttribute LAST_TS_ATTR = new FileAttribute("PERFORCE_LAST_TS_ATTR", 0, true);
    private static final FileAttribute CONTENT_ATTR = new FileAttribute("PERFORCE_LAST_UNCHANGED_CONTENT_ATTR", 0, false);
    private static final Key<Boolean> VCS_INVALID_FILE_STATUS = Key.create("PERFORCE_VCS_INVALID_FILE_STATUS");

    private LastUnchangedContentTracker() {
    }

    public static void updateLastUnchangedContent(VirtualFile file) {
        if (isTouched(file)) {
            return;
        }

        Long lastTs = getLastSavedStamp(file);
        long stamp = file.getTimeStamp();
        if (lastTs != null && stamp == lastTs) {
            return;
        }

        try {
            saveContent(file, file.contentsToByteArray());
        }
        catch (IOException e) {
            LOG.info(e);
        }
        markTouched(file);
    }

    private static boolean isTouched(VirtualFile file) {
        return Boolean.TRUE.equals(file.getUserData(VCS_INVALID_FILE_STATUS));
    }

    public static void markTouched(VirtualFile file) {
        file.putUserData(VCS_INVALID_FILE_STATUS, Boolean.TRUE);
    }

    public static void markUntouched(VirtualFile file) {
        file.putUserData(VCS_INVALID_FILE_STATUS, null);
    }

    public static byte @Nullable [] getLastUnchangedContent(VirtualFile file) {
        if (!file.isValid()) {
            return null;
        }
        try {
            return CONTENT_ATTR.readAttributeBytes(file);
        }
        catch (IOException e) {
            LOG.info(e);
        }
        return null;
    }

    public static boolean hasSavedContent(VirtualFile file) {
        return getLastUnchangedContent(file) != null;
    }

    public static void forceSavedContent(VirtualFile file, String content) {
        saveContent(file, content.getBytes(file.getCharset()));
    }

    private static void saveContent(VirtualFile file, byte[] content) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("saveContent file = " + file + ", length = " + content.length);
        }

        long stamp = file.getTimeStamp();
        try {
            CONTENT_ATTR.writeAttributeBytes(file, content);

            try (DataOutputStream tsStream = LAST_TS_ATTR.writeAttribute(file)) {
                tsStream.writeLong(stamp);
            }

            file.putUserData(LAST_TS_KEY, stamp);
        }
        catch (IOException e) {
            LOG.info(e);
        }
    }

    private static @Nullable Long getLastSavedStamp(VirtualFile file) {
        Long l = file.getUserData(LAST_TS_KEY);
        if (l == null) {
            try {
                DataInputStream stream = LAST_TS_ATTR.readAttribute(file);
                if (stream != null) {
                    try (stream) {
                        l = stream.readLong();
                    }
                }
            }
            catch (IOException e) {
                LOG.info(e);
            }
        }
        return l;
    }
}
