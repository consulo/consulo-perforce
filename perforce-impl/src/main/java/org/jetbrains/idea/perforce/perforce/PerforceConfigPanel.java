// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce;

import consulo.application.Application;
import consulo.configurable.SimpleConfigurableByProperties.PropertyBuilder;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.localize.LocalizeValue;
import consulo.perforce.localize.PerforceLocalize;
import consulo.platform.Platform;
import consulo.project.Project;
import consulo.ui.Button;
import consulo.ui.CheckBox;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.Hyperlink;
import consulo.ui.IntBox;
import consulo.ui.Label;
import consulo.ui.LabelStyle;
import consulo.ui.RadioButton;
import consulo.ui.RadioGroup;
import consulo.ui.Space;
import consulo.ui.TextBox;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.Messages;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.util.LabeledBuilder;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import org.jetbrains.idea.perforce.application.ConnectionDiagnoseRefresher;
import org.jetbrains.idea.perforce.application.P4ConfigConnectionDiagnoseDialog;
import org.jetbrains.idea.perforce.application.P4RootsInformation;
import org.jetbrains.idea.perforce.application.PerforceManager;
import org.jetbrains.idea.perforce.perforce.connections.P4ConnectionParameters;
import org.jetbrains.idea.perforce.perforce.connections.P4EnvHelper;
import org.jetbrains.idea.perforce.perforce.connections.P4ParamsCalculator;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionProblemsNotifier;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionTester;
import org.jetbrains.idea.perforce.perforce.connections.SingletonConnection;
import org.jetbrains.idea.perforce.perforce.login.PerforceLoginManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Content of the {@link PerforceConfigurable} page.
 */
final class PerforceConfigPanel {
    private static final String CHARSET_NONE = "none";
    private static final String CHARSET_ISO8859_1 = "iso8859-1";
    private static final String CHARSET_ISO8859_15 = "iso8859-15";
    private static final String CHARSET_EUCJP = "eucjp";
    private static final String CHARSET_SHIFTJIS = "shiftjis";
    private static final String CHARSET_WINANSI = "winansi";
    private static final String CHARSET_MACOSROMAN = "macosroman";
    private static final String CHARSET_UTF8 = "utf8";

    private static final List<String> CHARSET_VALUES = List.of(CHARSET_NONE, CHARSET_ISO8859_1, CHARSET_ISO8859_15, CHARSET_EUCJP,
        CHARSET_SHIFTJIS, CHARSET_WINANSI, CHARSET_MACOSROMAN, CHARSET_UTF8);

    private final Project myProject;
    private final Disposable myDisposable;
    private final P4EnvHelper myP4EnvHelper;
    private final PerforceSettings mySettings;

    private final RadioGroup<Boolean> myUseP4ConfigGroup = RadioGroup.create();
    private final RadioGroup<Boolean> myUseP4IgnoreGroup = RadioGroup.create();

    private final TextBox myPort = TextBox.create();
    private final TextBox myClient = TextBox.create();
    private final TextBox myUser = TextBox.create();
    private final ComboBox<String> myCharset = ComboBox.create(CHARSET_VALUES);

    private final CheckBox myShowCmds;
    private final CheckBox myUseLogin;

    private final IntBox myServerTimeoutField = IntBox.create();

    private final Label myConfigEnvWarning = Label.create(PerforceLocalize.radioNoP4configEnv(""));
    private final Label myIgnoreEnvWarning = Label.create(PerforceLocalize.radioNoP4ignoreEnv());

    private final Label myIgnorePanelLabel = Label.create(PerforceLocalize.borderConfigureIgnoreSettings());

    private final FileChooserTextBoxBuilder.Controller myPathToIgnore;
    private final FileChooserTextBoxBuilder.Controller myPathToP4;
    private final FileChooserTextBoxBuilder.Controller myPathToP4V;

    private final List<Component> myConnectionParamsComponents = new ArrayList<>();
    private final List<Component> myIgnoreComponents = new ArrayList<>();
    private final List<Component> myIgnorePathComponents = new ArrayList<>();

    private volatile boolean myDisposed;

