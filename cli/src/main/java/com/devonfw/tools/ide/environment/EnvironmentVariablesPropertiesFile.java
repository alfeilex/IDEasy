package com.devonfw.tools.ide.environment;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.devonfw.tools.ide.context.IdeContext;
import com.devonfw.tools.ide.variable.IdeVariables;
import com.devonfw.tools.ide.variable.VariableDefinition;

/**
 * Implementation of {@link EnvironmentVariables}.
 */
public final class EnvironmentVariablesPropertiesFile extends EnvironmentVariablesMap {

  private static final String NEWLINE = "\n";

  private final EnvironmentVariablesType type;

  private Path propertiesFilePath;

  private final Map<String, String> variables;

  private final Set<String> exportedVariables;

  private final Set<String> modifiedVariables;

  /**
   * The constructor.
   *
   * @param parent the parent {@link EnvironmentVariables} to inherit from.
   * @param type the {@link #getType() type}.
   * @param propertiesFilePath the {@link #getSource() source}.
   * @param context the {@link IdeContext}.
   */
  public EnvironmentVariablesPropertiesFile(AbstractEnvironmentVariables parent, EnvironmentVariablesType type,
      Path propertiesFilePath, IdeContext context) {

    super(parent, context);
    Objects.requireNonNull(type);
    assert (type != EnvironmentVariablesType.RESOLVED);
    this.type = type;
    this.propertiesFilePath = propertiesFilePath;
    this.variables = new HashMap<>();
    this.exportedVariables = new HashSet<>();
    this.modifiedVariables = new HashSet<>();
    load();
  }

  private void load() {

    if (this.propertiesFilePath == null) {
      return;
    }
    if (!Files.exists(this.propertiesFilePath)) {
      this.context.trace("Properties not found at {}", this.propertiesFilePath);
      return;
    }
    this.context.trace("Loading properties from {}", this.propertiesFilePath);
    boolean legacyProperties = this.propertiesFilePath.getFileName().toString().equals(LEGACY_PROPERTIES);
    try (BufferedReader reader = Files.newBufferedReader(this.propertiesFilePath)) {
      String line;
      do {
        line = reader.readLine();
        if (line != null) {
          VariableLine variableLine = VariableLine.of(line, this.context, getSource());
          String name = variableLine.getName();
          if (name != null) {
            VariableLine migratedVariableLine = migrateLine(variableLine, false);
            if (migratedVariableLine == null) {
              this.context.warning("Illegal variable definition: {}", variableLine);
              continue;
            }
            String migratedName = migratedVariableLine.getName();
            String migratedValue = migratedVariableLine.getValue();
            boolean legacyVariable = IdeVariables.isLegacyVariable(name);
            if (legacyVariable && !legacyProperties) {
              this.context.warning("Legacy variable name is used to define variable {} in {} - please cleanup your configuration.", variableLine,
                  this.propertiesFilePath);
            }
            String oldValue = this.variables.get(migratedName);
            if (oldValue != null) {
              VariableDefinition<?> variableDefinition = IdeVariables.get(name);
              if (legacyVariable) {
                // if the legacy name was configured we do not want to override the official variable!
                this.context.warning("Both legacy variable {} and official variable {} are configured in {} - ignoring legacy variable declaration!",
                    variableDefinition.getLegacyName(), variableDefinition.getName(), this.propertiesFilePath);
              } else {
                this.context.warning("Duplicate variable definition {} with old value '{}' and new value '{}' in {}", name, oldValue, migratedValue,
                    this.propertiesFilePath);
                this.variables.put(migratedName, migratedValue);
              }
            } else {
              this.variables.put(migratedName, migratedValue);
            }
            if (variableLine.isExport()) {
              this.exportedVariables.add(migratedName);
            }
          }
        }
      } while (line != null);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load properties from " + this.propertiesFilePath, e);
    }
  }

