---
name: create-plugin
description: Create a TIS plugin conforming to TIS plugin specifications
---

# TIS Plugin Creator

This skill helps you create a well-structured TIS (Data Integration Service) plugin that conforms to all TIS plugin specifications.

## What This Skill Does

When invoked, this skill will:
1. Analyze the user-provided context to understand the plugin's functionality
2. Design the plugin structure with appropriate properties (general or aggregated)
3. Generate the complete plugin implementation including:
   - Main plugin class implementing `Describable`
   - Inner `Descriptor` class with `@TISExtension`
   - Properties with proper `@FormField` annotations
   - Property validation methods
   - Corresponding `.json` property descriptor file
   - Optional `.md` help documentation

## TIS Plugin Specifications

### Core Requirements

Every TIS plugin MUST satisfy:

1. **Implement Describable Interface**
   - Plugin class must implement `com.qlangtech.tis.extension.Describable` (directly or via parent)

2. **Inner Descriptor Class**
   - Must have a `public` inner class extending `com.qlangtech.tis.extension.Descriptor`
   - Annotated with `@TISExtension` from `com.qlangtech.tis.extension.TISExtension`

3. **Property Requirements**
   - All properties must be `public`
   - Must be annotated with `@FormField`

4. **Property Descriptor Files**
   - A `.json` file in the same package under `resources/` directory
   - Optional `.md` file for rich markdown help content

### Property Types

#### General Properties
Basic Java types:
- `Boolean` / `boolean`
- `Integer` / `int`
- `String`
- `Long` / `long`
- `java.util.Date`
- `com.qlangtech.tis.plugin.MemorySize`
- Duration types

#### Aggregated Properties
Properties that are themselves `Describable` plugins, providing polymorphic capability. Example: `ClusterType` in `TISFlinkCDCStreamFactory`.

**IMPORTANT**: Each concrete implementation class of an aggregated property is a full plugin and MUST have:
- Its own Java class with `@FormField` properties
- Its own `@TISExtension` Descriptor
- **Descriptor MUST implement `DescriptorUseableShortComment` interface** (from `tis-plugin/src/main/java/com/qlangtech/tis/extension/DescriptorUseableShortComment.java`)
  - Implement `shortComment()` method returning a **brief Chinese description** of the plugin's function
  - **Requirements for shortComment()**:
    - Use Chinese language (中文)
    - **Maximum 10 characters** (10个字以内)
    - **NO punctuation marks** (no periods, commas, etc.)
    - Will be displayed in the frontend UI to help users understand the plugin at a glance
    - Example: "Hadoop文件系统管理" or "Hive元数据存储"
- Its own `.json` descriptor file in resources (same package path)
- Optional `.md` help file if properties are complex

**Parent Class Requirements for Aggregated Properties**:
- If an aggregated property type (e.g., `IcebergCatalog`) will have multiple concrete implementations, the **parent abstract class MUST define a `BasicDescriptor`**:
  - Must be a **protected abstract static** inner class
  - Must extend `Descriptor<ParentClassName>`
  - All concrete implementation classes' Descriptors MUST extend this `BasicDescriptor`
  - Example structure in parent class:
    ```java
    public abstract class IcebergCatalog implements Describable<IcebergCatalog> {
        // properties...
        
        protected abstract static class BasicDescriptor extends Descriptor<IcebergCatalog> {
            // common descriptor logic for all implementations
        }
    }
    ```
  - Example in implementation class:
    ```java
    public class HadoopCatalog extends IcebergCatalog {
        // properties...
        
        @TISExtension()
        public static class DefaultDescriptor extends BasicDescriptor 
                implements DescriptorUseableShortComment {
            @Override
            public String shortComment() {
                return "基于HDFS文件系统";
            }
            
            @Override
            public String getDisplayName() {
                return "Hadoop Catalog";
            }
        }
    }
    ```

