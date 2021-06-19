/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.AbstractCallNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BinaryOperationNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslOperator;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionParseTreeRewriter;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.NodeEqualityComparer;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.TernaryOperatorNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.TransitiveOperationsIgnoringComparer;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.UnaryOperationNode;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.SUSPICIOUS
  }
)
public class IdenticalExpressionsDiagnostic extends AbstractVisitorDiagnostic {

  private static final int MIN_EXPRESSION_SIZE = 3;

  @Override
  public ParseTree visitExpression(BSLParser.ExpressionContext ctx) {

    if (sufficientSize(ctx)) {
      return ctx;
    }

    var rewriter = new ExpressionParseTreeRewriter();
    rewriter.visitExpression(ctx);
    var tree = rewriter.getExpressionTree();

    var binariesList = flattenBinaryOperations(tree);
    if (binariesList.isEmpty()) {
      return ctx;
    }

    var comparer = new TransitiveOperationsIgnoringComparer();
    comparer.logicalOperationsAsTransitive(true);
    binariesList
      .stream()
      .filter(x -> checkEquality(comparer, x))
      .forEach(x -> diagnosticStorage.addDiagnostic(ctx,
        info.getMessage(x.getSourceCodeOperator(), getSomeText(x))));

    return ctx;
  }

  private boolean checkEquality(NodeEqualityComparer comparer, BinaryOperationNode node) {

    var justEqual = comparer.areEqual(node.getLeft(), node.getRight());
    if (justEqual) {
      return true;
    }

    if (isComplementary(node)) {
      // left не должен встречаться ни в одной из подветок right
      var searchableLeft = node.getLeft();
      BinaryOperationNode complementaryNode = node.getRight().cast();
      while (true) {
        var equal = comparer.areEqual(searchableLeft, complementaryNode.getLeft()) ||
          comparer.areEqual(searchableLeft, complementaryNode.getRight());

        if(equal)
          return true;

        if(isComplementary(complementaryNode)) {
          complementaryNode = complementaryNode.getRight().cast();
        }
        else {
          break;
        }
      }
    }

    return false;
  }

  private String getSomeText(BinaryOperationNode node) {

    if (node.getLeft().getRepresentingAst() != null) {
      return node.getLeft().getRepresentingAst().getText();
    }

    if (node.getRight().getRepresentingAst() != null) {
      return node.getRight().getRepresentingAst().getText();
    }

    return node.getRepresentingAst() == null ? "" : node.getRepresentingAst().getText();
  }

  private List<BinaryOperationNode> flattenBinaryOperations(BslExpression tree) {
    var list = new ArrayList<BinaryOperationNode>();
    gatherBinaryOperations(list, tree);
    return list;
  }

  private void gatherBinaryOperations(List<BinaryOperationNode> list, BslExpression tree) {
    switch (tree.getNodeType()) {
      case CALL:
        for (var expr : tree.<AbstractCallNode>cast().arguments()) {
          gatherBinaryOperations(list, expr);
        }
        break;
      case UNARY_OP:
        gatherBinaryOperations(list, tree.<UnaryOperationNode>cast().getOperand());
        break;
      case TERNARY_OP:
        var ternary = (TernaryOperatorNode) tree;
        gatherBinaryOperations(list, ternary.getCondition());
        gatherBinaryOperations(list, ternary.getTruePart());
        gatherBinaryOperations(list, ternary.getFalsePart());
        break;
      case BINARY_OP:
        var binary = (BinaryOperationNode) tree;
        var operator = binary.getOperator();

        // разыменования отбросим, хотя comparer их и не зачтет, но для производительности
        // лучше выкинем их сразу
        if (operator == BslOperator.DEREFERENCE || operator == BslOperator.INDEX_ACCESS) {
          return;
        }

        // одинаковые умножения и сложения - не считаем, см. тесты
        if (operator != BslOperator.ADD && operator != BslOperator.MULTIPLY) {
          list.add(binary);
        }

        gatherBinaryOperations(list, binary.getLeft());
        gatherBinaryOperations(list, binary.getRight());
        break;

      default:
        break; // для спокойствия сонара
    }
  }

  private boolean isComplementary(BinaryOperationNode binary) {
    var operator = binary.getOperator();
    if ((operator == BslOperator.OR || operator == BslOperator.AND) && binary.getRight() instanceof BinaryOperationNode) {
      return ((BinaryOperationNode) binary.getRight()).getOperator() == operator;
    }

    return false;
  }

  private static boolean sufficientSize(BSLParser.ExpressionContext ctx) {
    return ctx.children.size() < MIN_EXPRESSION_SIZE;
  }
}
