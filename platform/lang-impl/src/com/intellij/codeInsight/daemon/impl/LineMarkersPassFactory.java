// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

final class LineMarkersPassFactory extends AbstractLineMarkersPassFactory {
  @Override
  public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
    boolean serializeCodeInsightPasses =
      ((TextEditorHighlightingPassRegistrarImpl)registrar).isSerializeCodeInsightPasses();
    registrar.registerTextEditorHighlightingPass(new Factory(serializeCodeInsightPasses ? LineMarkersPass.Mode.FAST : LineMarkersPass.Mode.ALL),
                                                 null,
                                                 new int[]{Pass.UPDATE_ALL}, false, Pass.LINE_MARKERS);
  }
}