    @RequiredUIAccess
    PerforceConfigPanel(Project project, Disposable disposable, CheckBox showCmds, CheckBox useLogin) {
        myProject = project;
        myDisposable = disposable;
        myShowCmds = showCmds;
        myUseLogin = useLogin;
        myP4EnvHelper = P4EnvHelper.getConfigHelper(project);
        mySettings = PerforceSettings.getSettings(project);

        myPathToIgnore = FileChooserTextBoxBuilder.create(project)
            .fileChooserDescriptor(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor())
            .dialogTitle(PerforceLocalize.dialogTitlePathToP4Ignore())
            .dialogDescription(PerforceLocalize.dialogDescriptionPathToP4Ignore())
            .uiDisposable(disposable)
            .build();

        myPathToP4 = FileChooserTextBoxBuilder.create(project)
            .fileChooserDescriptor(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor())
            .dialogTitle(PerforceLocalize.dialogTitlePathToP4Exe())
            .dialogDescription(PerforceLocalize.dialogDescriptionPathToP4Exe())
            .uiDisposable(disposable)
            .build();

        myPathToP4V = FileChooserTextBoxBuilder.create(project)
            .fileChooserDescriptor(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor())
            .dialogTitle(PerforceLocalize.dialogTitlePathToP4Exe())
            .dialogDescription(PerforceLocalize.dialogDescriptionPathToP4vcExe())
            .uiDisposable(disposable)
            .build();

        myCharset.setTextRenderer(it -> it == null
            ? LocalizeValue.empty()
            : CHARSET_NONE.equals(it) ? PerforceLocalize.noneCharsetPresentation() : LocalizeValue.of(it));

        myConfigEnvWarning.addStyle(LabelStyle.FONT_SMALL);
        myIgnoreEnvWarning.addStyle(LabelStyle.FONT_SMALL);
        myIgnorePanelLabel.addStyle(LabelStyle.FONT_BOLD);

        Disposer.register(disposable, () -> myDisposed = true);
    }

    /**
     * The connection and the ignore settings - none for the default project.
     */
    @RequiredUIAccess
    void addConnectionSettings(VerticalLayout root, PropertyBuilder builder) {
        if (myProject.isDefault()) {
            return;
        }
        root.add(createConfigPanel(builder));
        root.add(createIgnorePanel(builder));

        myProject.getMessageBus().connect(myDisposable).subscribe(P4EnvHelper.P4_ENV_CHANGED,
            () -> UIAccess.get().give(this::updateIgnorePanelEnabled));
    }

    /**
     * The use login check box, with the test of the connection.
     */
    @RequiredUIAccess
    Component createLoginRow() {
        DockLayout loginRow = DockLayout.create();
        loginRow.left(myUseLogin);
        if (!myProject.isDefault()) {
            loginRow.right(Button.create(PerforceLocalize.buttonTextTestConnection(), event -> testConnection()));
        }
        return loginRow;
    }

    @RequiredUIAccess
    void addExecutables(VerticalLayout root, PropertyBuilder builder) {
        builder.add(() -> myPathToP4.getValue(), myPathToP4::setValue, () -> mySettings.pathToExec, it -> {
            String newText = StringUtil.notNullize(it).trim();
            boolean execChanged = !mySettings.pathToExec.equals(newText);
            mySettings.pathToExec = newText;
            if (execChanged) {
                PerforceManager.getInstance(myProject).resetClientVersion();
            }
        });
        root.add(LabeledBuilder.filled(PerforceLocalize.labelConfigurePerforcePathToP4Exe(), myPathToP4));

        builder.add(() -> myPathToP4V.getValue(), myPathToP4V::setValue, () -> mySettings.PATH_TO_P4VC,
            it -> mySettings.PATH_TO_P4VC = StringUtil.notNullize(it).trim());
        root.add(LabeledBuilder.filled(PerforceLocalize.labelConfigurePerforcePathToP4vcExe(), myPathToP4V));
    }

