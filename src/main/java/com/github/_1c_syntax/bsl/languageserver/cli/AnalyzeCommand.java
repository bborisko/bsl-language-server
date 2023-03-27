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
package com.github._1c_syntax.bsl.languageserver.cli;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.MetricStorage;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.reporters.ReportersAggregator;
import com.github._1c_syntax.bsl.languageserver.reporters.data.AnalysisInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.data.FileInfo;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.bsl.types.MdoReference;
import com.github._1c_syntax.mdclasses.mdo.AbstractMDObjectBase;
import com.github._1c_syntax.utils.Absolute;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.github._1c_syntax.bsl.languageserver.providers.JsonProvider.sha256;
import static picocli.CommandLine.Option;
import static com.github._1c_syntax.bsl.languageserver.providers.JsonProvider.toJson;

/**
 * Выполнение анализа
 * Ключ команды:
 * -a, (--analyze)
 * Параметры:
 * -s, (--srcDir) &lt;arg&gt; -        Путь к каталогу исходных файлов.
 * Возможно указывать как в абсолютном, так и относительном виде. Если параметр опущен,
 * то анализ выполняется в текущем каталоге запуска.
 * -o, (--outputDir) &lt;arg&gt; -     Путь к каталогу размещения отчетов - результатов анализа.
 * Возможно указывать как в абсолютном, так и относительном виде. Если параметр опущен,
 * то файлы отчета будут сохранены в текущем каталоге запуска.
 * -w, (--workspaceDir) &lt;arg&gt; -  Путь к каталогу проекта, относительно которого располагаются исходные файлы.
 * Возможно указывать как в абсолютном, так и в относительном виде. Если параметр опущен,
 * то пути к исходным файлам будут указываться относительно текущего каталога запуска.
 * -c, (--configuration) &lt;arg&gt; - Путь к конфигурационному файлу BSL Language Server (.bsl-language-server.json).
 * Возможно указывать как в абсолютном, так и относительном виде. Если параметр опущен,
 * то будут использованы настройки по умолчанию.
 * -r, (--reporter) &lt;arg&gt; -      Ключи "Репортеров", т.е. форматов отчетов, котрые необходимо сгенерировать после
 * выполнения анализа. Может быть указано более одного ключа. Если параметр опущен,
 * то вывод результата будет призведен в консоль.
 * -q, (--silent)              -       Флаг для отключения вывода прогресс-бара и дополнительных сообщений в консоль
 * Выводимая информация:
 * Выполняет анализ каталога исходных файлов и генерацию файлов отчета. Для каждого указанного ключа "Репортера"
 * создается отдельный файл (каталог файлов). Реализованные "репортеры" находятся в пакете "reporter".
 **/
@Slf4j
@Command(
  name = "analyze",
  aliases = {"-a", "--analyze"},
  description = "Run analysis and get diagnostic info",
  usageHelpAutoWidth = true,
  footer = "@|green Copyright(c) 2018-2022|@")
@Component
@RequiredArgsConstructor
public class AnalyzeCommand implements Callable<Integer> {

  private static class ReportersKeys extends ArrayList<String> {
    ReportersKeys(ReportersAggregator aggregator) {
      super(aggregator.reporterKeys());
    }
  }

  @Option(
    names = {"-h", "--help"},
    usageHelp = true,
    description = "Show this help message and exit")
  private boolean usageHelpRequested;

  @Option(
    names = {"-w", "--workspaceDir"},
    description = "Workspace directory",
    paramLabel = "<path>",
    defaultValue = "")
  private String workspaceDirOption;

  @Option(
    names = {"-s", "--srcDir"},
    description = "Source directory",
    paramLabel = "<path>",
    defaultValue = "")
  private String srcDirOption;

  @Option(
    names = {"-o", "--outputDir"},
    description = "Output report directory",
    paramLabel = "<path>",
    defaultValue = "")
  private String outputDirOption;

