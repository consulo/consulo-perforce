// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.perforce.checkout;

import consulo.disposer.Disposable;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.localize.LocalizeValue;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.Button;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.MessageBoxes;
import consulo.ui.PasswordBox;
import consulo.ui.TextBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.style.StandardColors;
import consulo.ui.util.FormBuilder;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.checkout.CheckoutCallback;
import consulo.versionControlSystem.checkout.CheckoutPage;
import org.jetbrains.idea.perforce.application.P4RootsInformation;
import org.jetbrains.idea.perforce.perforce.ConnectionId;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.connections.P4ConnectionParameters;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionProblemsNotifier;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionTester;
import org.jetbrains.idea.perforce.perforce.connections.TestParametersConnection;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The page of the checkout dialog: Perforce server, credentials, workspace and the target directory.
 */
final class PerforceCloneDialogComponent implements CheckoutPage {
    private final Project myProject;
    private final Disposable myUiDisposable;

    private @Nullable TextBox myServerField;
    private @Nullable TextBox myUserField;
    private @Nullable PasswordBox myPasswordField;
    private @Nullable TextBox myClientField;
    private FileChooserTextBoxBuilder.@Nullable Controller myDirectoryField;
    private @Nullable Label myErrorLabel;

    PerforceCloneDialogComponent(Project project, Disposable uiDisposable) {
        myProject = project;
        myUiDisposable = uiDisposable;
    }

    @Override
    @RequiredUIAccess
    public Component createComponent(Context context) {
        TextBox serverField = TextBox.create();
        serverField.setPlaceholder(PerforceLocalize.settingsPortPlaceholder());
        myServerField = serverField;

        TextBox userField = TextBox.create();
        myUserField = userField;

        PasswordBox passwordField = PasswordBox.create();
        myPasswordField = passwordField;

        TextBox clientField = TextBox.create();
        myClientField = clientField;

        FileChooserTextBoxBuilder directoryBuilder = FileChooserTextBoxBuilder.create(myProject);
        directoryBuilder.uiDisposable(myUiDisposable);
        directoryBuilder.fileChooserDescriptor(FileChooserDescriptorFactory.createSingleFolderDescriptor());
        directoryBuilder.dialogTitle(PerforceLocalize.checkoutDirectoryChooserTitle());
        FileChooserTextBoxBuilder.Controller directoryField = directoryBuilder.build();
        myDirectoryField = directoryField;

        Label errorLabel = Label.create();
        errorLabel.setForegroundColor(StandardColors.RED);
        myErrorLabel = errorLabel;

        Button testButton = Button.create(PerforceLocalize.checkoutTestConnection(), event -> testConnectionAndShowResult());

        serverField.addValueListener(event -> updateState(context));
        userField.addValueListener(event -> updateState(context));
        clientField.addValueListener(event -> updateState(context));
        directoryField.getComponent().addValueListener(event -> updateState(context));

        FormBuilder builder = FormBuilder.create();
        builder.addLabeled(PerforceLocalize.checkoutServerLabel(), serverField);
        builder.addLabeled(PerforceLocalize.checkoutUserLabel(), userField);
        builder.addLabeled(PerforceLocalize.checkoutPasswordLabel(), passwordField);
        builder.addLabeled(PerforceLocalize.checkoutClientLabel(), clientField);
        builder.addLabeled(PerforceLocalize.checkoutDirectoryLabel(), directoryField.getComponent());
        builder.addBottom(testButton);
        builder.addBottom(errorLabel);

        updateState(context);

        return builder.build();
    }

    @Override
    @RequiredUIAccess
    public void doCheckout(CheckoutCallback listener) {
        List<LocalizeValue> errors = doValidateAll();
        if (!errors.isEmpty()) {
            MessageBoxes.okError(errors.get(0)).title(PerforceLocalize.checkoutTaskFailed()).showAsync();
            return;
        }

        PerforceCloneParams params = new PerforceCloneParams(
            getServer(),
            getUser(),
            getPassword(),
            getClient(),
            getDirectory()
        );

        new PerforceCloneTask(myProject, params, listener).queue();
    }

    @RequiredUIAccess
    private void updateState(Context context) {
        Label errorLabel = myErrorLabel;
        if (errorLabel != null) {
            errorLabel.setText(findFieldError());
        }
        context.setCheckoutEnabled(isOkEnabled());
    }

    @RequiredUIAccess
    private boolean isOkEnabled() {
        return !getServer().isBlank() &&
            !getUser().isBlank() &&
            !getClient().isBlank() &&
            !getDirectory().isBlank();
    }

    @RequiredUIAccess
    private LocalizeValue findFieldError() {
        String directory = getDirectory();
        if (!directory.isBlank()) {
            File dir = new File(directory);
            if (dir.exists() && !dir.isDirectory()) {
                return PerforceLocalize.checkoutValidationDirectoryNotDir();
            }
        }
        return LocalizeValue.empty();
    }