    @RequiredUIAccess
    Component createServerTimeoutRow(PropertyBuilder builder) {
        builder.add(myServerTimeoutField, () -> mySettings.SERVER_TIMEOUT / 1000,
            it -> mySettings.SERVER_TIMEOUT = (it == null ? 0 : it) * 1000);
        HorizontalLayout timeoutRow = HorizontalLayout.create();
        timeoutRow.add(Label.create(PerforceLocalize.serverTimeout()));
        timeoutRow.add(myServerTimeoutField);
        timeoutRow.add(Label.create(PerforceLocalize.configurePerforceTimeoutSeconds()));
        return timeoutRow;
    }

    @RequiredUIAccess
    Component createDumpFileRow() {
        HorizontalLayout row = HorizontalLayout.create();
        row.paddingBuilder().leftSet(Space.LARGE).apply();
        row.add(Label.create(PerforceLocalize.checkboxConfigurePerforceLogCommandsOutput()));
        File dumpFile = PerforceRunner.getDumpFile();
        // the log lives in the system directory, whose path is long: show it shortened, the full path in the tooltip
        String shortPath = StringUtil.shortenPathWithEllipsis(dumpFile.getAbsolutePath(), 60);
        Component fileComponent;
        if (dumpFile.exists()) {
            fileComponent = Hyperlink.create(LocalizeValue.of(shortPath),
                event -> Platform.current().openFileInFileManager(PerforceRunner.getDumpFile(), UIAccess.current()));
        }
        else {
            fileComponent = Label.create(LocalizeValue.of("'" + shortPath + "'"));
        }
        fileComponent.setToolTipText(LocalizeValue.of(dumpFile.getAbsolutePath()));
        row.add(fileComponent);

        row.setEnabled(Boolean.TRUE.equals(myShowCmds.getValue()));
        myShowCmds.addValueListener(event -> row.setEnabled(Boolean.TRUE.equals(event.getValue())));
        return row;
    }

    @RequiredUIAccess
    private Component createConfigPanel(PropertyBuilder builder) {
        VerticalLayout content = VerticalLayout.create();

        builder.add(myCharset, () -> mySettings.CHARSET, it -> mySettings.CHARSET = it == null ? CHARSET_NONE : it);
        content.add(LabeledBuilder.filled(PerforceLocalize.comboboxConfigurePerforceCharset(), myCharset));

        RadioButton useP4Config = myUseP4ConfigGroup.newButton(PerforceLocalize.checkboxConfigurePerforceUseP4config(), Boolean.TRUE);
        content.add(useP4Config);
        myConfigEnvWarning.paddingBuilder().leftSet(Space.LARGE).apply();
        content.add(myConfigEnvWarning);

        RadioButton useParams = myUseP4ConfigGroup.newButton(PerforceLocalize.connectionParams(), Boolean.FALSE);
        content.add(useParams);

        VerticalLayout params = VerticalLayout.create();
        params.paddingBuilder().leftSet(Space.LARGE).apply();
        params.add(LabeledBuilder.filled(PerforceLocalize.labelConfigurePerforcePort(), myPort));
        params.add(LabeledBuilder.filled(PerforceLocalize.labelConfigurePerforceUser(), myUser));
        params.add(LabeledBuilder.filled(PerforceLocalize.labelConfigurePerforceClient(), myClient));
        content.add(params);
        myConnectionParamsComponents.add(params);

        builder.add(myPort, () -> mySettings.port, it -> mySettings.port = StringUtil.notNullize(it).trim());
        builder.add(myUser, () -> mySettings.user, it -> mySettings.user = StringUtil.notNullize(it));
        builder.add(myClient, () -> mySettings.client, it -> mySettings.client = StringUtil.notNullize(it));
        builder.add(myUseP4ConfigGroup, () -> mySettings.useP4CONFIG, it -> mySettings.useP4CONFIG = Boolean.TRUE.equals(it));

        myUseP4ConfigGroup.addValueListener(it -> {
            updateConnectionParamsEnabled();
            updateIgnorePanelHeader();
            updateIgnorePanelEnabled();
        });

        return LabeledLayout.create(PerforceLocalize.borderConfigurePerforceConfigSettings(), content);
    }