  @Option(
    names = {"-c", "--configuration"},
    description = "Path to language server configuration file",
    paramLabel = "<path>",
    defaultValue = "")
  private String configurationOption;

  @Option(
    names = {"-r", "--reporter"},
    paramLabel = "<keys>",
    completionCandidates = ReportersKeys.class,
    description = "Reporter key (${COMPLETION-CANDIDATES})")
  private String[] reportersOptions = {};

  @Option(
    names = {"-q", "--silent"},
    description = "Silent mode")
  private boolean silentMode;

  // Denis Start
  @Option(
    names = {"-1", "--1C"},
    description = "1C mode")
  private boolean _1CMode;
  // Denis End

  // EgorD +
  @Option(
    names = {"-l", "--logs"},
    description = "Show logs")
  private boolean showLogs;
  // EgorD -

  // Egor +
  @Option(
    names = {"-m", "--makets"},
    description = "Add tamplates Diagnostic")
  private boolean TamplatesDiag;
  // Egor -

  private final ReportersAggregator aggregator;
  private final LanguageServerConfiguration configuration;
  private final ServerContext context;
  private static final Pattern externalFileName = CaseInsensitivePattern.compile("([.][.][/])");
  private String typeConf = "";

  public Integer call() {

    var workspaceDir = Absolute.path(workspaceDirOption);
    if (!workspaceDir.toFile().exists()) {
      LOGGER.error("Workspace dir `{}` is not exists", workspaceDir);
      return 1;
    }

    var srcDir = Absolute.path(srcDirOption);
    if (!srcDir.toFile().exists()) {
      LOGGER.error("Source dir `{}` is not exists", srcDir);
      return 1;
    }

    var configurationFile = new File(configurationOption);
    if (configurationFile.exists()) {
      configuration.update(configurationFile);
    }

    var configurationPath = LanguageServerConfiguration.getCustomConfigurationRoot(configuration, srcDir);
    context.setConfigurationRoot(configurationPath);


    File fileConfiguration = configuration.getConfigurationFile();
    if (fileConfiguration != null && !fileConfiguration.toString().isEmpty()) {
      if (fileConfiguration.getAbsolutePath().endsWith(".mdo")) {
        typeConf = "EDT";
      } else {
        typeConf = "1C";
      }
    }
    else {
      typeConf = "EDT";
    }

    Matcher matcher = externalFileName.matcher(this.srcDirOption);

    int resultMatch;
    for(resultMatch = 0; matcher.find(); ++resultMatch) {
    }

    if (resultMatch > 1) {
      LOGGER.error("Контроль вхождений '../'. Разрешен 1 вход.");
      return 1;
    }

    var str = new String[]{"bsl", "os"};
    if (TamplatesDiag) {
      str = new String[]{"bsl", "os", "txt"};
    }

    var files = (List<File>) FileUtils.listFiles(srcDir.toFile(), str, true);

    context.populateContext(files);

    List<FileInfo> fileInfos;
    if (silentMode) {
      fileInfos = files.parallelStream()
        .map((File file) -> getFileInfoFromFile(workspaceDir, file))
        .collect(Collectors.toList());
    } else {
      try (ProgressBar pb = new ProgressBarBuilder()
        .setTaskName("Analyzing files...")
        .setInitialMax(files.size())
        .setStyle(ProgressBarStyle.ASCII)
        .build()) {
        fileInfos = files.parallelStream()
          .map((File file) -> {
            pb.step();
            return getFileInfoFromFile(workspaceDir, file);
          })
          .collect(Collectors.toList());
      }
    }

    var analysisInfo = new AnalysisInfo(LocalDateTime.now(), fileInfos, srcDir.toString());
    var outputDir = Absolute.path(outputDirOption);
    aggregator.report(analysisInfo, outputDir);
    return 0;
  }

  public String[] getReportersOptions() {
    return reportersOptions.clone();
  }

