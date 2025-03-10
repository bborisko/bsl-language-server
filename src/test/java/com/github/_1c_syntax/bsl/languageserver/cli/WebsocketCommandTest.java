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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import picocli.CommandLine;
import picocli.spring.PicocliSpringFactory;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class WebsocketCommandTest {

  @SpyBean
  private LanguageServerConfiguration configuration;

  @Autowired
  private PicocliSpringFactory factory;

  @Test
  void testCallReturnsNonStandardCode() {
    // given
    var commandLine = new CommandLine(WebsocketCommand.class, factory);

    // when
    var call = commandLine.execute();

    // then
    assertThat(call).isEqualTo(-1);
    verify(configuration, never()).update(any());
  }

  @Test
  void testCallUpdatesConfigurationFile() {

    // given
    var commandLine = new CommandLine(WebsocketCommand.class, factory);

    // when
    commandLine.execute("-c", "src/test/resources/.empty-bsl-language-server.json");

    // then
    verify(configuration, times(1)).update(any());
  }
}