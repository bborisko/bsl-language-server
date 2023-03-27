/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * BSL Language Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * BSL Language Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BSL Language Server.
 */
package com.github._1c_syntax.bsl.languageserver.reporters.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.MetricStorage;
import com.github._1c_syntax.mdclasses.mdo.AbstractMDObjectBase;
import com.github._1c_syntax.utils.Absolute;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;
import org.eclipse.lsp4j.Diagnostic;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
// Denis Begin
//@Value
@Getter
@Setter
@ToString
@EqualsAndHashCode
// Denis End
@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileInfo {
  Path path;
  String mdoRef;
  List<Diagnostic> diagnostics;
  MetricStorage metrics;

  // Denis Begin
  //Map<String, Object> diagnosticsHash;
  //Object diagnosticsHash = new Object();
  // Denis End

  public FileInfo(String sourceDir, DocumentContext documentContext, List<Diagnostic> diagnostics) {
    URI uri = documentContext.getUri();
    path = Absolute.path(sourceDir).relativize(Absolute.path(uri));
    this.diagnostics = new ArrayList<>(diagnostics);
    metrics = documentContext.getMetrics();
    Optional<AbstractMDObjectBase> mdObjectBase = documentContext.getMdObject();
    if (mdObjectBase.isPresent()) {
      mdoRef = mdObjectBase.get().getMdoReference().getMdoRef();
    } else {
      mdoRef = "";
    }
  }

  // Denis Begin
  public FileInfo(Path path, String mdoRef, List<Diagnostic> diagnostics, MetricStorage metrics) {
    this.path = path;
    this.mdoRef = mdoRef;
    this.diagnostics = diagnostics;
    this.metrics = metrics;
  }
  // Denis End
}
