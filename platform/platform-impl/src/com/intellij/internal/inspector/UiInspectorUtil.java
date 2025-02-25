// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.inspector;

import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import com.intellij.ide.ui.customization.CustomisedActionGroup;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionWithDelegate;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class UiInspectorUtil {
  private static final String PROPERTY_KEY = "UiInspectorContextProvider.Key";

  public static void registerProvider(@NotNull JComponent component, @NotNull UiInspectorContextProvider provider) {
    component.putClientProperty(PROPERTY_KEY, provider);
  }

  public static UiInspectorContextProvider getProvider(@NotNull Object component) {
    if (component instanceof UiInspectorContextProvider) {
      return ((UiInspectorContextProvider)component);
    }
    if (component instanceof JComponent) {
      return ObjectUtils.tryCast(((JComponent)component).getClientProperty(PROPERTY_KEY), UiInspectorContextProvider.class);
    }
    return null;
  }

  @Nullable
  public static String getActionId(@NotNull AnAction action) {
    if (action instanceof CustomisedActionGroup) {
      action = ((CustomisedActionGroup)action).getOrigin();
    }
    return ActionManager.getInstance().getId(action);
  }

  @NotNull
  public static List<PropertyBean> collectActionGroupInfo(@NotNull @NonNls String prefix,
                                                          @NotNull ActionGroup group,
                                                          @Nullable String place) {
    List<PropertyBean> result = new ArrayList<>();

    if (place != null) {
      result.add(new PropertyBean(prefix + " Place", place, true));
    }

    String toolbarId = getActionId(group);
    result.add(new PropertyBean(prefix + " Group", toolbarId, true));

    Set<String> ids = new HashSet<>();
    recursiveCollectGroupIds(group, ids);
    ContainerUtil.addIfNotNull(ids, toolbarId);
    if (ids.size() > 1 ||
        ids.size() == 1 && toolbarId == null) {
      result.add(new PropertyBean("All Groups", StringUtil.join(ids, ", "), true));
    }
    return result;
  }

  @NotNull
  public static List<PropertyBean> collectAnActionInfo(@NotNull AnAction action) {
    List<PropertyBean> result = new ArrayList<>();
    result.add(new PropertyBean("Action", action.getClass().getName(), true));

    boolean isGroup = action instanceof ActionGroup;
    result.add(new PropertyBean("Action" + (isGroup ? " Group" : "") + " ID", getActionId(action), true));

    final ClassLoader classLoader = action.getClass().getClassLoader();
    if (classLoader instanceof PluginAwareClassLoader) {
      result.add(new PropertyBean("Action Plugin ID", ((PluginAwareClassLoader)classLoader).getPluginId().getIdString(), true));
    }

    if (action instanceof ActionWithDelegate<?>) {
      Object delegate = ((ActionWithDelegate<?>)action).getDelegate();
      if (delegate instanceof AnAction) {
        result.add(new PropertyBean("Action Delegate", delegate.getClass().getName(), true));
        result.add(new PropertyBean("Action Delegate ID", getActionId((AnAction)delegate), true));
      }
      result.add(new PropertyBean("Action Delegate toString", delegate, false));
    }

    return result;
  }

  private static void recursiveCollectGroupIds(@NotNull ActionGroup group, @NotNull Set<? super String> result) {
    for (AnAction action : group.getChildren(null)) {
      if (action instanceof ActionGroup) {
        ActionGroup child = (ActionGroup)action;
        ContainerUtil.addIfNotNull(result, getActionId(child));
        recursiveCollectGroupIds(child, result);
      }
    }
  }

  public static @NotNull String getComponentName(@NotNull Component component) {
    String name = getClassName(component);

    String componentName = component.getName();
    if (StringUtil.isNotEmpty(componentName)) {
      name += " \"" + componentName + "\"";
    }
    return name;
  }

  public static @NotNull String getClassName(@NotNull Object value) {
    Class<?> clazz0 = value.getClass();
    Class<?> clazz = clazz0.isAnonymousClass() ? clazz0.getSuperclass() : clazz0;
    return clazz.getSimpleName();
  }
}
