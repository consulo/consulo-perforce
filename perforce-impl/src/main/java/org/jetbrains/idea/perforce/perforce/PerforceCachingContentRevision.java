// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce;

import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.ContentRevision;
import consulo.virtualFileSystem.InvalidVirtualFileAccessException;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.FileAttribute;
import consulo.util.collection.ArrayUtil;
import org.jspecify.annotations.Nullable;
import consulo.perforce.localize.PerforceLocalize;
import org.jetbrains.idea.perforce.application.PerforceBinaryContentRevision;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;


public final class PerforceCachingContentRevision extends PerforceContentRevision {
    private final FilePath myCurrentPath;   // for renames - path after rename
    private final boolean myOffline;
    private static final Logger LOG = Logger.getInstance(PerforceCachingContentRevision.class);

    // upper bound of the cached content size kept in a VFS attribute
    private static final int MAX_ATTRIBUTE_VALUE_SIZE = 50 * 1024 * 1024;

    private static final FileAttribute PERFORCE_CONTENT_ATTRIBUTE = new FileAttribute("p4.content", 3, false);

    private PerforceCachingContentRevision(Project project, FilePath path, FilePath currentPath, long revision, boolean offline) {
        super(project, path, revision);
        myCurrentPath = currentPath;
        myOffline = offline;
    }

    @Override
    protected byte [] loadContent() throws VcsException {
        assert myFilePath != null;
        if (LOG.isDebugEnabled()) {
            LOG.debug("loadContent: " + myCurrentPath);
        }
        VirtualFile vFile = myCurrentPath.getVirtualFile();
        if (vFile == null) {
            return super.loadContent();
        }
        byte @Nullable [] content = null;
        try {
            content = loadCachedContent(vFile);
        }
        catch (InvalidVirtualFileAccessException e) {
            throw new VcsException(PerforceLocalize.errorFileHasBeenDeleted(vFile.getPath()).get());
        }
        catch (IOException ignore) {
        }
        if (content == null) {
            if (!PerforceSettings.getSettings(myProject).ENABLED) throw new VcsException(
                PerforceLocalize.errorCanTLoadContentPerforceIsOffline().get());

            content = super.loadContent();
            try {
                saveCachedContent(vFile, myRevision, content);
            }
            catch (InvalidVirtualFileAccessException e) {
                throw new VcsException(PerforceLocalize.errorFileHasBeenDeleted(vFile.getPath()).get());
            }
            catch (IOException e) {
                LOG.error(e);
            }
        }
        return content;
    }

    private byte @Nullable [] loadCachedContent(VirtualFile vFile) throws IOException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("loadCachedContent: " + vFile + ", offline=" + myOffline);
        }
        if (myOffline) {
            byte[] content = LastUnchangedContentTracker.getLastUnchangedContent(vFile);
            if (content != null) {
                return content;
            }
        }

        try (DataInputStream stream = PERFORCE_CONTENT_ATTRIBUTE.readAttribute(vFile)) {
            if (stream == null) {
                return null;
            }

            long cachedRevision = stream.readLong();
            if (cachedRevision != myRevision) {
                return null;
            }

            return FileUtil.loadBytes(stream, stream.readInt());
        }
    }

    private static void saveCachedContent(VirtualFile vFile, long revision, byte [] content) throws IOException {
        int allSizeToStore =
            content.length + Long.BYTES + Integer.BYTES; //content.length + revision bytes + content.length bytes

        if (allSizeToStore >= MAX_ATTRIBUTE_VALUE_SIZE) {
            LOG.warn(String.format("Trying to cache file %s with %s size but max %s allowed. Skip caching.",
                                                          vFile.getPath(), allSizeToStore, MAX_ATTRIBUTE_VALUE_SIZE));
            return;
        }
        try (DataOutputStream stream = PERFORCE_CONTENT_ATTRIBUTE.writeAttribute(vFile)) {
            stream.writeLong(revision);
            stream.writeInt(content.length);
            stream.write(content);
        }
    }

    public static void removeCachedContent(VirtualFile vFile) {
        try {
            saveCachedContent(vFile, -1, ArrayUtil.EMPTY_BYTE_ARRAY);
        }
        catch (IOException e) {
            LOG.info(e);
        }
    }

    public static ContentRevision create(Project project, FilePath path, long haveRevision) {
        if (path.getFileType().isBinary()) {
            return new PerforceBinaryContentRevision(project, path, haveRevision);
        }
        return new PerforceCachingContentRevision(project, path, path, haveRevision, false);
    }

    public static ContentRevision createOffline(Project project,
                                                                                            FilePath path,
                                                                                            FilePath currentPath) {
        if (path.getFileType().isBinary()) {
            return new PerforceBinaryContentRevision(project, path, -1);
        }
        return new PerforceCachingContentRevision(project, path, currentPath, -1, true);
    }
}