    @RequiredUIAccess
    private Component createIgnorePanel(PropertyBuilder builder) {
        VerticalLayout panel = VerticalLayout.create();
        panel.add(myIgnorePanelLabel);

        VerticalLayout content = VerticalLayout.create();
        content.paddingBuilder().leftSet(Space.MEDIUM).apply();

        RadioButton useP4Ignore = myUseP4IgnoreGroup.newButton(PerforceLocalize.checkboxConfigureIgnoreUseP4ignore(), Boolean.TRUE);
        content.add(useP4Ignore);
        myIgnoreEnvWarning.paddingBuilder().leftSet(Space.LARGE).apply();
        content.add(myIgnoreEnvWarning);

        RadioButton useSettings = myUseP4IgnoreGroup.newButton(PerforceLocalize.ignoreSettings(), Boolean.FALSE);
        content.add(useSettings);

        Component pathRow = LabeledBuilder.filled(PerforceLocalize.ignorePathToFile(), myPathToIgnore);
        pathRow.paddingBuilder().leftSet(Space.LARGE).apply();
        content.add(pathRow);
        myIgnorePathComponents.add(pathRow);

        panel.add(content);
        myIgnoreComponents.add(content);

        builder.add(() -> myPathToIgnore.getValue(), myPathToIgnore::setValue, () -> mySettings.pathToIgnore,
            it -> mySettings.pathToIgnore = StringUtil.notNullize(it).trim());
        builder.add(myUseP4IgnoreGroup, () -> mySettings.useP4IGNORE, it -> mySettings.useP4IGNORE = Boolean.TRUE.equals(it));

        myUseP4IgnoreGroup.addValueListener(it -> {
            updateIgnorePathEnabled();
            updateIgnorePanelHeader();
        });

        return panel;
    }

    private boolean isUseP4ConfigSelected() {
        return Boolean.TRUE.equals(myUseP4ConfigGroup.getValue());
    }

    private boolean isUseP4IgnoreSelected() {
        return Boolean.TRUE.equals(myUseP4IgnoreGroup.getValue());
    }

    @RequiredUIAccess
    private void updateConnectionParamsEnabled() {
        for (Component component : myConnectionParamsComponents) {
            component.setEnabledRecursive(!isUseP4ConfigSelected());
        }
    }

    @RequiredUIAccess
    private void updateIgnorePathEnabled() {
        for (Component component : myIgnorePathComponents) {
            component.setEnabledRecursive(!isUseP4IgnoreSelected());
        }
    }

    @RequiredUIAccess
    private void updateIgnorePanelEnabled() {
        if (myDisposed) {
            return;
        }
        boolean enabled = !hasIgnoreFileFromEnv() || !isUseP4ConfigSelected();
        for (Component component : myIgnoreComponents) {
            component.setEnabledRecursive(enabled);
        }
        if (enabled) {
            updateIgnorePathEnabled();
        }
    }

    @RequiredUIAccess
    private void testConnection() {
        boolean isEmpty = PerforceLoginManager.getInstance(myProject).getNotifier().isEmpty();

        PerforceSettings settings = createTestConnectionSettings();
        PerforceConnectionTester tester = new PerforceConnectionTester(myProject, settings);

        boolean isSuccess;

        if (settings.useP4CONFIG) {
            PerforceConnectionTester.TestConnectionResult result = tester.testConnection();
            ConnectionDiagnoseRefresher refresher = result.getRefresher();
            isSuccess = !result.isCancelled();
            if (result.isCancelled()) {
                showCancelledConnectionDialog();
            }
            else if (refresher != null) {
                new P4ConfigConnectionDiagnoseDialog(myProject, refresher).show();
            }
        }
        else {
            PerforceConnectionTester.TestConnectionResult result = tester.testConnection(new SingletonConnection(myProject, settings));
            isSuccess = !result.isCancelled();
            P4RootsInformation info = result.getInfo();
            if (result.isCancelled()) {
                showCancelledConnectionDialog();
            }
            else if (info != null) {
                PerforceConnectionProblemsNotifier.showSingleConnectionState(myProject, info);
            }
        }

        boolean wasConnectionProblems = isSuccess && PerforceConnectionProblemsNotifier.getInstance(myProject).hasConnectionProblems();

        if (!isEmpty || wasConnectionProblems) {
            PerforceConnectionManager.getInstance(myProject).updateConnections();
        }

        updateLabels();
    }

