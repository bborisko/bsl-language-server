/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
//import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
//import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;
//import org.apache.commons.lang3.StringUtils;
//import org.eclipse.lsp4j.DocumentRangeFormattingParams;
//import org.eclipse.lsp4j.FormattingOptions;
//import org.eclipse.lsp4j.Position;
//import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.ArrayList;
//import java.util.Arrays;
import java.util.Collections;
//import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
//import java.util.Set;
//import java.util.stream.Collectors;

@Component
public final class JsonProvider {

  // Denis Begin


  private static final Gson PRETTY_PRINT_GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final Gson GSON = new Gson();

  public static String toJson(ParseTree tree) {
    return toJson(tree, true);
  }

  public static String toJson(ParseTree tree, boolean prettyPrint) {
    return prettyPrint ? PRETTY_PRINT_GSON.toJson(toMap(tree)) : GSON.toJson(toMap(tree));
  }

  public static Map<String, Object> toMap(ParseTree tree) {
    Map<String, Object> map = new LinkedHashMap<>();
    traverse(tree, map);
    return map;
  }

  public static void traverse(ParseTree tree, Map<String, Object> map) {

    if (tree instanceof TerminalNodeImpl) {
      Token token = ((TerminalNodeImpl) tree).getSymbol();
      map.put("type", token.getType());
      map.put("text", token.getText());
      //map.put("line", token.getLine());
    }
    else {
      List<Map<String, Object>> children = new ArrayList<>();
      String name = tree.getClass().getSimpleName().replaceAll("Context$", "");
      map.put(Character.toLowerCase(name.charAt(0)) + name.substring(1), children);

      for (int i = 0; i < tree.getChildCount(); i++) {
        Map<String, Object> nested = new LinkedHashMap<>();
        children.add(nested);
        traverse(tree.getChild(i), nested);
      }
    }
  }

  // Denis End

  public static String sha256(final String base) {
    try{
      final MessageDigest digest = MessageDigest.getInstance("SHA1");
      final byte[] hash = digest.digest(base.getBytes("UTF-8"));
      final StringBuilder hexString = new StringBuilder();
      for (int i = 0; i < hash.length; i++) {
        final String hex = Integer.toHexString(0xff & hash[i]);
        if(hex.length() == 1)
          hexString.append('0');
        hexString.append(hex);
      }
      return hexString.toString();
    } catch(Exception ex){
      throw new RuntimeException(ex);
    }
  }

  public List<TextEdit> getJson(DocumentContext documentContext) {
    List<Token> tokens = documentContext.getTokens();
    if (tokens.isEmpty()) {
      return Collections.emptyList();
    }

    // Denis Begin
    ParseTree ast = documentContext.getAst();
    String toJson = toJson(ast);
    //System.out.println(toJson);


    for (int i = 0; i < ast.getChildCount(); i++) {

      if (ast.getChild(i) instanceof BSLParser.SubsContext) {
        ParseTree subs = ast.getChild(i);
        //int a = 1;
        for (int sub_num = 0; sub_num < subs.getChildCount(); sub_num++) {
          ParseTree sub = subs.getChild(sub_num);
          //String sub_inf = "start_line:" + sub.start.line;
          //System.out.println(sub_inf);
          //int a = ((CommonToken) ((BSLParser.SubContext) sub).start).line;
          Token ct = ((BSLParser.SubContext) sub).start;
          int start_line = ct.getLine();
          int stop_line = ((BSLParser.SubContext) sub).stop.getLine();
          String json = toJson(sub);
          String sha256 = sha256(json);
          //boolean is_eq = "7357d317af24fb2e1f8b7d7a699aecf423f2ba6660665bdd89ab3360d136f9bc".equals(sha256);

          //boolean is_eq = "e580dcb2e4ca6b75208b5412f2b80b2ccfe48e0c6ce85fe17d17638dd98117d7" == sha256 ? true : false;
          //int b = 1;      //"e580dcb2e4ca6b75208b5412f2b80b2ccfe48e0c6ce85fe17d17638dd98117d7"
          //System.out.println(sub_inf);
        }
      }
    }
    // Denis End
    return Collections.emptyList();
  }


}