  @SneakyThrows
  private FileInfo getFileInfoFromFile(Path srcDir, File file) {
    var textDocumentContent = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
    var documentContext = context.addDocument(file.toURI());
    context.rebuildDocument(documentContext);

    var filePath = srcDir.relativize(Absolute.path(file));

    String fileExtension = FilenameUtils.getExtension(file.getAbsolutePath());
    String fileNameWithOutExt = FilenameUtils.removeExtension(file.getAbsolutePath());
    if (fileExtension.equals("txt") && !file.getName().equals("Template.txt"))
    {
      return new FileInfo(filePath, "", new ArrayList<>(), new MetricStorage());
    }

    List<Diagnostic> diagnostics = documentContext.getDiagnostics();

    if (file.getName().equals("Template.txt"))
    {
      for (Diagnostic strDiag : diagnostics
      ) {
        if (strDiag.getCode().getLeft().equals("ParseError")){
          diagnostics.remove(strDiag);
          break;
        }
      }
    }

    MetricStorage metrics = documentContext.getMetrics();
    var mdoRef = documentContext.getMdObject()
      .map(AbstractMDObjectBase::getMdoReference)
      .map(MdoReference::getMdoRef)
      .orElse("");

    // Denis Begin
    @JsonInclude(JsonInclude.Include.NON_NULL)
    class CodeDiags {
      public String code;
      public ArrayList<Diagnostic> diags;

      public CodeDiags(String code, ArrayList<Diagnostic> diags) {
        this.code = code;
        this.diags = diags;
      }
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    class Hash {
      public String hash;
      public ArrayList<CodeDiags> codeDiags;
      public String fileHash;

      public Hash(String hash, ArrayList<CodeDiags> codeDiags) {
        this.hash = hash;
        this.codeDiags = codeDiags;
      }
      public Hash(String hash, String fileHash) {
        this.hash = hash;
        this.fileHash = fileHash;
      }
    }

    //Map<String, Object> hashes = new TreeMap<String, Object>();
    ArrayList<Hash> hashes = new ArrayList<>();
    try {

      if (_1CMode && !textDocumentContent.isEmpty()) {
        ParseTree ast = documentContext.getAst();
        ArrayList<ParseTree> blocks = new ArrayList<ParseTree>();
        for (int i = 0; i < ast.getChildCount(); i++) {
          ParseTree ast_child = ast.getChild(i);
          if (ast_child instanceof BSLParser.ModuleVarsContext && ast_child.getChild(0) != null) {
            blocks.add(ast_child.getChild(0));
          } else if (ast_child instanceof BSLParser.SubsContext) {
            for (int subs_idx = 0; subs_idx < ast_child.getChildCount(); subs_idx++) {
              blocks.add(ast_child.getChild(subs_idx));
            }
          } else if (ast_child instanceof BSLParser.FileCodeBlockContext) {
            blocks.add(ast_child.getChild(0));
          }
        }

        for (int block_idx = 0; block_idx < blocks.size(); block_idx++) {
          ParseTree block = blocks.get(block_idx);
          Token ct = ((BSLParserRuleContext) block).start;
          int start_line = ct.getLine();
          int stop_line = ((BSLParserRuleContext) block).stop.getLine();

          //codesLines Map<String, Map<>>
          //linesCols Map<Integer, Map<>>
          //colsDiags Map<Integer, ArrayList<Diagnostic>>

          Map<String, Object> codesLines = new TreeMap<String, Object>();
          for (int diag_num = 0; diag_num < diagnostics.size(); diag_num++) {
            Diagnostic diag = diagnostics.get(diag_num);
            if (!diag.getMessage().equals("GPN_CommonModulePrivileged")){
              continue;
            }

            int diag_start_line = diag.getRange().getStart().getLine() + 1;
            if (diag_start_line >= start_line && diag_start_line <= stop_line) {
              String code = diag.getCode().getLeft();
              @SuppressWarnings("unchecked")
              Map<Integer, Object> linesCols = (Map<Integer, Object>) codesLines.get(code);
              if (linesCols == null) {
                linesCols = new TreeMap<Integer, Object>();
                codesLines.put(code, linesCols);
              }
              @SuppressWarnings("unchecked")
              Map<Integer, Object> lineCols = (Map<Integer, Object>) linesCols.get(diag_start_line);
              if (lineCols == null) {
                lineCols = new TreeMap<Integer, Object>();
                linesCols.put(diag_start_line, lineCols);
              }

              int diag_col_start = diag.getRange().getStart().getCharacter();
              @SuppressWarnings("unchecked")
              ArrayList<Diagnostic> colDiags = (ArrayList<Diagnostic>) lineCols.get(diag_col_start);
              if (colDiags == null) {
                colDiags = new ArrayList<Diagnostic>();
                lineCols.put(diag_col_start, colDiags);
              }
              colDiags.add(diag);
            }
          }

          if (codesLines.size() > 0) {
            ArrayList<CodeDiags> codesDiags = new ArrayList<>();
            for (String code : codesLines.keySet()) {
              @SuppressWarnings("unchecked")
              Map<Integer, Object> lineCols = (Map<Integer, Object>) codesLines.get(code);
              ArrayList<Diagnostic> codeDiags = new ArrayList<>();
              for (Object line : ((TreeMap) lineCols).keySet()) {
                @SuppressWarnings("unchecked")
                Map<Integer, Object> colDiags = (Map<Integer, Object>) lineCols.get(line);
                for (Object col : ((TreeMap) colDiags).keySet()) {
                  @SuppressWarnings("unchecked")
                  ArrayList<Diagnostic> diags = (ArrayList<Diagnostic>) colDiags.get(col);
                  codeDiags.addAll(diags);
                }
              }
              codesDiags.add(new CodeDiags(code, codeDiags));
            }
            String json = toJson(block);
            String sha256 = sha256(json);
            hashes.add(new Hash(sha256, codesDiags));
          }
        }

        //Egor +
        if (typeConf.equals("EDT")) {
          for (int diag_num = 0; diag_num < diagnostics.size(); diag_num++) {
            Diagnostic diag = diagnostics.get(diag_num);
            List<DiagnosticRelatedInformation> diagRelatedInformation = diag.getRelatedInformation();
            if (diagRelatedInformation != null && diagRelatedInformation.size() > 0) {
              if (diagRelatedInformation.get(0).getMessage().equals("MetadataDiagnostic")) {
                //hashes.add(new Hash(diag.getCode().toString(), sha256(textDocumentContent)));

                ParseTree block = blocks.get(0);
                String code = diag.getCode().getLeft();
                Map<Integer, Object> linesCols = new TreeMap<Integer, Object>();
                Map<String, Object> codesLines = new TreeMap<String, Object>();
                codesLines.put(code, linesCols);
                ArrayList<Diagnostic> colDiags = new ArrayList<Diagnostic>();
                colDiags.add(diag);
                String json = toJson(block);
                String sha256 = sha256(json);

                ArrayList<CodeDiags> codesDiags = new ArrayList<>();
                codesDiags.add(new CodeDiags(code, colDiags));
                hashes.add(new Hash(sha256, codesDiags));
              }
            }
          }
        }
        //Egor -

        if (blocks.size() == 0 && diagnostics.size() > 0
          && diagnostics.get(0).getCode().getLeft().equals("ParseError")) {
          hashes.add(new Hash("ParseError", sha256(textDocumentContent)));
        }
      }
    } catch (Exception e) {
      if (showLogs) {
        LOGGER.error("\n ______ERROR READ FILE______" + file.toURI().toString());
        //throw new RuntimeException(e);
      }
    }
    // Denis End

    var fileInfo = new FileInfo(filePath, mdoRef, diagnostics, metrics);

    // clean up AST after diagnostic computing to free up RAM.
    context.tryClearDocument(documentContext);

    return fileInfo;
  }
}