Example: If `DataxIcebergWriter` has an aggregated property `IcebergCatalog catalog`, and `HadoopCatalog` extends `IcebergCatalog`, then you need:
- `IcebergCatalog.java` (abstract parent with protected abstract static BasicDescriptor inner class)
- `HadoopCatalog.java` (with its own @FormField properties, Descriptor extends BasicDescriptor)
- `HadoopCatalog.json` (describing HadoopCatalog's own properties)
- Descriptor implements `DescriptorUseableShortComment` with `shortComment()` returning Chinese text (≤10 chars, no punctuation)
- Optional `HadoopCatalog.md` (if properties are complex)

### @FormField Annotation Attributes

- **identity**: Unique identifier for the plugin instance (acts as primary key)
- **ordinal**: Display order in UI form (lower = higher priority, semantically related fields should be adjacent)
- **advance**: Mark as advanced setting (must have default value, can be hidden)
- **validate**: Frontend validation rules (see Validator options below)
- **type**: Field type (see FormFieldType options below)

### Validator Options

Reference: `tis-plugin/src/main/java/com/qlangtech/tis/plugin/annotation/Validator.java`

- `require`: Required field
- `user_name`: Username format (letters, numbers, underscore, dot, dash)
- `email`: Email format
- `forbid_start_with_number`: Cannot start with number
- `identity`: Primary key format
- `integer`: Integer format
- `host`: Internet domain with optional port (e.g., `192.168.28.200:7070`)
- `hostWithoutPort`: Domain without port
- `url`: URL starting with http/https
- `db_col_name`: Database column name format
- `relative_path`: File system relative path
- `absolute_path`: Unix absolute path
- `none_blank`: Non-empty content

### FormFieldType Options

Reference: `tis-plugin/src/main/java/com/qlangtech/tis/plugin/annotation/FormFieldType.java`

- `MULTI_SELECTABLE`: Multi-select, property type `List<IdentityName>` or `List<String>`
- `INPUTTEXT`: Single-line text input, property type `String`
- `SELECTABLE`: Single-select dropdown (register options via `registerSelectOptions()` in Descriptor)
- `PASSWORD`: Password input
- `FILE`: File upload (only one per form, plugin must implement `ITmpFileStore`)
- `TEXTAREA`: Multi-line text input (for SQL scripts, XML, etc.)
- `DATE`: Date picker
- `JDBCColumn`: JDBC column type
- `INT_NUMBER`: Integer number input
- `ENUM`: Enumeration selection
- `DateTime`: Date-time (UTC, property type `long` or `java.util.Date`)
- `DECIMAL_NUMBER`: Decimal number
- `DURATION_OF_SECOND`: Duration in seconds
- `DURATION_OF_MINUTE`: Duration in minutes
- `DURATION_OF_HOUR`: Duration in hours
- `MEMORY_SIZE_OF_BYTE`: Memory size in bytes
- `MEMORY_SIZE_OF_KIBI`: Memory size in KB
- `MEMORY_SIZE_OF_MEGA`: Memory size in MB

### Validation Logic

#### Single Property Validation

Add a method in Descriptor with pattern `validate{PropertyName}`:

```java
public boolean validateAge(IFieldErrorHandler msgHandler, Context context, String fieldName, String value) {
    int age = Integer.parseInt(value);
    if (age < MIN_AGE) {
        msgHandler.addFieldError(context, fieldName, "Cannot be less than: " + MIN_AGE);
        return false;
    }
    if (age > MAX_AGE) {
        msgHandler.addFieldError(context, fieldName, "Cannot be greater than: " + MAX_AGE);
        return false;
    }
    return true;
}
```

#### Multi-Property Joint Validation

Override `validateAll` in Descriptor:

```java
@Override
protected boolean validateAll(IControlMsgHandler msgHandler, Context context, PostFormVals postFormVals) {
    // Perform joint validation
    if (!validateUserCredentials(postFormVals.newInstance())) {
        msgHandler.addErrorMessage(context, "Validation failed");
        return false;
    }
    return true;
}
```

## Reference Implementations

Excellent examples in the codebase:
1. `tis-plugin/src/main/java/com/qlangtech/tis/config/authtoken/impl/DefaultHiveUserToken.java`
2. `tis-plugin/src/main/java/com/qlangtech/tis/manage/common/UserProfile.java`
3. `tis-plugin/src/main/java/com/qlangtech/tis/plugin/alert/impl/LoginPlugin.java` (multi-property validation)

## Instructions for Claude

When the user invokes `/create-plugin`, follow this workflow:

### Step 1: Analyze Context
- Ask the user to provide context about the plugin they want to create (or the user may provide it directly)
- Analyze the context to determine:
  - Plugin's purpose and functionality
  - What properties are needed (general vs aggregated)
  - Parent class to extend (if any)
  - Validation rules required

### Step 2: Design Plugin Structure
- Determine property organization:
  - Which properties should be general types?
  - Which properties should be aggregated (polymorphic)?
  - **For each aggregated property**:
    - Will it have multiple concrete implementations?
    - If yes, the parent abstract class MUST define a `BasicDescriptor` inner class
    - List all planned implementation classes
  - Group semantically related properties via ordinal ordering
- Plan validation logic:
  - Which properties need business logic validation?
  - Are there any multi-property joint validations?

### Step 3: Confirm Design with User
Before implementation, present:
- Plugin class name and package
- List of properties with types, FormFieldType, and validators
- **Any aggregated properties and their subtypes** (each subtype will be a separate plugin with its own files)
- For each aggregated property subtype, list:
  - Subtype class name and parent class
  - Its own properties with types and validators
  - Files to be generated (Java + JSON + optional MD)
- Validation methods to be implemented
- Wait for user approval

### Step 4: Implement Plugin
Generate these files in the correct structure:

1. **Plugin Java Class**
   - Package declaration
   - Imports
   - Plugin class implementing `Describable<PluginClassName>`
   - Public properties with `@FormField` annotations
   - Business logic methods
   - Inner `Descriptor` class with:
     - `@TISExtension` annotation
     - `getDisplayName()` override
     - `validate*` methods for property validation
     - `validateAll()` if needed for joint validation
     - `registerSelectOptions()` if using SELECTABLE fields

2. **Property Descriptor JSON** (`PluginClassName.json`)
   - **CRITICAL: Correct JSON Format**
     ```json
     {
       "propertyName1": {
         "label": "Display Label",
         "placeholder": "Input placeholder text",
         "help": "Short one-sentence help text",
         "dftVal": "default value"
       },
       "propertyName2": {
         "label": "Another Property",
         "help": "Brief description"
       }
     }
     ```
   - **WRONG Format** (DO NOT USE):
     ```json
     {
       "formFields": [
         {"key": "propertyName1", "label": "...", "help": "..."}
       ]
     }
     ```
   - In `src/main/resources/{package_path}/`
   - Direct object-to-object mapping (propertyName → property descriptor)
   - Each property descriptor contains:
     - `label`: Display label (required)
     - `placeholder`: Input placeholder (optional, applicable for text inputs)
     - `help`: Brief one-sentence help text (optional, for simple properties)
     - `dftVal`: Default value (optional, for optional fields)

3. **Optional Markdown Help** (`PluginClassName.md`)
   - **Create ONLY for properties that need detailed multi-line explanation**
   - **Help Information Distribution Rule**:
     - **Simple properties** (username, password, port, simple flags): Only define `help` in JSON file with one sentence
     - **Complex properties** (catalog types, file formats, configuration objects, properties needing examples): Define detailed help in `.md` file with `## propertyName` headers
     - **DO NOT duplicate**: If a property has detailed help in `.md` file, keep JSON `help` brief or omit it
   - **Decision criteria for .md file**:
     - Property requires multiple sentences to explain
     - Property needs code examples or configuration samples
     - Property has multiple options that need detailed comparison
     - Property involves technical concepts requiring elaboration
   - Use `## propertyName` headers for each complex property that needs detailed documentation

4. **For Aggregated Properties: Parent Abstract Class**
   - **IMPORTANT**: If the plugin has aggregated properties with multiple implementations, first create or verify the parent abstract class:
   
   **Parent Abstract Class** (e.g., `IcebergCatalog.java`)
   - Implements `Describable<ParentClassName>`
   - Has `@FormField` properties that are common to all implementations
   - **MUST define a `BasicDescriptor` inner class**:
     - Declared as `protected abstract static class BasicDescriptor extends Descriptor<ParentClassName>`
     - Contains common descriptor logic for all implementations
     - May implement common validation methods
     - Example:
       ```java
       public abstract class IcebergCatalog implements Describable<IcebergCatalog> {
           @FormField(ordinal = 1, validate = {Validator.require})
           public String catalogName;
           
           @FormField(ordinal = 2)
           public String databaseName;
           
           // abstract methods...
           
           protected abstract static class BasicDescriptor extends Descriptor<IcebergCatalog> {
               // common descriptor logic
               @Override
               protected boolean verify(IFieldErrorHandler msgHandler, Context context, PostFormVals postFormVals) {
                   return super.verify(msgHandler, context, postFormVals);
               }
           }
       }
       ```

5. **For Each Aggregated Property Implementation Class**
   - **IMPORTANT**: If the plugin has aggregated properties (polymorphic plugin properties), EACH concrete implementation class is a separate plugin and needs:
   
   **Example**: Main plugin `DataxIcebergWriter` has property `IcebergCatalog catalog`, and you're creating `HadoopCatalog extends IcebergCatalog`:
   
   a. **Implementation Java Class** (`HadoopCatalog.java`)
      - Extends the abstract parent class (e.g., `IcebergCatalog`)
      - Has its own `@FormField` properties (in addition to inherited ones)
      - Has its own `@TISExtension` Descriptor that:
        - **MUST extend parent's BasicDescriptor**
        - **MUST implement `DescriptorUseableShortComment` interface**
        - **MUST override `shortComment()` method** with these requirements:
          - Return Chinese text (中文) describing the plugin function
          - **Maximum 10 characters** (10个字以内)
          - **NO punctuation marks** (不要标点符号)
          - Be concise and clear
          - Example for HadoopCatalog: `return "基于HDFS文件系统";`
          - Example for HiveMetastoreCatalog: `return "使用Hive元数据";`
        - Override `getDisplayName()` if needed
      - Same structure as main plugin
   
   b. **Implementation JSON Descriptor** (`HadoopCatalog.json`)
      - In `src/main/resources/{package_path}/` (same package as HadoopCatalog.java)
      - Describes **only HadoopCatalog's own properties** (not inherited ones)
      - Same format rules as main plugin JSON
      - Example:
        ```json
        {
          "warehouse": {
            "label": "Warehouse Path",
            "help": "HDFS or local filesystem path"
          },
          "hadoopConfDir": {
            "label": "Hadoop Config Directory",
            "help": "Path to Hadoop configuration directory"
          }
        }
        ```
   
   c. **Optional Implementation MD** (`HadoopCatalog.md`)
      - Create if HadoopCatalog has complex properties
      - Same rules as main plugin MD file
   
   d. **Repeat for ALL implementation classes**
      - If there's also `HiveMetastoreCatalog`, create its Java + JSON + optional MD
      - If there's `GlueCatalog`, create its Java + JSON + optional MD
      - Each implementation is a complete plugin with full file set

### Step 5: Verify Implementation
- Check all requirements are satisfied:
  - Implements `Describable`
  - Has `@TISExtension` inner Descriptor
  - All properties are public with `@FormField`
  - Ordinal values are properly sequenced
  - Validation rules are appropriate
  - **JSON descriptor format is correct** (object-to-object, NOT formFields array)
  - **All properties in JSON file** exist as public fields in Java class
  - **Help information distribution is correct**:
    - Simple properties: only `help` in JSON (one sentence)
    - Complex properties: detailed help in `.md` file, brief or no `help` in JSON
    - No duplicate help content between JSON and .md files
  - **For aggregated properties**: 
    - **Parent abstract class requirements**:
      - Has a `protected abstract static class BasicDescriptor extends Descriptor<ParentClass>`
      - BasicDescriptor is properly defined in the parent class
    - **Each implementation class has its complete file set**:
      - Java class exists with @TISExtension Descriptor
      - **Descriptor extends parent's BasicDescriptor**
      - **Descriptor implements `DescriptorUseableShortComment` interface**
      - **`shortComment()` method implemented correctly**:
        - Returns Chinese text (中文)
        - Length ≤ 10 characters
        - No punctuation marks
        - Clearly describes the plugin's function
      - JSON descriptor exists in correct resources path
      - JSON describes only the implementation's own properties (not inherited ones)
      - Optional MD file if implementation has complex properties
- Suggest any improvements

### Step 6: Provide Usage Instructions
- Explain where the plugin files were created
- How to build and test the plugin
- How to register the plugin in TIS (if special steps needed)

## Important Considerations

1. **JSON Format (CRITICAL)**: The property descriptor JSON file MUST use object-to-object format, NOT array format.
   - ✅ Correct: `{"propertyName": {"label": "...", "help": "..."}}`
   - ❌ Wrong: `{"formFields": [{"key": "propertyName", ...}]}`
   - Wrong format will cause TIS backend to fail loading the plugin

2. **Help Information Distribution**: Avoid duplicating help content between JSON and .md files.
   - **Simple properties** (username, password, port, boolean flags): Use only JSON `help` field with one concise sentence
   - **Complex properties** (catalog types, configuration objects, properties with multiple options): Use .md file with detailed explanation, keep JSON `help` brief or omit it
   - **Rule of thumb**: If explanation fits in one sentence, use JSON only. If it needs examples, bullet points, or multiple paragraphs, use .md file
   - Let the AI judge the complexity: analyze each property's nature and decide the appropriate help placement

3. **Ordinal Sequencing**: Assign ordinal values carefully. Related properties (like username/password) should have consecutive ordinals to appear together in the UI.

4. **Default Values**: Advanced properties (with `advance = true`) MUST have sensible default values.

5. **Validation**: Always implement business logic validation for properties that need it. Don't rely solely on frontend validation.

6. **Parent Classes**: Check if there's an appropriate parent class to extend (like `AlertChannel` for alert plugins) to inherit common functionality.

7. **Package Organization**: Place the plugin in the appropriate package that reflects its functionality (e.g., alert plugins in `*.plugin.alert.impl.*`).

## Example Interaction

```
User: /create-plugin

I want to create an email alert channel plugin for TIS. It should support:
- SMTP host and port
- Authentication (username/password)  
- SSL support
- Sender email address