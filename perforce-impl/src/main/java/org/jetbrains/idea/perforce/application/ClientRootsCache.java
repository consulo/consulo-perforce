// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.perforce.application;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.util.SystemInfo;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
* to fix a case when user sets "c:\depot" in his client specification instead of "C:\depot" and
* we don't want to convert to canonical path/file - for each path reported by P4
*/
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public final class ClientRootsCache {
  private static final Logger LOG = Logger.getInstance(ClientRootsCache.class);
  private final Map<String, String> myRawToCanonical = new HashMap<>();
  private final Map<String, String> myCanonicalToRaw = new HashMap<>();

  private final Object myLock = new Object();

  public static ClientRootsCache getClientRootsCache(Project project) {
    return project.getInstance(ClientRootsCache.class);
  }

  String putGet(String rawClientRoot) {
    synchronized (myLock) {
      String existing = myRawToCanonical.get(rawClientRoot);
      if (existing != null) {
        return existing;
      }
      String converted = correctCase(rawClientRoot);
      // If a VCS root resolves to the same canonical path, use the VCS root path
      // so that convertPath maps directly to VCS root junction/symlink paths.
      String vcsRoot = myCanonicalToRaw.get(converted);
      if (vcsRoot != null) {
        converted = vcsRoot;
      }
      else {
        // Check if canonical is a parent of any VCS root canonical:
        // if so, raw is already in the junction namespace — keep it.
        for (String vcsCanonical : myCanonicalToRaw.keySet()) {
          if (FileUtil.startsWith(vcsCanonical, converted)) {
            converted = rawClientRoot;
            break;
          }
        }
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("canonicalize " + rawClientRoot + " to " + converted);
      }
      myCanonicalToRaw.put(converted, rawClientRoot);
      myRawToCanonical.put(rawClientRoot, converted);
      return converted;
    }
  }

  private static String correctCase(String rawClientRoot) {
    String converted = rawClientRoot;
    if (SystemInfo.isWindows && // assume the path has already correct case on other systems
        !"null".equals(rawClientRoot) &&
        !isUnixPath(rawClientRoot)) {
      File root = new File(rawClientRoot);
      try {
        converted = root.getCanonicalPath();
      }
      catch (IOException ignore) {
      }
      if (rawClientRoot.endsWith(":") && rawClientRoot.length() == 2) { //disk drive letter
        converted = StringUtil.trimEnd(converted, File.separator);
      }
    }
    return converted;
  }

  private static boolean isUnixPath(String root) {
    return root.startsWith("/");
  }

  @Nullable String getRaw(@Nullable String converted) {
    synchronized (myLock) {
      String was = myCanonicalToRaw.get(converted);
      return was == null ? converted : was;
    }
  }

  // faster than File canonical path
  String convertPath(@Nullable String convertedClientRoot, String s) {
    String trimmed = s.trim();
    synchronized (myLock) {
      if (convertedClientRoot != null) {
        String rawRoot = myCanonicalToRaw.get(convertedClientRoot);
        if (rawRoot != null && FileUtil.startsWith(trimmed, rawRoot)) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("convertPath " + trimmed + " with rawRoot " + rawRoot + ", convertedClientRoot=" + convertedClientRoot);
          }
          return glueRelativePath(convertedClientRoot, trimmed.substring(rawRoot.length()));
        }
        // no info anyway
        return trimmed;
      }
      // check all
      for (Map.Entry<String, String> entry : myRawToCanonical.entrySet()) {
        String raw = entry.getKey();
        if (FileUtil.startsWith(trimmed, raw)) {
          String canonical = entry.getValue();
          if (LOG.isDebugEnabled()) {
            LOG.debug("convertPath " + trimmed + " with raw=" + raw + ", canonical=" + canonical);
          }
          return glueRelativePath(canonical, trimmed.substring(raw.length()));
        }
      }
      return trimmed;
    }
  }

  /**
   * Registers VCS root paths so that subsequent {@link #putGet} calls can map P4 client roots
   * to VCS root junction/symlink paths when they share the same canonical path.
   * Must be called before {@link #putGet} during a refresh cycle (clears all maps).
   */
  void putVcsRoots(Collection<String> vcsRootPaths) {
    synchronized (myLock) {
      myRawToCanonical.clear();
      myCanonicalToRaw.clear();
      for (String vcsRootPath : vcsRootPaths) {
        try {
          String canonical = new File(vcsRootPath).getCanonicalPath();
          if (LOG.isDebugEnabled()) {
            LOG.debug("VCS root: " + vcsRootPath + " -> canonical: " + canonical);
          }
          myCanonicalToRaw.put(canonical, vcsRootPath);
        }
        catch (IOException e) {
          LOG.debug("Failed to resolve canonical path for VCS root: " + vcsRootPath, e);
        }
      }
    }
  }

  private static String glueRelativePath(String absPath, String relativePath) {
    if (absPath.endsWith("\\") || absPath.endsWith("/") || relativePath.startsWith("\\") || relativePath.startsWith("/")) {
      return absPath + relativePath;
    }
    return absPath + File.separator + relativePath;
  }
}