    /**
     * Re-reads the P4 environment in background and refreshes the environment dependent labels afterwards.
     */
    @RequiredUIAccess
    void updateEnv(boolean mappedRootsUpdate) {
        UIAccess uiAccess = UIAccess.current();
        Application.get().executeOnPooledThread(() -> {
            if (myDisposed) {
                return;
            }
            myP4EnvHelper.reset();
            uiAccess.give(() -> {
                if (myDisposed) {
                    return;
                }
                if (mappedRootsUpdate) {
                    scheduleMappedRootsUpdate();
                }
                updateLabels();
            });
        });
    }

    /**
     * Makes the VCS manager recalculate the mapped roots (the environment may change which roots belong to Perforce).
     */
    private void scheduleMappedRootsUpdate() {
        ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
        vcsManager.setDirectoryMappings(new ArrayList<>(vcsManager.getDirectoryMappings()));
    }

    @RequiredUIAccess
    private void showCancelledConnectionDialog() {
        Messages.showMessageDialog(myProject, PerforceLocalize.connectionCancelled().get(),
            PerforceLocalize.connectionStateTitle().get(), Messages.getErrorIcon());
    }

    @RequiredUIAccess
    private PerforceSettings createTestConnectionSettings() {
        PerforceSettings settings = new PerforceSettings(myProject);
        settings.setCanGoOffline(false);
        settings.useP4CONFIG = isUseP4ConfigSelected();
        settings.useP4IGNORE = isUseP4IgnoreSelected();
        settings.port = StringUtil.notNullize(myPort.getValue());
        settings.client = StringUtil.notNullize(myClient.getValue());
        settings.user = StringUtil.notNullize(myUser.getValue());
        settings.CHARSET = StringUtil.notNullize(myCharset.getValue(), CHARSET_NONE);
        settings.showCmds = Boolean.TRUE.equals(myShowCmds.getValue());
        settings.pathToIgnore = StringUtil.notNullize(myPathToIgnore.getValue());
        String pathToP4 = StringUtil.notNullize(myPathToP4.getValue());
        boolean execChanged = !settings.pathToExec.equals(pathToP4);
        settings.pathToExec = pathToP4;
        if (execChanged) {
            PerforceManager.getInstance(myProject).resetClientVersion();
        }
        settings.PATH_TO_P4VC = StringUtil.notNullize(myPathToP4V.getValue());
        settings.USE_LOGIN = Boolean.TRUE.equals(myUseLogin.getValue());

        Integer seconds = myServerTimeoutField.getValue();
        if (seconds == null) {
            return settings;
        }
        settings.SERVER_TIMEOUT = seconds * 1000;
        return settings;
    }

    @RequiredUIAccess
    private void updateLabels() {
        if (myProject.isDefault()) {
            return;
        }

        updateIgnorePanelHeader();
        updateConnectionParamsEnabled();
        updateIgnorePanelEnabled();

        String unsetEnv = myP4EnvHelper.getUnsetP4EnvironmentVars();
        if (!unsetEnv.isEmpty()) {
            myConfigEnvWarning.setText(PerforceLocalize.radioNoP4configEnv(unsetEnv));
        }
        myConfigEnvWarning.setVisible(!unsetEnv.isEmpty());
        myIgnoreEnvWarning.setVisible(!myP4EnvHelper.hasP4IgnoreSetting());
    }

    @RequiredUIAccess
    private void updateIgnorePanelHeader() {
        boolean enablePanel = !(isUseP4ConfigSelected() && hasIgnoreFileFromEnv());
        myIgnorePanelLabel.setText(enablePanel
            ? PerforceLocalize.borderConfigureIgnoreSettings()
            : PerforceLocalize.borderConfigureIgnoreSettingsDisabled());
    }

    private boolean hasIgnoreFileFromEnv() {
        String configFileName = myP4EnvHelper.getP4Config();
        if (!myProject.isDefault() && myP4EnvHelper.hasP4ConfigSetting()) {
            String basePath = Objects.requireNonNull(myProject.getBasePath());
            P4ConnectionParameters params = P4ParamsCalculator.getParametersFromConfig(new File(basePath),
                Objects.requireNonNull(configFileName));
            return params.getIgnoreFileName() != null;
        }

        return false;
    }
}
