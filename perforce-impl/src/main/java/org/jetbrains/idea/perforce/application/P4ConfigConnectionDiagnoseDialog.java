// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the MIT license (see NOTICE.txt).
package org.jetbrains.idea.perforce.application;

import consulo.application.progress.ProgressManager;
import consulo.perforce.localize.PerforceLocalize;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.ScrollPaneFactory;
import consulo.ui.ex.awt.tree.ColoredTreeCellRenderer;
import consulo.ui.ex.awt.tree.Tree;
import consulo.ui.ex.awt.tree.TreeUIHelper;
import consulo.ui.ex.awt.tree.TreeUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.io.FileUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.VcsException;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.perforce.perforce.connections.P4Connection;
import org.jetbrains.idea.perforce.perforce.connections.P4ConnectionParameters;
import org.jetbrains.idea.perforce.perforce.connections.P4EnvHelper;
import org.jetbrains.idea.perforce.perforce.connections.PerforceMultipleConnections;
import org.jspecify.annotations.Nullable;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

public class P4ConfigConnectionDiagnoseDialog extends DialogWrapper {
  private PerforceMultipleConnections myMultipleConnections;
  private P4RootsInformation myChecker;
  private final BaseNode myRoot = new BaseNode("", NodeType.veryRoot);
  private final Tree myTree = new Tree(myRoot);
  private final Project myProject;
  private final P4EnvHelper myP4EnvHelper;
  private final ConnectionDiagnoseRefresher myRefresher;

  public P4ConfigConnectionDiagnoseDialog(Project project, ConnectionDiagnoseRefresher refresher) {
    super(project, true);
    myProject = project;
    myP4EnvHelper = P4EnvHelper.getConfigHelper(project);
    myRefresher = refresher;
    myMultipleConnections = refresher.getMultipleConnections();
    myChecker = refresher.getP4RootsInformation();
    setTitle(PerforceLocalize.configDialogTitle());
    setCancelButtonText(CommonLocalize.closeActionName());

    init();
  }

