// SPDX-License-Identifier: MIT
package com.daimler.sechub.window;

import com.daimler.sechub.commons.model.TrafficLight;
import com.daimler.sechub.model.FileLocationExplorer;
import com.daimler.sechub.model.FindingModel;
import com.daimler.sechub.model.FindingNode;
import com.daimler.sechub.ui.SecHubToolWindowUISupport;
import com.daimler.sechub.ui.SecHubTreeNode;
import com.daimler.sechub.util.ErrorLogger;
import com.daimler.sechub.util.SimpleStringUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public class SecHubToolWindow {

    private final SecHubToolWindowUISupport support;
    private Icon iconCallHierarchyElement;
    private static final Logger LOG = Logger.getInstance(SecHubToolWindow.class);

    private static SecHubToolWindow INSTANCE;

    private JPanel sechubToolWindowContent;
    private JPanel secHubReportPanel;
    private JPanel sechubCallHierarchy;
    private JPanel secHubReportHeaderPanel;
    private JLabel scanResultForJobLabel;
    private JLabel trafficLightLabel;
    private JLabel findingsLabel;
    private JPanel secHubReportContentPanel;
    private JTable reportTable;
    private JTable callStepDetailTable;
    private JPanel detailPanel;
    private JTextField trafficLightText;
    private JTextField scanResultForJobText;
    private JTextField findingsText;
    private JTree callHierarchyTree;
    private JPanel callStepDetailPanel;
    private JTextArea reportSourceCodeTextArea;
    private JLabel findingHeaderLabel;
    private JSplitPane mainSplitPane;
    private JLabel reportSourceCodeLabel;
    private JSplitPane callHierarchySplitPane;
    private FindingModel model;


    public SecHubToolWindow(ToolWindow toolWindow) {
        //mainSplitPane.setDividerLocation(0.5);
        iconCallHierarchyElement= IconLoader.findIcon("/icons/activity.png");
        callHierarchySplitPane.setDividerLocation(0.5);
        support = new SecHubToolWindowUISupport(reportTable,callHierarchyTree, callStepDetailTable, ErrorLogger.getInstance());
        support.addCallStepChangeListener((callStep)->{
            reportSourceCodeTextArea.setText(callStep == null ? "" : SimpleStringUtil.toStringTrimmed(callStep.getSource()));
            /* now show in editor as well */
            showInEditor(callStep);
        });
        support.addReportFindingSelectionChangeListener((finding)->{
            findingHeaderLabel.setText("Finding "+finding.getId()+" - " +finding.getDescription());
        });
        support.initialize();

        installDragAndDrop();
        customizeCallHierarchyTree();

    }
    private void installDragAndDrop() {
        reportTable.setTransferHandler(new SecHubToolWindowTransferSupport(ErrorLogger.getInstance()));
        // unfortunately necessary to get drag and drop working with empty tables:
        // see https://docs.oracle.com/javase/tutorial/uiswing/dnd/emptytable.html
       // reportTable.setFillsViewportHeight(true);
    }

    private void customizeCallHierarchyTree() {
        callHierarchyTree.setCellRenderer(new ColoredTreeCellRenderer() {
            @Override
            public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                if (!(value instanceof DefaultMutableTreeNode)) {
                    return;
                }
                SecHubTreeNode treeNode = (SecHubTreeNode) value;
                FindingNode findingNode = treeNode.getFindingNode();
                if (findingNode == null) {
                    return;
                }
                String relevantPart = findingNode.getRelevantPart();
                append(relevantPart ==null ? "unknown" : relevantPart, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                append(" - ");
                String fileName = findingNode.getFileName();
                append(fileName == null ? "unknown": fileName, SimpleTextAttributes.GRAY_ATTRIBUTES);

                setIcon(iconCallHierarchyElement);

            }
        });

    }

    public static void registerInstance(SecHubToolWindow secHubToolWindow) {
        INSTANCE = secHubToolWindow;
    }

    public static SecHubToolWindow getInstance() {
        return INSTANCE;
    }

    private void showInEditor(FindingNode callStep) {
        if (callStep == null) {
            return;
        }
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        Project activeProject = null;
        for (Project project : projects) {
            Window window = WindowManager.getInstance().suggestParentWindow(project);
            if (window != null && window.isActive()) {
                activeProject = project;
            }
        }
        if (activeProject == null) {
            LOG.error("No active project found, so cannot show current call step in editor!");
            return;
        }
        FileLocationExplorer explorer = new FileLocationExplorer();
        VirtualFile projectDir = ProjectUtil.guessProjectDir(activeProject);
        if (projectDir==null){
            return;
        }
        explorer.getSearchFolders().add(projectDir.toNioPath());

        List<Path> pathes = null;
        try {
            pathes = explorer.searchFor(callStep.getLocation());
        } catch (IOException e) {
            LOG.error("Lookup for sources failed", e);
            return;
        }
        if (pathes.isEmpty()) {
            LOG.error("No source found!");
            return;
        }
        if (pathes.size() > 1) {
            LOG.warn("Multiple pathes found useing only first one");
        }
        Path first = pathes.get(0);
        @Nullable VirtualFile firstAsVirtualFile = VirtualFileManager.getInstance().findFileByNioPath(first);
        if (firstAsVirtualFile == null) {
            LOG.error("Found in normal filesystem but not in virutal one:" + first);
            return;
        }
        int line = callStep.getLine();
        int column = callStep.getColumn();

        OpenFileDescriptor fileDescriptor = new OpenFileDescriptor(activeProject, firstAsVirtualFile, line-1, column);
        fileDescriptor.navigateInEditor(activeProject, true);
    }


    public JPanel getContent() {
        return sechubToolWindowContent;
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
    }

    public void update(FindingModel model) {
        UUID jobUUID = model.getJobUUID();
        TrafficLight trafficLight = model.getTrafficLight();

        scanResultForJobText.setText(jobUUID == null ? "" : jobUUID.toString());
        trafficLightText.setText(trafficLight == null ? "" : trafficLight.toString());
        findingsText.setText("" + model.getFindings().size());
        this.model = model;
        support.setModel(model);
    }
}