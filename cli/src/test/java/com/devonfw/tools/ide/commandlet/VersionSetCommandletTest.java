package com.devonfw.tools.ide.commandlet;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.devonfw.tools.ide.context.AbstractIdeContextTest;
import com.devonfw.tools.ide.context.IdeContext;

/**
 * Integration test of {@link VersionSetCommandlet}.
 */
public class VersionSetCommandletTest extends AbstractIdeContextTest {

  /**
   * Test of {@link VersionSetCommandlet} run.
   *
   * @throws IOException on error.
   */
  @Test
  public void testVersionSetCommandletRun() throws IOException {

    // arrange
    String path = "workspaces/foo-test/my-git-repo";
    IdeContext context = newContext("basic", path, true);
    VersionSetCommandlet versionSet = context.getCommandletManager().getCommandlet(VersionSetCommandlet.class);
    versionSet.tool.setValueAsString("mvn");
    versionSet.version.setValueAsString("3.1.0");
    // act
    versionSet.run();
    // assert
    Path settingsIdeProperties = context.getSettingsPath().resolve("ide.properties");
    assertThat(settingsIdeProperties).hasContent("""
        #********************************************************************************
        # This file contains project specific environment variables
        #********************************************************************************

        JAVA_VERSION=17*
        MVN_VERSION=3.1.0
        ECLIPSE_VERSION=2023-03
        INTELLIJ_EDITION=ultimate

        IDE_TOOLS=mvn,eclipse

        BAR=bar-${SOME}
                """);
  }
}