  @Override
  protected Action [] createActions() {
    DialogWrapperAction refreshAction = new DialogWrapperAction(CommonLocalize.actionRefresh()) {
      @Override
      protected void doAction(ActionEvent e) {
        myTree.setPaintBusy(true);
        myTree.setEnabled(false);

        boolean complete = ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> myRefresher.refresh(),
                                                                                                   PerforceLocalize.configRefresh(), true,
                                                                                                   myProject);
        if (complete) {
          fillTree();
        }
        myTree.setPaintBusy(false);
        myTree.setEnabled(true);
      }
    };
    return new Action[]{refreshAction, getCancelAction()};
  }

  // will be called on refresh
  private void fillTree() {
    myRoot.removeAllChildren();
    myMultipleConnections = myRefresher.getMultipleConnections();
    myChecker = myRefresher.getP4RootsInformation();

    DefaultTreeModel model = (DefaultTreeModel)myTree.getModel();
    Collection<Pair<VirtualFile, P4ConnectionParameters>> connectionParameters = myMultipleConnections.getAllConnectionParameters();
    P4ConnectionParameters defaultParameters = myMultipleConnections.getDefaultParameters();
    Map<VirtualFile, File> configsMap = myMultipleConnections.getConfigsMap();

    Set<String> p4ConfigNames = new LinkedHashSet<>();

    int i = 0;
    boolean containNoConfigs = false;
    for (Pair<VirtualFile, P4ConnectionParameters> entry : connectionParameters) {
      VirtualFile root = entry.first;
      P4ConnectionParameters parameters = entry.second;

      BaseNode fileNode = new BaseNode(root, NodeType.root);
      model.insertNodeInto(fileNode, myRoot, i);
      /*myRoot.add(fileNode);
      fileNode.setParent(myRoot);*/

      File configDir = configsMap.get(root);
      putConfigDir(fileNode, configDir, parameters.isNoConfigFound());

      containNoConfigs |= parameters.isNoConfigFound();
      ContainerUtil.addIfNotNull(p4ConfigNames, parameters.getConfigFileName());
      if (! putConfigLines(defaultParameters, fileNode, configDir, parameters)) {
        addGenericErrors(fileNode, parameters);
        continue;
      }
      if (! checkConnection(root, fileNode)) {
        addGenericErrors(fileNode, parameters);
        continue;
      }
      boolean someErrors = addGenericErrors(fileNode, parameters);
      someErrors |= addRootError(Objects.requireNonNull(myMultipleConnections.getConnection(root)), fileNode);
      if (!someErrors) {
        addNode(fileNode, new BaseNode(CommonLocalize.buttonOk().get(), NodeType.ok));
      }
      ++i;
    }

    String envP4Config = myP4EnvHelper.getP4Config();
    if (envP4Config != null) {
      addNode(myRoot,
              new BaseNode(PerforceLocalize.connectionEnv(envP4Config).get(), NodeType.info));
    }

    if (myChecker.hasNoConnections()) {
      addNode(myRoot, new ErrorNode(PerforceLocalize.connectionNoValidConnectionsShort().get()));
    }
    // common errors
    addGenericErrors(myRoot, defaultParameters);
    if (containNoConfigs) {
      if (p4ConfigNames.isEmpty()) {
        ContainerUtil.addIfNotNull(p4ConfigNames, envP4Config);
      }
      if (!p4ConfigNames.isEmpty()) {
        addNode(myRoot,
                new BaseNode(PerforceLocalize.connectionConfigFile(StringUtil.join(p4ConfigNames, ", ")).get(), NodeType.info));
      }
    }

    model.nodeStructureChanged(myRoot);
    TreeUtil.expand(myTree, 3);
  }

  private static boolean addGenericErrors(BaseNode fileNode, P4ConnectionParameters parameters) {
    boolean somethingAdded = false;
    Throwable exception = parameters.getException();
    if (exception != null) {
      addNode(fileNode, new ErrorNode(StringUtil.notNullize(exception.getMessage())));
      somethingAdded = true;
    }
    List<String> warnings = parameters.getWarnings();
    for (String warning : warnings) {
      addNode(fileNode, new ErrorNode(warning));
      somethingAdded = true;
    }
    return somethingAdded;
  }

  private static void addNode(BaseNode parent, BaseNode child) {
    parent.add(child);
    child.setParent(parent);
  }

  private boolean addRootError(P4Connection connection, BaseNode fileNode) {
    boolean somethingAdded = false;
    if (myChecker.getNotAuthorized().containsKey(connection)) {
      addNode(fileNode, new ErrorNode(PerforceLocalize.loginNotLoggedIn().get()));
      somethingAdded = true;
    }
    Collection<VcsException> exceptions = myChecker.getErrors().get(connection);
    for (VcsException exception : exceptions) {
      ErrorNode node = new ErrorNode(StringUtil.notNullize(exception.getMessage()));
      addNode(fileNode, node);
      somethingAdded = true;
    }
    PerforceClientRootsChecker.WrongRoots wrongRoots = myChecker.getMap().get(connection);
    if (wrongRoots != null) {
      ErrorNode node = new ErrorNode(PerforceLocalize.configWrongClientSpec().get());
      addNode(fileNode, node);
      somethingAdded = true;
      List<ErrorNode> children = new ArrayList<>();
      children.add(new ErrorNode(PerforceLocalize.configClientRoots().get() + ' '));
      for (String clientRoot : wrongRoots.getActualInClientSpec()) {
        children.add(new ErrorNode(clientRoot));
      }
      children.add(new ErrorNode(PerforceLocalize.configActualRoot().get() + ' '));
      for (VirtualFile vf : wrongRoots.getWrong()) {
        children.add(new ErrorNode(vf.getPath()));
      }

      for (ErrorNode child : children) {
        addNode(node, child);
      }
    }
    return somethingAdded;
  }

  private boolean checkConnection(VirtualFile root, BaseNode fileNode) {
    P4Connection connection = myMultipleConnections.getConnection(root);
    if (connection == null) {
      ErrorNode node = new ErrorNode(PerforceLocalize.connectionCannotCreate().get());
      addNode(fileNode, node);
      return false;
    } else {
      if (! connection.isConnected()) {
        ErrorNode node = new ErrorNode(PerforceLocalize.connectionNotConnected().get());
        addNode(fileNode, node);
        return false;
      }
    }
    return true;
  }

  private void putConfigDir(BaseNode fileNode, @Nullable File configDir, boolean noConfigFound) {
    String p4ConfigValue = myMultipleConnections.getP4ConfigValue();
    if (configDir != null && !noConfigFound && p4ConfigValue != null) {
      File file = new File(FileUtil.toSystemDependentName(configDir.getPath()), p4ConfigValue);
      addNode(fileNode, new BaseNode(file, NodeType.p4configFile));
    } else {
      addNode(fileNode, new BaseNode(PerforceLocalize.connectionNoConfigFile().get(), NodeType.noConfig));
    }
  }

  private static boolean putConfigLines(P4ConnectionParameters defaultParameters,
                                        BaseNode fileNode,
                                        @Nullable File configDir, P4ConnectionParameters parameters) {
    Map<String, ConfigLine> dataPresentation = getDataPresentation(parameters, defaultParameters, configDir == null);
    if (dataPresentation.isEmpty()) {
      addNode(fileNode, new ErrorNode(PerforceLocalize.connectionNoParams().get()));
      return false;
    } else {
      for (ConfigLine line : dataPresentation.values()) {
        addNode(fileNode, new BaseNode(line, NodeType.configLine));
      }

      boolean requiredParametersDefined = true;

      if (parameters.getServer() == null && defaultParameters.getServer() == null) {
        addNode(fileNode, new ErrorNode(PerforceLocalize.errorServerUnknownEnv().get()));
        requiredParametersDefined = false;
      }

      if (parameters.getUser() == null && defaultParameters.getUser() == null) {
        addNode(fileNode, new ErrorNode(PerforceLocalize.errorUserUnknownEnv().get()));
        requiredParametersDefined = false;
      }

      if (parameters.getClient() == null && defaultParameters.getClient() == null) {
        addNode(fileNode, new ErrorNode(PerforceLocalize.errorClientUnknownEnv().get()));
        requiredParametersDefined = false;
      }
      return requiredParametersDefined;
    }
  }

  public static Map<String, ConfigLine> getDataPresentation(P4ConnectionParameters p,
                                                            P4ConnectionParameters d,
                                                            boolean noConfig) {
    Map<String, ConfigLine> result = new HashMap<>();
    if (noConfig) {
      putParametersIntoTheMap(d, result, "");
    } else {
      putParametersIntoTheMap(d, result, "environment");
      putParametersIntoTheMap(p, result, "config");
    }
    return result;
  }

  private static void putParametersIntoTheMap(P4ConnectionParameters p, Map<String, ConfigLine> map, String source) {
    addIfNotNull(p.getServer(), "P4PORT = ", map, source, 0);
    addIfNotNull(p.getClient(), "P4CLIENT = ", map, source, 1);
    addIfNotNull(p.getUser(), "P4USER = ", map, source, 2);
    if (p.getPassword() != null) {
      map.put("3Password:", new ConfigLine(source, "P4PASSWD = ", "********"));
    }
    addIfNotNull(p.getCharset(), "P4CHARSET = ", map, source, 4);
    addIfNotNull(p.getIgnoreFileName(), "P4IGNORE = ", map, source, 5);
  }

  private static void addIfNotNull(@Nullable String value, String text, Map<String, ConfigLine> map, String source, int i) {
    if (value != null) {
      map.put(i + text, new ConfigLine(source, text, value));
    }
  }

  private static final class ConfigLine {
    private final String mySource;
    private final String myParam;
    private final String myValue;

    private ConfigLine(String source, String param, String value) {
      mySource = source;
      myParam = param;
      myValue = value;
    }

    public String getSource() {
      return mySource;
    }

    public String getParam() {
      return myParam;
    }

    public String getValue() {
      return myValue;
    }

    @Override
    public String toString() {
      return myParam + myValue;
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTree;
  }

  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());

    TreeUIHelper.getInstance().installTreeSpeedSearch(myTree);
    TreeUtil.installActions(myTree);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(false);
    myTree.setCellRenderer(new MyRenderer());
    fillTree();
    panel.add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);
    return panel;
  }

  private static final class ErrorNode extends BaseNode {

    private ErrorNode(String text) {
      super(text, NodeType.problem);
    }

    @Override
    public String toString() {
      return (String) getUserObject();
    }
  }

  private static class BaseNode extends DefaultMutableTreeNode {
    private final NodeType myNodeType;
    protected BaseNode(Object userObject, NodeType nodeType) {
      super(userObject);
      myNodeType = nodeType;
    }

    public NodeType getNodeType() {
      return myNodeType;
    }

    @Override
    public String toString() {
      if (NodeType.root.equals(myNodeType)) {
        VirtualFile vf = (VirtualFile)getUserObject();
        return vf.getName() + ' ' + getParentPath(vf);
      }
      if (NodeType.p4configFile.equals(myNodeType)) {
        File file = (File)getUserObject();
        return PerforceLocalize.configFile(file.getName()).get() + ' ' + getParentPath(file);
      }
      return getUserObject().toString();
    }
  }

  private enum NodeType {
    veryRoot,
    root,
    p4configFile,
    configLine,
    problem,
    string,
    ok,
    info,
    noConfig
  }

  private static class MyRenderer extends ColoredTreeCellRenderer {

    public static final SimpleTextAttributes GREEN = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.GREEN.darker(), null);

    @Override
    public void customizeCellRenderer(JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (! (value instanceof BaseNode baseNode)) {
        return;
      }
      NodeType type = baseNode.getNodeType();
      Object uo = baseNode.getUserObject();

      if (NodeType.root.equals(type)) {
        setIcon(PlatformIconGroup.nodesFolder());
        VirtualFile root = (VirtualFile)uo;
        append(root.getName() + ' ');
        String fragment;
        fragment = getParentPath(root);
        append(fragment, SimpleTextAttributes.GRAY_ATTRIBUTES);
      } else if (NodeType.p4configFile.equals(type)) {
        File configFile = (File) uo;
        setIcon(PlatformIconGroup.generalSettings());
        append(PerforceLocalize.configFile(configFile.getName()).get() + ' ', SimpleTextAttributes.DARK_TEXT);
        String parentPath = getParentPath(configFile);
        append(parentPath, SimpleTextAttributes.GRAY_ATTRIBUTES);
      } else if (NodeType.noConfig.equals(type)) {
        String text = (String) uo;
        setIcon(PlatformIconGroup.generalSettings());
        append(text, SimpleTextAttributes.DARK_TEXT);
      } else if (NodeType.string.equals(type)) {
        append((String)uo);
      } else if (NodeType.configLine.equals(type)) {
        ConfigLine cl = (ConfigLine) uo;
        append(cl.getParam());
        append(cl.getValue());
        String source = cl.getSource();
        if (! StringUtil.isEmptyOrSpaces(source)) {
          append(" (" + source + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
      } else if (NodeType.problem.equals(type)) {
        assert baseNode instanceof ErrorNode;
        append((String)uo, SimpleTextAttributes.ERROR_ATTRIBUTES);
        /*final ErrorNode errorNode = (ErrorNode) baseNode;
        if (ErrorType.notEnoughParameters.equals(errorNode.myErrorType)) {
          //5
        } else if (ErrorType.parameters.equals(errorNode.myErrorType)) {
          //6
        } else if (ErrorType.connection.equals(errorNode.myErrorType)) {
          //7
        } else if (ErrorType.wrongClient.equals(errorNode.myErrorType)) {
          //8
        } else {
          assert true;
        }*/
      } else if (NodeType.ok.equals(type)) {
        append((String)uo, GREEN);
      } else if (NodeType.info.equals(type)) {
        setIcon(PlatformIconGroup.generalInformation());
        append((String)uo);
      }
    }
  }

  private static String getParentPath(File configFile) {
    String parent = StringUtil.notNullize(configFile.getParent()).replace('\\', '/');
    return " (" + parent + ")";
  }

  private static String getParentPath(VirtualFile root) {
    String fragment;
    VirtualFile parent = root.getParent();
    if (parent != null) {
      fragment = "(" + parent.getPath() + ")";
    } else {
      fragment = "(" + root.getPath() + ")";
    }
    return fragment;
  }
}