    @RequiredUIAccess
    private List<LocalizeValue> doValidateAll() {
        List<LocalizeValue> result = new ArrayList<>();

        if (getServer().isBlank()) {
            result.add(PerforceLocalize.checkoutValidationServerEmpty());
        }
        if (getUser().isBlank()) {
            result.add(PerforceLocalize.checkoutValidationUserEmpty());
        }
        if (getClient().isBlank()) {
            result.add(PerforceLocalize.checkoutValidationClientEmpty());
        }
        if (getDirectory().isBlank()) {
            result.add(PerforceLocalize.checkoutValidationDirectoryEmpty());
        }
        else {
            File dir = new File(getDirectory());
            if (dir.exists() && !dir.isDirectory()) {
                result.add(PerforceLocalize.checkoutValidationDirectoryNotDir());
            }
        }

        if (result.isEmpty()) {
            LocalizeValue connectionError = testConnection();
            if (connectionError != null) {
                result.add(connectionError);
            }
        }

        return result;
    }

    @RequiredUIAccess
    private void testConnectionAndShowResult() {
        String server = getServer();
        String user = getUser();

        if (server.isBlank() || user.isBlank()) {
            MessageBoxes.okWarning(PerforceLocalize.checkoutTestConnectionFillFields())
                .title(PerforceLocalize.checkoutTestConnectionTitle())
                .showAsync();
            return;
        }

        PerforceConnectionTester.TestConnectionResult result = doTestConnection();

        if (result.isCancelled()) {
            MessageBoxes.okError(PerforceLocalize.connectionCancelled()).title(PerforceLocalize.connectionStateTitle()).showAsync();
            return;
        }

        String errorMessage = result.getErrorMessage();
        if (errorMessage != null) {
            MessageBoxes.okError(LocalizeValue.of(errorMessage)).title(PerforceLocalize.connectionStateTitle()).showAsync();
            return;
        }

        P4RootsInformation info = result.getInfo();
        if (info == null) {
            return;
        }
        PerforceConnectionProblemsNotifier.showSingleConnectionState(myProject, info);
    }

    @RequiredUIAccess
    private @Nullable LocalizeValue testConnection() {
        PerforceConnectionTester.TestConnectionResult result = doTestConnection();

        if (result.isCancelled()) {
            return null;
        }

        String errorMessage = result.getErrorMessage();
        if (errorMessage != null) {
            return LocalizeValue.of(errorMessage);
        }

        P4RootsInformation info = result.getInfo();
        if (info == null) {
            return null;
        }
        String stateMessage = PerforceConnectionProblemsNotifier.getSingleConnectionState(info);
        return stateMessage.isBlank() ? null : LocalizeValue.of(stateMessage);
    }

    @RequiredUIAccess
    private PerforceConnectionTester.TestConnectionResult doTestConnection() {
        String server = getServer();
        String user = getUser();
        String password = getPassword();
        String client = getClient();
        String directory = getDirectory();

        Path clientRoot = Path.of(directory);
        if (!clientRoot.toFile().isDirectory()) {
            return new PerforceConnectionTester.TestConnectionResult(
                false,
                false,
                null,
                null,
                PerforceLocalize.checkoutValidationDirectoryNotExist(directory).get()
            );
        }

        PerforceSettings settings = createTestConnectionSettings(server, user, client);

        P4ConnectionParameters parameters = new P4ConnectionParameters();
        parameters.setServer(server);
        parameters.setUser(user);
        parameters.setClient(client);
        parameters.setPassword(password);
        TestParametersConnection connection = new TestParametersConnection(parameters, new ConnectionId());

        PerforceConnectionTester tester = new PerforceConnectionTester(myProject, settings);
        return tester.testConnection(connection, clientRoot);
    }

    private PerforceSettings createTestConnectionSettings(String server, String user, String client) {
        PerforceSettings settings = new PerforceSettings(myProject);
        settings.setCanGoOffline(false);
        settings.useP4CONFIG = false;
        settings.USE_LOGIN = true;
        settings.port = server;
        settings.user = user;
        settings.client = client;
        return settings;
    }

    @RequiredUIAccess
    private String getServer() {
        return myServerField == null ? "" : StringUtil.notNullize(myServerField.getValue()).trim();
    }

    @RequiredUIAccess
    private String getUser() {
        return myUserField == null ? "" : StringUtil.notNullize(myUserField.getValue()).trim();
    }

    @RequiredUIAccess
    private String getPassword() {
        return myPasswordField == null ? "" : StringUtil.notNullize(myPasswordField.getValue());
    }

    @RequiredUIAccess
    private String getClient() {
        return myClientField == null ? "" : StringUtil.notNullize(myClientField.getValue()).trim();
    }

    @RequiredUIAccess
    private String getDirectory() {
        return myDirectoryField == null ? "" : myDirectoryField.getValue().trim();
    }
}
