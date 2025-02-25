// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.components;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.internal.InternalActionsBundle;
import com.intellij.internal.inspector.PropertyBean;
import com.intellij.internal.inspector.UiInspectorAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.actions.IconWithTextAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.Disposer;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.paint.RectanglePainter;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class InspectorWindow extends JDialog implements Disposable {
  private InspectorTable myInspectorTable;
  @NotNull private final java.util.List<Component> myComponents = new ArrayList<>();
  private java.util.List<? extends PropertyBean> myInfo;
  @NotNull private final Component myInitialComponent;
  @NotNull private final java.util.List<HighlightComponent> myHighlightComponents = new ArrayList<>();
  private boolean myIsHighlighted = true;
  @NotNull private final HierarchyTree myHierarchyTree;
  @NotNull private final Wrapper myWrapperPanel;
  @Nullable private final Project myProject;
  private final UiInspectorAction.UiInspector myInspector;

  public InspectorWindow(@Nullable Project project,
                         @NotNull Component component,
                         UiInspectorAction.UiInspector inspector) throws HeadlessException {
    super(findWindow(component));
    myProject = project;
    myInspector = inspector;
    Window window = findWindow(component);
    setModal(window instanceof JDialog && ((JDialog)window).isModal());
    myComponents.add(component);
    myInitialComponent = component;
    getRootPane().setBorder(JBUI.Borders.empty(5));

    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

    setLayout(new BorderLayout());
    setTitle(component.getClass().getName());
    Dimension size = DimensionService.getInstance().getSize(getDimensionServiceKey(), null);
    Point location = DimensionService.getInstance().getLocation(getDimensionServiceKey(), null);
    if (size != null) setSize(size);
    if (location != null) setLocation(location);

    DefaultActionGroup actions = new DefaultActionGroup();
    actions.addAction(new MyTextAction(IdeBundle.messagePointer("action.Anonymous.text.highlight")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        myIsHighlighted = !myIsHighlighted;
        updateHighlighting();
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(myInfo != null || !myComponents.isEmpty());
      }
    });

    actions.addSeparator();

    actions.add(new MyTextAction(InternalActionsBundle.messagePointer("action.Anonymous.text.refresh")) {

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        getCurrentTable().refresh();
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(!myComponents.isEmpty());
      }
    });

    actions.addSeparator();

    actions.add(new MyTextAction(InternalActionsBundle.messagePointer("action.Anonymous.text.Accessible")) {
      private boolean isAccessibleEnable = false;

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        switchHierarchy();
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setText(isAccessibleEnable
                                    ? InternalActionsBundle.message("action.Anonymous.text.Visible")
                                    : InternalActionsBundle.message("action.Anonymous.text.Accessible"));
      }

      private void switchHierarchy() {
        TreePath path = myHierarchyTree.getLeadSelectionPath();
        Object node = path == null ? null : path.getLastPathComponent();
        if (node == null) return;
        Component c = ((HierarchyTree.ComponentNode)node).getComponent();
        if (c != null) {
          isAccessibleEnable = !isAccessibleEnable;
          myHierarchyTree.resetModel(c, isAccessibleEnable);
          myHierarchyTree.expandPath(isAccessibleEnable);
        }
      }
    });

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.CONTEXT_TOOLBAR, actions, true);
    toolbar.setTargetComponent(getRootPane());
    add(toolbar.getComponent(), BorderLayout.NORTH);

    myWrapperPanel = new Wrapper();

    myInspectorTable = new InspectorTable(component);
    myHierarchyTree = new HierarchyTree(component) {
      @Override
      public void onComponentsChanged(java.util.List<? extends Component> components) {
        switchComponentsInfo(components);
        updateHighlighting();
      }

      @Override
      public void onClickInfoChanged(java.util.List<? extends PropertyBean> info) {
        switchClickInfo(info);
        updateHighlighting();
      }
    };
    DataProvider provider = dataId -> {
      if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
        return new Navigatable() {
          @Override
          public void navigate(boolean requestFocus) {
            if (myHierarchyTree.hasFocus()) {
              if (!myComponents.isEmpty()) {
                openClass(myComponents.get(0).getClass().getName(), requestFocus);
              }
              else {
                TreePath path = myHierarchyTree.getSelectionPath();
                if (path != null) {
                  Object obj = path.getLastPathComponent();
                  if (obj instanceof HierarchyTree.ComponentNode) {
                    Component comp = ((HierarchyTree.ComponentNode)obj).getComponent();
                    if (comp != null) {
                      openClass(comp.getClass().getName(), requestFocus);
                    }
                  }
                }
              }
            }
            else if (myInspectorTable.getTable().hasFocus()) {
              int row = myInspectorTable.getTable().getSelectedRow();
              Object at = myInspectorTable.getModel().getValueAt(row, 1);
              openClass(String.valueOf(at), requestFocus);
            }
          }

          @Override
          public boolean canNavigate() {
            return true;
          }

          @Override
          public boolean canNavigateToSource() {
            return true;
          }
        };
      }
      return null;
    };
    myWrapperPanel.setContent(myInspectorTable);

    Splitter splitPane = new JBSplitter(false, "UiInspector.splitter.proportion", 0.5f);
    splitPane.setSecondComponent(myWrapperPanel);
    splitPane.setFirstComponent(new JBScrollPane(myHierarchyTree));
    add(splitPane, BorderLayout.CENTER);
    DataManager.registerDataProvider(splitPane, provider);

    myHierarchyTree.expandPath();

    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        close();
      }
    });

    getRootPane().getActionMap().put("CLOSE", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        close();
      }
    });
    updateHighlighting();
    getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "CLOSE");
  }

  private void openClass(String fqn, boolean requestFocus) {
    if (myProject != null) {
      try {
        String javaPsiFacadeFqn = "com.intellij.psi.JavaPsiFacade";
        PluginId pluginId = PluginManager.getPluginByClassNameAsNoAccessToClass(javaPsiFacadeFqn);
        Class<?> facade = null;
        if (pluginId != null) {
          IdeaPluginDescriptor plugin = PluginManager.getInstance().findEnabledPlugin(pluginId);
          if (plugin != null) {
            facade = Class.forName(javaPsiFacadeFqn, false, plugin.getPluginClassLoader());
          }
        }
        else {
          facade = Class.forName(javaPsiFacadeFqn);
        }
        if (facade != null) {
          Method getInstance = facade.getDeclaredMethod("getInstance", Project.class);
          Method findClass = facade.getDeclaredMethod("findClass", String.class, GlobalSearchScope.class);
          Object result = findClass.invoke(getInstance.invoke(null, myProject), fqn, GlobalSearchScope.allScope(myProject));
          if (result instanceof PsiElement) {
            PsiNavigateUtil.navigate((PsiElement)result, requestFocus);
          }
        }
      }
      catch (Exception ignore) {
      }
    }
  }

  public static String getDimensionServiceKey() {
    return "UiInspectorWindow";
  }

  private static Window findWindow(Component component) {
    DialogWrapper dialogWrapper = DialogWrapper.findInstance(component);
    if (dialogWrapper != null) {
      return dialogWrapper.getPeer().getWindow();
    }
    return null;
  }

  private InspectorTable getCurrentTable() {
    return myInspectorTable;
  }

  private void switchComponentsInfo(@NotNull java.util.List<? extends Component> components) {
    if (components.isEmpty()) return;
    myComponents.clear();
    myComponents.addAll(components);
    myInfo = null;
    setTitle(components.get(0).getClass().getName());
    myInspectorTable = new InspectorTable(components.get(0));
    myWrapperPanel.setContent(myInspectorTable);
  }

  private void switchClickInfo(@NotNull List<? extends PropertyBean> clickInfo) {
    myComponents.clear();
    myInfo = clickInfo;
    setTitle("Click Info");
    myInspectorTable = new InspectorTable(clickInfo);
    myWrapperPanel.setContent(myInspectorTable);
  }

  @Override
  public void dispose() {
    DimensionService.getInstance().setSize(getDimensionServiceKey(), getSize(), null);
    DimensionService.getInstance().setLocation(getDimensionServiceKey(), getLocation(), null);
    super.dispose();
    DialogWrapper.cleanupRootPane(rootPane);
    DialogWrapper.cleanupWindowListeners(this);
    Disposer.dispose(this);
  }

  public void close() {
    if (myInitialComponent instanceof JComponent) {
      ((JComponent)myInitialComponent).putClientProperty(UiInspectorAction.CLICK_INFO, null);
    }
    myIsHighlighted = false;
    myInfo = null;
    myComponents.clear();
    updateHighlighting();
    setVisible(false);
    Disposer.dispose(this);
  }

  public UiInspectorAction.UiInspector getInspector() {
    return myInspector;
  }

  private void updateHighlighting() {
    for (HighlightComponent component : myHighlightComponents) {
      JComponent glassPane = getGlassPane(component);
      if (glassPane != null) {
        glassPane.remove(component);
        glassPane.revalidate();
        glassPane.repaint();
      }
    }
    myHighlightComponents.clear();

    if (myIsHighlighted) {
      for (Component component : myComponents) {
        ContainerUtil.addIfNotNull(myHighlightComponents, createHighlighter(component, null));
      }
      if (myInfo != null) {
        Rectangle bounds = null;
        for (PropertyBean bean : myInfo) {
          if (UiInspectorAction.RENDERER_BOUNDS.equals(bean.propertyName)) {
            bounds = (Rectangle)bean.propertyValue;
            break;
          }
        }
        ContainerUtil.addIfNotNull(myHighlightComponents, createHighlighter(myInitialComponent, bounds));
      }
    }
  }

  @Nullable
  private static HighlightComponent createHighlighter(@NotNull Component component, @Nullable Rectangle bounds) {
    JComponent glassPane = getGlassPane(component);
    if (glassPane == null) return null;

    if (bounds != null) {
      bounds = SwingUtilities.convertRectangle(component, bounds, glassPane);
    }
    else {
      Point pt = SwingUtilities.convertPoint(component, new Point(0, 0), glassPane);
      bounds = new Rectangle(pt.x, pt.y, component.getWidth(), component.getHeight());
    }

    JBColor color = new JBColor(JBColor.GREEN, JBColor.RED);
    if (bounds.width == 0 || bounds.height == 0) {
      bounds.width = Math.max(bounds.width, 1);
      bounds.height = Math.max(bounds.height, 1);
      color = JBColor.BLUE;
    }

    Insets insets = component instanceof JComponent ? ((JComponent)component).getInsets() : JBInsets.emptyInsets();
    HighlightComponent highlightComponent = new HighlightComponent(color, insets);
    highlightComponent.setBounds(bounds);

    glassPane.add(highlightComponent);
    glassPane.revalidate();
    glassPane.repaint();

    return highlightComponent;
  }

  @Nullable
  private static JComponent getGlassPane(@NotNull Component component) {
    JRootPane rootPane = SwingUtilities.getRootPane(component);
    return rootPane == null ? null : (JComponent)rootPane.getGlassPane();
  }

  private static final class HighlightComponent extends JComponent {
    @NotNull private final Color myColor;
    @NotNull private final Insets myInsets;

    private HighlightComponent(@NotNull Color c, @NotNull Insets insets) {
      myColor = c;
      myInsets = insets;
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2d = (Graphics2D)g;

      Color oldColor = g2d.getColor();
      Composite old = g2d.getComposite();
      g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));

      Rectangle r = getBounds();
      RectanglePainter.paint(g2d, 0, 0, r.width, r.height, 0, myColor, null);

      ((Graphics2D)g).setPaint(myColor.darker());
      for (int i = 0; i < myInsets.left; i++) {
        LinePainter2D.paint(g2d, i, myInsets.top, i, r.height - myInsets.bottom - 1);
      }
      for (int i = 0; i < myInsets.right; i++) {
        LinePainter2D.paint(g2d, r.width - i - 1, myInsets.top, r.width - i - 1, r.height - myInsets.bottom - 1);
      }
      for (int i = 0; i < myInsets.top; i++) {
        LinePainter2D.paint(g2d, 0, i, r.width, i);
      }
      for (int i = 0; i < myInsets.bottom; i++) {
        LinePainter2D.paint(g2d, 0, r.height - i - 1, r.width, r.height - i - 1);
      }

      g2d.setComposite(old);
      g2d.setColor(oldColor);
    }
  }

  private abstract static class MyTextAction extends IconWithTextAction implements DumbAware {
    private MyTextAction(Supplier<String> text) {
      super(text);
    }
  }
}