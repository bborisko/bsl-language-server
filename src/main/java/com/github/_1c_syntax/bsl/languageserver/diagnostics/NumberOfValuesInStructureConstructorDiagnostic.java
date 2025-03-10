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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * @author Leon Chagelishvili &lt;lChagelishvily@gmail.com&gt;
 */
@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  scope = DiagnosticScope.ALL,
  minutesToFix = 10,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.BRAINOVERLOAD
  }
)
public class NumberOfValuesInStructureConstructorDiagnostic extends AbstractVisitorDiagnostic {

  private static final int MAX_VALUES_COUNT = 3;

  @DiagnosticParameter(
    type = Integer.class,
    defaultValue = "" + MAX_VALUES_COUNT
  )
  private int maxValuesCount = MAX_VALUES_COUNT;

  @Override
  public ParseTree visitNewExpression(BSLParser.NewExpressionContext ctx) {

    if (ctx.typeName() == null) {
      return super.visitNewExpression(ctx);
    }

    if (!(DiagnosticHelper.isStructureType(ctx.typeName()) || DiagnosticHelper.isFixedStructureType(ctx.typeName()))) {
      return super.visitNewExpression(ctx);
    }

    BSLParser.DoCallContext doCallContext = ctx.doCall();

    if (doCallContext != null &&
      doCallContext.callParamList().callParam().size() > maxValuesCount + 1) {
      diagnosticStorage.addDiagnostic(ctx);
    }
    return super.visitNewExpression(ctx);
  }

}
