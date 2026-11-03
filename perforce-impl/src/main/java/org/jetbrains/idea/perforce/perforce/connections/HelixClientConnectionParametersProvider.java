// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.connections;

import consulo.util.lang.StringUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.util.lang.SystemProperties;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Provides the connections known to the Helix visual client (P4V) and Helix admin tool,
 * read from their {@code connectionmap.xml} files.
 */
@ExtensionImpl(id = "helixClient", order = "after env")
public class HelixClientConnectionParametersProvider implements P4ConnectionParametersProvider {
    private static final Logger LOG = Logger.getInstance(HelixClientConnectionParametersProvider.class);
    private static final String CONNECTIONMAP_FILE_NAME = "connectionmap.xml";

    public interface ConfigFiles {
        File getClientConfig();

        File getAdminConfig();
    }

    public static class DefaultConfigFiles implements ConfigFiles {
        private static String getUserHomeDir() {
            return FileUtil.toSystemIndependentName(StringUtil.notNullize(FileUtil.toCanonicalPath(SystemProperties.getUserHome(), true)));
        }

        @Override
        public File getClientConfig() {
            return new File(getUserHomeDir() + "/.p4qt/" + CONNECTIONMAP_FILE_NAME);
        }

        @Override
        public File getAdminConfig() {
            return new File(getUserHomeDir() + "/.p4admin/" + CONNECTIONMAP_FILE_NAME);
        }
    }

    private final ConfigFiles myConfigFiles;

    @Inject
    public HelixClientConnectionParametersProvider() {
        this(new DefaultConfigFiles());
    }

    public HelixClientConnectionParametersProvider(ConfigFiles configFiles) {
        myConfigFiles = configFiles;
    }

    @Override
    public Set<P4ConnectionParameters> getConnectionParameters(Project project) {
        Set<P4ConnectionParameters> result = new LinkedHashSet<>();
        File clientConfig = myConfigFiles.getClientConfig();
        if (clientConfig.exists()) {
            result.addAll(parseConnectionParameters(clientConfig));
        }
        File adminConfig = myConfigFiles.getAdminConfig();
        if (adminConfig.exists()) {
            result.addAll(parseConnectionParameters(adminConfig));
        }

        return Collections.unmodifiableSet(result);
    }

    private static List<P4ConnectionParameters> parseConnectionParameters(File file) {
        ConnectionMapParseHandler handler = new ConnectionMapParseHandler();
        try {
            SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
            try (Reader reader = new FileReader(file)) {
                saxParser.parse(new InputSource(reader), handler);
            }
        }
        catch (Exception e) {
            LOG.warn("Unable to parse " + file, e);
        }
        List<P4ConnectionParameters> connectionMap = handler.getConnectionMapList();
        return connectionMap != null ? connectionMap : Collections.emptyList();
    }

    private static final class ConnectionMapParseHandler extends DefaultHandler {
        private @Nullable List<P4ConnectionParameters> myConnectionMapList;
        private final List<P4ConnectionParameters> myTempConnectionMapList = new ArrayList<>();
        private @Nullable P4ConnectionParameters myTempConnectionMap;
        private @Nullable String myTempValue;

        @Nullable List<P4ConnectionParameters> getConnectionMapList() {
            return myConnectionMapList;
        }

        @Override
        public void startElement(@Nullable String uri, @Nullable String localName, @Nullable String qName, @Nullable Attributes attributes) {
            if ("ConnectionMapList".equals(qName)) {
                myConnectionMapList = myTempConnectionMapList;
            }
            else if ("ConnectionMap".equals(qName)) {
                P4ConnectionParameters parameters = new P4ConnectionParameters();
                parameters.setNoConfigFound(true);
                myTempConnectionMap = parameters;
            }
        }

        @Override
        public void endElement(@Nullable String uri, @Nullable String localName, @Nullable String qName) {
            P4ConnectionParameters tempConnectionMap = myTempConnectionMap;
            if (tempConnectionMap != null) {
                if ("User".equals(qName)) {
                    tempConnectionMap.setUser(myTempValue);
                }
                else if ("P4Port".equals(qName)) {
                    tempConnectionMap.setServer(myTempValue);
                }
                else if ("ConnectionMap".equals(qName)) {
                    myTempConnectionMapList.add(tempConnectionMap);
                }
            }
            myTempValue = "";
        }

        @Override
        public void characters(char @Nullable [] ch, int start, int length) {
            myTempValue = ch != null ? new String(ch, start, length) : null;
        }
    }
}
