// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.connections;

import consulo.logging.Logger;
import consulo.util.lang.Pair;
import org.jetbrains.idea.perforce.util.VirtualFileMapping;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;
import org.jetbrains.idea.perforce.perforce.ConnectionId;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PerforceMultipleConnections implements PerforceConnectionMapper {
    private static final Logger LOG = Logger.getInstance(PerforceMultipleConnections.class);

    private final @Nullable String myP4ConfigValue;

    private final VirtualFileMapping<P4ConnectionParameters> myParametersMap = new VirtualFileMapping<>();
    private final P4ConnectionParameters myDefaultParameters;
    private final Map<VirtualFile, P4ParametersConnection> myConnectionMap = new HashMap<>();
    private final Object myLock = new Object();
    private final Map<VirtualFile, File> myConfigsMap;

    public PerforceMultipleConnections(@Nullable String p4ConfigValue,
                                       P4ConnectionParameters defaultParameters,
                                       Map<VirtualFile, P4ConnectionParameters> parametersMap,
                                       Map<VirtualFile, File> configsMap) {
        myP4ConfigValue = p4ConfigValue;
        myDefaultParameters = defaultParameters;
        myConfigsMap = configsMap;

        for (Map.Entry<VirtualFile, P4ConnectionParameters> entry : parametersMap.entrySet()) {
            myParametersMap.add(entry.getKey(), entry.getValue());
        }
    }

    public P4ConnectionParameters getDefaultParameters() {
        return myDefaultParameters;
    }

    public @Nullable String getP4ConfigValue() {
        return myP4ConfigValue;
    }

    @Override
    public @Nullable P4ParametersConnection getConnection(VirtualFile file) {
        if (!file.isInLocalFileSystem()) {
            LOG.warn("Trying to get connection for non-local file " + file.getClass() + " " + file);
        }
        // only caches for depot roots (see what added), but lets check first
        synchronized (myLock) {
            P4ParametersConnection cached = myConnectionMap.get(file);
            if (cached != null) {
                return cached;
            }
        }

        Pair<VirtualFile, P4ConnectionParameters> pair = myParametersMap.getMappingAndRootFor(file);
        if (pair == null) {
            return null;
        }
        synchronized (myLock) {
            return createOrGetConnectionByParameters(pair.first, pair.second);
        }
    }

    private P4ParametersConnection createOrGetConnectionByParameters(VirtualFile vf, P4ConnectionParameters parameters) {
        synchronized (myLock) {
            P4ParametersConnection existing = myConnectionMap.get(vf);
            if (existing != null) {
                return existing;
            }
            P4ConnectionParameters withParent = parameters.hasProblems() ? parameters : new P4ConnectionParameters(parameters);
            P4ParametersConnection connection = new P4ParametersConnection(withParent, new ConnectionId(myP4ConfigValue, vf.getPath()));
            myConnectionMap.put(vf, connection);
            return connection;
        }
    }

    @Override
    public Map<VirtualFile, P4Connection> getAllConnections() {
        synchronized (myLock) {
            for (Pair<VirtualFile, P4ConnectionParameters> entry : myParametersMap.entries()) {
                createOrGetConnectionByParameters(entry.first, entry.second);
            }
            return Collections.<VirtualFile, P4Connection>unmodifiableMap(myConnectionMap);
        }
    }

    public Collection<Pair<VirtualFile, P4ConnectionParameters>> getAllConnectionParameters() {
        return myParametersMap.entries();
    }

    public boolean hasAnyErrors() {
        if (myDefaultParameters.hasProblems()) {
            return true;
        }
        for (P4ConnectionParameters parameters : myParametersMap.values()) {
            if (parameters.hasProblems()) {
                return true;
            }
        }
        return false;
    }

    public Map<VirtualFile, File> getConfigsMap() {
        return myConfigsMap;
    }
}
