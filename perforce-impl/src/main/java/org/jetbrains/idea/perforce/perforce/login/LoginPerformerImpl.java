// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.perforce.login;

import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.VcsException;
import org.jetbrains.idea.perforce.perforce.ExecResult;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.PerforceSettings;
import org.jetbrains.idea.perforce.perforce.PerforceTimeoutException;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.connections.P4ParametersConnection;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManagerI;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoginPerformerImpl implements LoginPerformer {
    private static final Logger LOG = Logger.getInstance(LoginPerformerImpl.class);
    private static final String LOGGED_IN_MESSAGE = "logged in";
    private static final String NAVIGATE_MESSAGE = "Navigate to URL";
    private static final String CONNECT_FAILED = "Connect to server failed; check $P4PORT.";
    private static final String CONNECTION_REFUSED = "Connection refused";
    private static final String SSO_REQUIRED = "ssoAuth required";
    private static final String SSO_OPTIONAL = "ssoAuth optional";
    private static final String PASSWORD_DELETED = "Password deleted";
    private static final String PASSWORD_UPDATED = "Password updated";

    private final P4Connection myConnection;
    private final PerforceSettings mySettings;
    private static final Pattern ourTicketExpiresPattern =
      Pattern.compile("User (.*) ticket expires in\\s+((\\d*)\\s+hours\\s+)?(\\d+)\\s+minutes.*", Pattern.DOTALL);
    private final PerforceConnectionManagerI myConnectionManager;
    // used for single connection case
    private @Nullable String myRecentCredentials;

    public LoginPerformerImpl(Project project, P4Connection connection, PerforceConnectionManagerI connectionManagerI) {
        mySettings = PerforceSettings.getSettings(project);
        myConnectionManager = connectionManagerI;
        myConnection = connection;
    }

    @Override
    public LoginState getLoginState() {
        try {
            ExecResult result = myConnection.runP4CommandLine(mySettings, new String[]{"login", "-s"}, null);
            if (result.getExitCode() != 0) {
                String stdErr = result.getStderr();
                if (stdErr.contains(CONNECT_FAILED) || stdErr.contains(CONNECTION_REFUSED) || stdErr.contains(PerforceRunner.PASSWORD_EXPIRED) || stdErr.contains("No route to host")) {
                    return new LoginState(false, -1, stdErr);
                }
                if (StringUtil.isEmpty(stdErr)) {
                    Throwable exception = result.getException();
                    if (exception != null) {
                        return new LoginState(false, -1, exception.getMessage());
                    }
                }
                return new LoginState(false, -1, null);
            }

            String stdOut = result.getStdout();
            return tryParseTicketExpiresTime(stdOut);
        }
        catch (VcsException e) {
            if (e.getCause() instanceof PerforceTimeoutException) {
                return new LoginState(false, -1, e.getMessage());
            }
            return new LoginState(false, -1, null);
        }
    }

    private static LoginState tryParseTicketExpiresTime(String stdOut) {
        Matcher matcher = ourTicketExpiresPattern.matcher(stdOut);
        if (matcher.matches()) {
            String hours = matcher.group(3);
            String minutes = matcher.group(4);
            if (minutes != null) {
                try {
                    long hoursInt = hours != null ? Integer.parseInt(hours) : 0;
                    int minutesInt = Integer.parseInt(minutes);
                    return new LoginState(true, ((hoursInt * 60) + minutesInt) * 60 * 1000, null);
                }
                catch (NumberFormatException e) {
                    //
                }
            }
        }
        return new LoginState(true, -1, null);
    }

    public static LoginState parseExpirationTicket(String stdOut) {
        return tryParseTicketExpiresTime(stdOut);
    }

    @Override
    public boolean isCredentialsChanged() {
        if (! mySettings.useP4CONFIG) {
            return true;
        }
        if (!Objects.equals(mySettings.getPasswd(), myRecentCredentials)) {
            myRecentCredentials = mySettings.getPasswd();
            return true;
        }
        return false;
    }

    @Override
    public LoginState login(String password) {
        try {
            StringBuffer data = new StringBuffer();
            data.append(password);
            ExecResult loginResult = myConnection.runP4CommandLine(mySettings, new String[]{"login"}, data);
            String stdOut = loginResult.getStdout();
            String stdErr = loginResult.getStderr();
            if ((!stdErr.isEmpty() && !stdErr.contains(NAVIGATE_MESSAGE)) || !stdOut.contains(LOGGED_IN_MESSAGE)) {
                String message = !stdOut.isEmpty() && !stdErr.isEmpty() ? stdOut + "\n" + stdErr : stdOut + stdErr;
                Throwable exception = loginResult.getException();
                if (StringUtil.isEmptyOrSpaces(message) && exception != null) {
                    message = exception.getMessage();
                }
                LOG.debug("Login failed, message: " + message);
                return new LoginState(false, -1, message);
            }
            myConnectionManager.updateConnections();
            return new LoginState(true, -1, null);
        }
        catch (VcsException e) {
            if (e.getCause() instanceof PerforceTimeoutException) {
                return new LoginState(false, -1, e.getMessage());
            }
            return new LoginState(false, -1, null);
        }
    }

    @Override
    public LoginState loginWithStoredPassword() {
        if (isSSOAuthRequired()) {
            // no password required for SSO
            return login("");
        }
        String password = myConnection instanceof P4ParametersConnection parametersConnection
                          ? parametersConnection.getParameters().getPassword()
                          : mySettings.getPasswd();
        if (password != null && !password.isEmpty()) {
            return login(password);
        }
        return new LoginState(false, -1, null);
    }

    @Override
    public P4Connection getMyConnection() {
        return myConnection;
    }

    @Override
    public LoginState changePassword(String oldPass, String password) {
        try {
            StringBuffer data = new StringBuffer();
            if (!oldPass.isEmpty()) {
                data.append(oldPass);
                data.append('\n');
            }

            data.append(password);
            data.append('\n');
            data.append(password);
            ExecResult loginResult = myConnection.runP4CommandLine(mySettings, new String[]{"passwd"}, data);
            String stdOut = loginResult.getStdout();
            String stdErr = loginResult.getStderr();
            if ((!stdErr.isEmpty() && !stdErr.contains(NAVIGATE_MESSAGE)) ||
                !(stdOut.contains(PASSWORD_DELETED)
                || stdOut.contains(PASSWORD_UPDATED))) {
                String message = stdErr;
                Throwable exception = loginResult.getException();
                if (StringUtil.isEmptyOrSpaces(message) && exception != null) {
                    message = exception.getMessage();
                }
                LOG.debug("Change pass failed, stdOut: %s, stdErr: %s".formatted(stdOut, stdErr));
                return new LoginState(false, -1, message);
            }
            myConnectionManager.updateConnections();
            return new LoginState(true, -1, null);
        }
        catch (VcsException e) {
            if (e.getCause() instanceof PerforceTimeoutException) {
                return new LoginState(false, -1, e.getMessage());
            }
            return new LoginState(false, -1, null);
        }
    }

    private boolean isSSOAuthRequired() {
        try {
            ExecResult infoResult = myConnection.runP4CommandLine(mySettings, new String[]{"-ztag"}, new String[]{"info"}, null);
            String infoStr = infoResult.getStdout();
            if (infoStr.contains(SSO_REQUIRED) || infoStr.contains(SSO_OPTIONAL)) {
                LOG.debug("Login: ssoAuth enabled");
                return true;
            }
        }
        catch (VcsException e) {
            LOG.warn("Failed to fetch ssoAuth status from 'p4 info': " + e.getMessage());
            return false;
        }
        return false;
    }
}