  @Override
  public void save() {

    if (this.modifiedVariables.isEmpty()) {
      this.context.trace("No changes to save in properties file {}", this.propertiesFilePath);
      return;
    }
    Path newPropertiesFilePath = this.propertiesFilePath;
    String propertiesFileName = this.propertiesFilePath.getFileName().toString();
    Path propertiesParentPath = newPropertiesFilePath.getParent();

    if (LEGACY_PROPERTIES.equals(propertiesFileName)) {
      newPropertiesFilePath = propertiesParentPath.resolve(DEFAULT_PROPERTIES);
      this.context.info("Converting legacy properties to {}", newPropertiesFilePath);
    }

    this.context.getFileAccess().mkdirs(propertiesParentPath);
    List<VariableLine> lines = new ArrayList<>();

    // Skip reading if the file does not exist
    if (Files.exists(this.propertiesFilePath)) {
      try (BufferedReader reader = Files.newBufferedReader(this.propertiesFilePath)) {
        String line;
        do {
          line = reader.readLine();
          if (line != null) {
            VariableLine variableLine = VariableLine.of(line, this.context, getSource());
            lines.add(variableLine);
          }
        } while (line != null);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to load existing properties from " + this.propertiesFilePath, e);
      }
    } else {
      this.context.debug("Properties file {} does not exist, skipping read.", newPropertiesFilePath);
    }

    try (BufferedWriter writer = Files.newBufferedWriter(newPropertiesFilePath, StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING)) {
      // copy and modify original lines from properties file
      for (VariableLine line : lines) {
        VariableLine newLine = migrateLine(line, true);
        if (newLine == null) {
          this.context.debug("Removed variable line '{}' from {}", line, newPropertiesFilePath);
        } else {
          if (newLine != line) {
            this.context.debug("Changed variable line from '{}' to '{}' in {}", line, newLine, newPropertiesFilePath);
          }
          writer.append(newLine.toString());
          writer.append(NEWLINE);
          String name = line.getName();
          if (name != null) {
            this.modifiedVariables.remove(name);
          }
        }
      }
      // append variables that have been newly added
      for (String name : this.modifiedVariables) {
        String value = this.variables.get(name);
        if (value == null) {
          this.context.trace("Internal error: removed variable {} was not found in {}", name, this.propertiesFilePath);
        } else {
          boolean export = this.exportedVariables.contains(name);
          VariableLine line = VariableLine.of(export, name, value);
          writer.append(line.toString());
          writer.append(NEWLINE);
        }
      }
      this.modifiedVariables.clear();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to save properties to " + newPropertiesFilePath, e);
    }
    this.propertiesFilePath = newPropertiesFilePath;
  }

  private VariableLine migrateLine(VariableLine line, boolean saveNotLoad) {

    String name = line.getName();
    if (name != null) {
      VariableDefinition<?> variableDefinition = IdeVariables.get(name);
      if (variableDefinition != null) {
        line = variableDefinition.migrateLine(line);
      }
      if (saveNotLoad) {
        name = line.getName();
        if (this.modifiedVariables.contains(name)) {
          String value = this.variables.get(name);
          if (value == null) {
            return null;
          } else {
            line = line.withValue(value);
          }
        }
        boolean newExport = this.exportedVariables.contains(name);
        if (line.isExport() != newExport) {
          line = line.withExport(newExport);
        }
      }
    }
    return line;
  }

  @Override
  protected Map<String, String> getVariables() {

    return this.variables;
  }

  @Override
  protected void collectVariables(Map<String, VariableLine> variables, boolean onlyExported, AbstractEnvironmentVariables resolver) {

    for (String key : this.variables.keySet()) {
      variables.computeIfAbsent(key, k -> createVariableLine(key, onlyExported, resolver));
    }
    super.collectVariables(variables, onlyExported, resolver);
  }

  @Override
  protected boolean isExported(String name) {

    if (this.exportedVariables.contains(name)) {
      return true;
    }
    return super.isExported(name);
  }

  @Override
  public EnvironmentVariablesType getType() {

    return this.type;
  }

  @Override
  public Path getPropertiesFilePath() {

    return this.propertiesFilePath;
  }

  @Override
  public Path getLegacyPropertiesFilePath() {

    if (this.propertiesFilePath == null) {
      return null;
    }
    if (this.propertiesFilePath.getFileName().toString().equals(LEGACY_PROPERTIES)) {
      return this.propertiesFilePath;
    }
    Path legacyProperties = this.propertiesFilePath.getParent().resolve(LEGACY_PROPERTIES);
    if (Files.exists(legacyProperties)) {
      return legacyProperties;
    }
    return null;
  }

  @Override
  public String set(String name, String value, boolean export) {

    String oldValue = this.variables.put(name, value);
    boolean flagChanged = export != this.exportedVariables.contains(name);
    if (Objects.equals(value, oldValue) && !flagChanged) {
      this.context.trace("Set variable '{}={}' caused no change in {}", name, value, this.propertiesFilePath);
    } else {
      this.context.debug("Set variable '{}={}' in {}", name, value, this.propertiesFilePath);
      this.modifiedVariables.add(name);
      if (export && (value != null)) {
        this.exportedVariables.add(name);
      } else {
        this.exportedVariables.remove(name);
      }
    }
    return oldValue;
  }

}
