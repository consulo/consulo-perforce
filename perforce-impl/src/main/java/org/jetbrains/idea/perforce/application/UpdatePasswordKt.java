// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import consulo.util.lang.ref.SimpleReference;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.util.HtmlBuilder;
import consulo.application.util.HtmlChunk;
import consulo.component.ProcessCanceledException;
import consulo.credentialStorage.CredentialAttributes;
import consulo.credentialStorage.Credentials;
import consulo.credentialStorage.PasswordSafe;
import consulo.perforce.localize.PerforceLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.FormBuilder;
import consulo.ui.ex.awt.JBCheckBox;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.ValidationInfo;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.ui.ex.awt.util.ColorUtil;
import consulo.util.lang.Pair;
import org.jetbrains.idea.perforce.perforce.login.AttemptsStateMachine;
import org.jetbrains.idea.perforce.perforce.login.LoginState;
import org.jspecify.annotations.Nullable;

import java.awt.Color;
import java.util.Arrays;
import javax.swing.JComponent;
import javax.swing.JPasswordField;
import javax.swing.event.DocumentEvent;

public final class UpdatePasswordKt {
  private UpdatePasswordKt() {
  }

  public static boolean askUpdatePassword(Project project, AttemptsStateMachine attemptsMachine, CredentialAttributes attributes) {
    return UIUtil.invokeAndWaitIfNeeded(() -> new UpdatePasswordDialog(project, attemptsMachine, attributes).showAndGet());
  }

  private static final class UpdatePasswordDialog extends DialogWrapper {
    private final Project myProject;
    private final AttemptsStateMachine myAttemptsMachine;
    private final CredentialAttributes myAttributes;

    private final JPasswordField myOldPasswordField = new JPasswordField();
    private final JPasswordField myNewPasswordField = new JPasswordField();
    private final JPasswordField myRepeatNewPasswordField = new JPasswordField();
    private final JBCheckBox myRememberCheckbox = new JBCheckBox(PerforceLocalize.loginPasswordRemember().get(), true);
    private final JBLabel myErrorLabel = new JBLabel("");

    private UpdatePasswordDialog(Project project, AttemptsStateMachine attemptsMachine, CredentialAttributes attributes) {
      super(project);
      myProject = project;
      myAttemptsMachine = attemptsMachine;
      myAttributes = attributes;
      setTitle(PerforceLocalize.loginPasswordUpdateTitle());
      init();
    }

    @Override
    protected JComponent createCenterPanel() {
      myRememberCheckbox.setToolTipText(PerforceLocalize.loginPasswordRememberTooltip().get());

      DocumentAdapter validationListener = new DocumentAdapter() {
        @Override
        protected void textChanged(DocumentEvent e) {
          initValidation();
        }
      };
      myOldPasswordField.getDocument().addDocumentListener(validationListener);
      myNewPasswordField.getDocument().addDocumentListener(validationListener);
      myRepeatNewPasswordField.getDocument().addDocumentListener(validationListener);

      return FormBuilder.createFormBuilder()
        .addLabeledComponent(PerforceLocalize.loginPasswordOldPassword().get(), myOldPasswordField)
        .addLabeledComponent(PerforceLocalize.loginPasswordNewPassword().get(), myNewPasswordField)
        .addLabeledComponent(PerforceLocalize.loginPasswordRepeatNewPassword().get(), myRepeatNewPasswordField)
        .addComponent(myRememberCheckbox)
        .addComponent(myErrorLabel)
        .getPanel();
    }

    @Override
    @RequiredUIAccess
    protected @Nullable ValidationInfo doValidate() {
      if (!Arrays.equals(myNewPasswordField.getPassword(), myRepeatNewPasswordField.getPassword())) {
        return new ValidationInfo(PerforceLocalize.loginPasswordPasswordsNotMatchError(), myRepeatNewPasswordField);
      }
      if (Arrays.equals(myOldPasswordField.getPassword(), myNewPasswordField.getPassword())) {
        return new ValidationInfo(PerforceLocalize.loginPasswordPasswordNotChangedError(), myOldPasswordField);
      }

      return super.doValidate();
    }

    @Override
    @RequiredUIAccess
    public JComponent getPreferredFocusedComponent() {
      return myOldPasswordField;
    }

    @Override
    protected void doOKAction() {
      Pair<String, String> passwords = getPasswords();
      LoginState state = changePassUnderProgress(myProject, myAttemptsMachine, passwords.first, passwords.second);
      if (state == null || !state.isSuccess()) {
        Color color = UIUtil.getErrorForeground();
        String error = state == null ? "" : String.valueOf(state.getError());
        HtmlBuilder builder = new HtmlBuilder();
        builder.append(
          HtmlChunk.raw(PerforceLocalize.loginPerforceError(error).get())
            .wrapWith("left")
            .wrapWith(HtmlChunk.font(ColorUtil.toHex(color)))
        );
        myErrorLabel.setText(builder.wrapWithHtmlBody().toString());
        myErrorLabel.setVisible(true);
        return;
      }

      PasswordSafe store = PasswordSafe.getInstance();
      Credentials credentials = new Credentials(myAttributes.getUserName(), new String(myNewPasswordField.getPassword()).trim());
      if (myRememberCheckbox.isSelected()) {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> store.set(myAttributes, credentials),
                                                                          getTitle(), false, myProject);
      }

      super.doOKAction();
    }

    private Pair<String, String> getPasswords() {
      return Pair.create(new String(myOldPasswordField.getPassword()), new String(myNewPasswordField.getPassword()));
    }
  }

  private static @Nullable LoginState changePassUnderProgress(Project project,
                                                              AttemptsStateMachine machine,
                                                              String oldPass,
                                                              String newPassword) {
    SimpleReference<LoginState> result = SimpleReference.create();
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      if (indicator != null) {
        indicator.setIndeterminate(true);
      }
      try {
        result.set(machine.changePass(oldPass, newPassword));
      }
      catch (ProcessCanceledException ignored) {
      }
    }, PerforceLocalize.loginPasswordUpdateTitle(), true, project);
    return result.get();
  }
}
