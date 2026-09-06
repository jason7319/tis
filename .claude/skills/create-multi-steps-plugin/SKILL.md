---
name: create-multi-steps-plugin
description: Create a TIS multi-steps plugin with dependent properties configured through multiple steps
---

# TIS Multi-Steps Plugin Creator

This skill helps you create a well-structured TIS multi-steps plugin that conforms to TIS multi-steps plugin specifications. Multi-steps plugins are used when plugin properties have logical dependencies (e.g., selecting a province before showing cities in that province).

## What This Skill Does

When invoked, this skill will:
1. Analyze the user-provided context to understand the plugin's multi-step functionality
2. Design the multi-steps plugin structure with host plugin and step plugins
3. Generate the complete implementation including:
   - **Host Plugin Class**: Implements `MultiStepsSupportHost` and `IPluginStore.ManipuldateProcessor`
   - **Host Descriptor**: Implements `MultiStepsSupportHostDescriptor` with step definitions
   - **Step Plugin Classes**: Each step extends `OneStepOfMultiSteps`
   - **Step Descriptors**: Extend `OneStepOfMultiSteps.BasicDesc` with navigation logic
   - **Property descriptor files** (`.json`) for all plugins
   - **Optional help documentation** (`.md`) for complex properties

## When to Use This Skill

Use this skill when:
1. User explicitly requests to invoke this skill
2. During assisted programming, you discover properties with semantic dependencies (like province → city), **and the user confirms** the need for a multi-steps plugin

## Reference Example

The primary reference example is:
- **Host Plugin**: `/Users/mozhenghua/j2ee_solution/project/plugins/tis-ontology-plugin/src/main/java/com/qlangtech/tis/plugin/ontology/impl/valuetype/DefaultOntologyValueType.java`
- **Parent Class**: `tis-plugin/src/main/java/com/qlangtech/tis/plugin/ontology/OntologyValueType.java`
- **Step 1**: `/Users/mozhenghua/j2ee_solution/project/plugins/tis-ontology-plugin/src/main/java/com/qlangtech/tis/plugin/ontology/impl/valuetype/MetadataOfValueType.java`
- **Step 2**: `/Users/mozhenghua/j2ee_solution/project/plugins/tis-ontology-plugin/src/main/java/com/qlangtech/tis/plugin/ontology/impl/valuetype/ConstraintsOfValueType.java`

## TIS Multi-Steps Plugin Specifications

### Core Requirements for Host Plugin

#### 1. Implement MultiStepsSupportHost Interface

The plugin class (or its parent) MUST implement `com.qlangtech.tis.extension.MultiStepsSupportHost` interface.

**Reference**: `tis-plugin/src/main/java/com/qlangtech/tis/extension/MultiStepsSupportHost.java`

Example (parent class):
```java
public abstract class OntologyValueType extends Ontology 
        implements IdentityName, MultiStepsSupportHost, IPluginStore.ManipuldateProcessor {
    // ...
}
```

#### 2. Define stepsPlugin Property

The plugin class (or its parent) MUST have a `protected OneStepOfMultiSteps[] stepsPlugin` property and implement two methods from `MultiStepsSupportHost`:

```java
protected OneStepOfMultiSteps[] stepsPlugin;

@Override
public void setSteps(OneStepOfMultiSteps[] stepsPlugin) {
    this.stepsPlugin = Objects.requireNonNull(stepsPlugin, "stepsPlugin can not be null");
    final int FIXED_VALUE_TYPE_STEPS_LENGTH = 2; // Number of steps
    if (stepsPlugin.length != FIXED_VALUE_TYPE_STEPS_LENGTH) {
        throw new IllegalStateException("stepsPlugin.length must be equal to " + FIXED_VALUE_TYPE_STEPS_LENGTH);
    }
}

// IMPORTANT: Must add @JSONField(serialize = false) annotation
@JSONField(serialize = false)
@Override
public OneStepOfMultiSteps[] getMultiStepsSavedItems() {
    return stepsPlugin;
}
```

**Important Notes**:
- `setSteps()` should validate the array length matches the expected number of steps
- `getMultiStepsSavedItems()` MUST be annotated with `@JSONField(serialize = false)` to prevent duplicate serialization

#### 3. Implement IPluginStore.ManipuldateProcessor

The plugin class (or its parent) MUST implement `com.qlangtech.tis.plugin.IPluginStore.ManipuldateProcessor` interface to handle persistence logic.

**Reference**: See `tis-plugin/src/main/java/com/qlangtech/tis/plugin/ontology/OntologyValueType.java:L152`

```java
@Override
public void manipuldateProcess(IPluginContext currentCtx) {
    // Custom persistence logic
    // Example: Save to custom storage, validate cross-step data, etc.
}
```

#### 4. Host Descriptor Requirements

The inner `@TISExtension` Descriptor class MUST implement `com.qlangtech.tis.extension.MultiStepsSupportHostDescriptor` interface.

**Reference**: `tis-plugin/src/main/java/com/qlangtech/tis/extension/MultiStepsSupportHostDescriptor.java`

Must implement two methods:

```java
@TISExtension
public static class DefaultDesc extends Ontology.BasicDesc 
        implements MultiStepsSupportHostDescriptor<OntologyValueType> {
    
    @Override
    public Class<OntologyValueType> getHostClass() {
        return OntologyValueType.class;
    }
    
    @Override
    public List<OneStepOfMultiSteps.BasicDesc> getStepDescriptionList() {
        // Return step descriptors IN ORDER
        return List.of(new MetadataOfValueType.Desc(), new ConstraintsOfValueType.Desc());
    }
}
```

**Important Notes**:
- `getStepDescriptionList()` returns step descriptor instances **in execution order**
- The order in the list defines the step sequence (first element = Step 1, second = Step 2, etc.)

### Core Requirements for Step Plugins

#### 1. Extend OneStepOfMultiSteps

Each step plugin MUST extend `com.qlangtech.tis.extension.OneStepOfMultiSteps`.

**Reference**: `tis-plugin/src/main/java/com/qlangtech/tis/extension/OneStepOfMultiSteps.java`

```java
public class MetadataOfValueType extends OneStepOfMultiSteps 
        implements OntologyValueType.IMetadataOfValueType {
    
    @FormField(ordinal = 0, validate = {Validator.require})
    public String name;
    
    // Other properties...
}
```

#### 2. Optional: Override processPreSaved()

Step plugins can override `processPreSaved()` to execute business logic when the user submits the step:

```java
@Override
public void processPreSaved(IPluginContext pluginContext) {
    // Custom validation or processing logic
    super.processPreSaved(pluginContext);
}
```

#### 3. Step Descriptor Requirements

The inner `@TISExtension` Descriptor MUST extend `OneStepOfMultiSteps.BasicDesc` and implement key methods:

```java
@TISExtension
public static class Desc extends OneStepOfMultiSteps.BasicDesc {
    
    @Override
    public String getStepDescription() {
        return "Metadata"; // Step display name
    }
    
    @Override
    public Step getStep() {
        return Step.Step1; // Which step this is (Step1, Step2, etc.)
    }
    
    @Override
    public Optional<BasicDesc> nextPluginDesc(OneStepOfMultiSteps current) {
        // Return the next step's descriptor
        return Optional.of(new ConstraintsOfValueType.Desc());
        // For final step: return Optional.empty();
    }
    
    @Override
    public boolean isFinalStep() {
        return false; // true for the last step
    }
}
```

**Important Notes**:
- `getStep()` must return the correct `Step` enum value (Step1, Step2, ..., Step7)
- `nextPluginDesc()` returns the next step's descriptor instance, or `Optional.empty()` for the final step
- `isFinalStep()` returns `true` only for the last step in the sequence

#### 4. Step Plugin Properties

Step plugins follow the same property rules as regular TIS plugins:
- All properties must be `public`
- Annotated with `@FormField`
- Have corresponding `.json` descriptor files
- Can use all standard validators and field types
- **See `/create-plugin` skill for detailed property specifications**

### Additional Specifications

#### 5. Override getDescriptor() with @JSONField

If the host plugin class overrides the parent's `getDescriptor()` method, it MUST add `@JSONField(serialize = false)` annotation:

**Reference**: See `tis-plugin/src/main/java/com/qlangtech/tis/plugin/ontology/OntologyObjectType.java`

```java
@JSONField(serialize = false)
@Override
public Descriptor<Ontology> getDescriptor() {
    return super.getDescriptor();
}
```

#### 6. Identity Support (Optional)

If the multi-steps plugin needs to be "identifiable" (parent class doesn't already implement `IdentityName`):

**Ask the user** whether the plugin should be identifiable. If yes:

1. Implement `com.qlangtech.tis.plugin.IdentityName` interface
2. Implement `identityValue()` method
3. Add a dummy identity field (TIS requirement):

```java
public abstract class OntologyValueType extends Ontology 
        implements IdentityName, MultiStepsSupportHost {
    
    /**
     * Caution: This field is currently unused but required since we implement IdentityName
     */
    @FormField(identity = true, ordinal = 0, validate = {Validator.require, Validator.identity})
    public String useless;
    
    @Override
    public String identityValue() {
        // Usually return identity from first step
        for (OneStepOfMultiSteps step : getMultiStepsSavedItems()) {
            if (step instanceof IMetadataOfValueType meta) {
                return meta.getName();
            }
        }
        throw new IllegalStateException("illegal name have not been set");
    }
}
```

## Instructions for Claude

When the user invokes `/create-multi-steps-plugin`, follow this workflow:

### Step 1: Analyze Context

- Ask the user to provide context about the multi-steps plugin they want to create (or the user may provide it directly)
- Analyze the context to determine:
  - Plugin's purpose and multi-step workflow
  - Number of steps required (2-7 steps)
  - Properties for each step
  - **Dependency relationships** between steps (how earlier steps influence later steps)
  - Parent class to extend (if any)
  - Whether the plugin needs to be identifiable

### Step 2: Design Multi-Steps Plugin Structure

Determine the plugin organization:

**Host Plugin**:
- Class name and package
- Parent class (if extending an existing plugin class)
- Whether it implements `IdentityName` (ask user if parent doesn't)
- Number of steps
- Any host-level methods or logic

**For Each Step** (Step 1, Step 2, ..., Step N):
- Step class name and package
- Step description (display name)
- Properties with types, FormFieldType, and validators
- Business logic in `processPreSaved()` if needed
- **How this step's properties depend on previous steps**
- Whether this is the final step

**Property Descriptor Files**:
- Which properties need `.json` descriptors
- Which properties need `.md` help files

### Step 3: Confirm Design with User

Before implementation, present:

1. **Host Plugin**:
   - Class name, parent class, interfaces
   - Number of steps
   - Identity support (yes/no)
   - Persistence logic requirements

2. **Step Details** (for each step):
   - Step number and class name
   - Step description
   - Properties list with types and validators
   - Dependency on previous steps
   - Business logic requirements

3. **Files to Generate**:
   - Host plugin: Java + JSON + optional MD
   - Step 1 plugin: Java + JSON + optional MD
   - Step 2 plugin: Java + JSON + optional MD
   - ... (for each step)

Wait for user approval before proceeding.

### Step 4: Implement Multi-Steps Plugin

Generate files in the correct structure:

#### A. Host Plugin Java Class

**File structure**:
```
{package_path}/{HostPluginName}.java
```

**Must include**:
1. Package and imports
2. Class declaration implementing required interfaces:
   - Extends parent class (if any)
   - Implements `MultiStepsSupportHost` (or parent does)
   - Implements `IPluginStore.ManipuldateProcessor` (or parent does)
   - Optionally implements `IdentityName`
3. Properties:
   - `protected OneStepOfMultiSteps[] stepsPlugin;` (if not in parent)
   - Identity field if implementing `IdentityName`
4. Methods:
   - `setSteps()` with validation
   - `getMultiStepsSavedItems()` with `@JSONField(serialize = false)`
   - `manipuldateProcess()` for persistence logic
   - `identityValue()` if implementing `IdentityName`
   - Getter methods for accessing specific steps (optional but recommended)
5. Inner Descriptor class:
   - Annotated with `@TISExtension`
   - Implements `MultiStepsSupportHostDescriptor<HostPluginClass>`
   - Implements `getHostClass()` returning the host class
   - Implements `getStepDescriptionList()` returning step descriptors in order
   - Overrides `getDisplayName()`

**Example structure**:
```java
package com.qlangtech.tis.plugin.example;

import com.qlangtech.tis.extension.*;
import com.qlangtech.tis.plugin.IPluginStore;
// ... other imports

public class ExampleMultiStepsPlugin extends ParentClass 
        implements MultiStepsSupportHost, IPluginStore.ManipuldateProcessor {
    
    protected OneStepOfMultiSteps[] stepsPlugin;
    
    @Override
    public void setSteps(OneStepOfMultiSteps[] stepsPlugin) {
        this.stepsPlugin = Objects.requireNonNull(stepsPlugin);
        if (stepsPlugin.length != 2) {
            throw new IllegalStateException("Expected 2 steps");
        }
    }
    
    @JSONField(serialize = false)
    @Override
    public OneStepOfMultiSteps[] getMultiStepsSavedItems() {
        return stepsPlugin;
    }
    
    @Override
    public void manipuldateProcess(IPluginContext currentCtx) {
        // Custom persistence logic
    }
    
    // Optional: Typed getters for steps
    public Step1Plugin getStep1() {
        return (Step1Plugin) stepsPlugin[Step.Step1.getStepIndex()];
    }
    
    @TISExtension
    public static class DefaultDesc extends ParentDesc 
            implements MultiStepsSupportHostDescriptor<ExampleMultiStepsPlugin> {
        
        @Override
        public Class<ExampleMultiStepsPlugin> getHostClass() {
            return ExampleMultiStepsPlugin.class;
        }
        
        @Override
        public List<OneStepOfMultiSteps.BasicDesc> getStepDescriptionList() {
            return List.of(new Step1Plugin.Desc(), new Step2Plugin.Desc());
        }
        
        @Override
        public String getDisplayName() {
            return "Example Multi-Steps Plugin";
        }
    }
}
```

#### B. Host Plugin Property Descriptors

**File**: `src/main/resources/{package_path}/{HostPluginName}.json`

Follow the same JSON format rules as regular TIS plugins (see `/create-plugin` skill).

**Only include properties directly defined in the host plugin** (not properties from step plugins).

If the host plugin only has the `stepsPlugin` array and identity field, the JSON might be minimal or focus on those fields.

#### C. Step Plugin Java Classes (for each step)

**File structure**:
```
{package_path}/{StepPluginName}.java
```

**Must include**:
1. Package and imports
2. Class declaration:
   - Extends `OneStepOfMultiSteps`
   - Optionally implements interfaces for type safety
3. Public properties with `@FormField` annotations
4. Business methods (if needed)
5. Optional: Override `processPreSaved()` for step-specific logic
6. Inner Descriptor class:
   - Annotated with `@TISExtension`
   - Extends `OneStepOfMultiSteps.BasicDesc`
   - Implements required methods:
     - `getStepDescription()` - step display name
     - `getStep()` - returns Step enum (Step1, Step2, etc.)
     - `nextPluginDesc()` - returns next step or Optional.empty()
     - `isFinalStep()` - true for last step
   - Validation methods for properties

**Example structure**:
```java
package com.qlangtech.tis.plugin.example;

import com.qlangtech.tis.extension.*;
import com.qlangtech.tis.plugin.annotation.*;
// ... other imports

public class Step1Plugin extends OneStepOfMultiSteps {
    
    @FormField(ordinal = 0, validate = {Validator.require})
    public String name;
    
    @FormField(ordinal = 1, type = FormFieldType.ENUM, validate = {Validator.require})
    public int type;
    
    @Override
    public void processPreSaved(IPluginContext pluginContext) {
        // Save this step's data to context for next step
        pluginContext.getContext().put(Step1Plugin.class.getName(), this);
        super.processPreSaved(pluginContext);
    }
    
    @TISExtension
    public static class Desc extends OneStepOfMultiSteps.BasicDesc {
        
        @Override
        public String getStepDescription() {
            return "Basic Settings";
        }
        
        @Override
        public Step getStep() {
            return Step.Step1;
        }
        
        @Override
        public Optional<BasicDesc> nextPluginDesc(OneStepOfMultiSteps current) {
            return Optional.of(new Step2Plugin.Desc());
        }
        
        @Override
        public boolean isFinalStep() {
            return false;
        }
        
        // Validation methods as needed
        public boolean validateName(IFieldErrorHandler msgHandler, Context context, 
                                   String fieldName, String value) {
            // Validation logic
            return true;
        }
    }
}
```

**For Step 2 and later steps**:
- Can access previous step's data using `OneStepOfMultiSteps.getPreviousStepInstance()`:

```java
public class Step2Plugin extends OneStepOfMultiSteps {
    
    @FormField(ordinal = 0, type = FormFieldType.ENUM, validate = {Validator.require})
    public int option;
    
    // Method to provide dynamic options based on Step 1
    public static List<Option> availableOptions() {
        // Get Step 1 instance from context
        Step1Plugin step1 = OneStepOfMultiSteps.getPreviousStepInstance(Step1Plugin.class);
        
        // Return options based on step1.type
        return getOptionsForType(step1.type);
    }
    
    @TISExtension
    public static class Desc extends OneStepOfMultiSteps.BasicDesc {
        
        @Override
        public String getStepDescription() {
            return "Advanced Settings";
        }
        
        @Override
        public Step getStep() {
            return Step.Step2;
        }
        
        @Override
        public Optional<BasicDesc> nextPluginDesc(OneStepOfMultiSteps current) {
            return Optional.empty(); // This is the final step
        }
        
        @Override
        public boolean isFinalStep() {
            return true;
        }
    }
}
```

#### D. Step Plugin Property Descriptors

**For each step plugin**: `src/main/resources/{package_path}/{StepPluginName}.json`

Follow the same JSON format rules as regular TIS plugins.

**Example**:
```json
{
  "name": {
    "label": "Name",
    "help": "Unique identifier for this configuration"
  },
  "type": {
    "label": "Type",
    "help": "Select the configuration type"
  }
}
```

#### E. Optional Markdown Help Files

**For host or step plugins with complex properties**: `{PluginName}.md`

Follow the same rules as regular TIS plugins (see `/create-plugin` skill).

Use `## propertyName` headers for detailed property documentation.

### Step 5: Verify Implementation

Check all requirements are satisfied:

**Host Plugin Checklist**:
- [ ] Implements `MultiStepsSupportHost` (directly or via parent)
- [ ] Has `protected OneStepOfMultiSteps[] stepsPlugin` property
- [ ] Implements `setSteps()` with validation
- [ ] Implements `getMultiStepsSavedItems()` with `@JSONField(serialize = false)`
- [ ] Implements `IPluginStore.ManipuldateProcessor`
- [ ] Descriptor implements `MultiStepsSupportHostDescriptor`
- [ ] Descriptor's `getStepDescriptionList()` returns steps in correct order
- [ ] If implements `IdentityName`, has identity field and `identityValue()` method
- [ ] If overrides `getDescriptor()`, has `@JSONField(serialize = false)`
- [ ] JSON descriptor file exists with correct format
- [ ] Optional MD file if needed

**Step Plugin Checklist** (for each step):
- [ ] Extends `OneStepOfMultiSteps`
- [ ] All properties are `public` with `@FormField`
- [ ] Descriptor extends `OneStepOfMultiSteps.BasicDesc`
- [ ] Descriptor implements all required methods correctly:
  - `getStepDescription()` returns meaningful name
  - `getStep()` returns correct Step enum
  - `nextPluginDesc()` returns correct next step or empty
  - `isFinalStep()` is correct
- [ ] JSON descriptor file exists with correct format
- [ ] Optional MD file if needed
- [ ] If step accesses previous step data, uses `getPreviousStepInstance()` correctly

**Cross-Step Dependencies**:
- [ ] Step sequence is logical and correctly ordered
- [ ] Data passing between steps is properly implemented
- [ ] Dynamic options/validation based on previous steps work correctly

### Step 6: Provide Usage Instructions

Explain to the user:
1. Where all plugin files were created (host + all steps)
2. How the multi-step workflow operates
3. How to build and test the plugin
4. How data flows between steps
5. Any special configuration or registration needed

## Important Considerations

### 1. Step Ordering

The order in `getStepDescriptionList()` is critical:
```java
return List.of(new Step1.Desc(), new Step2.Desc()); // Step1 → Step2
```

This must match:
- The `Step` enum returned by each descriptor's `getStep()` method
- The navigation in `nextPluginDesc()` methods
- The array indices when accessing `stepsPlugin[]`

### 2. Data Passing Between Steps

Steps communicate via `IPluginContext`:

**In Step N's `processPreSaved()`**:
```java
pluginContext.getContext().put(StepNPlugin.class.getName(), this);
```

**In Step N+1**:
```java
StepNPlugin prevStep = OneStepOfMultiSteps.getPreviousStepInstance(StepNPlugin.class);
int selectedType = prevStep.type; // Use previous step's data
```

### 3. Dynamic Options Based on Previous Steps

When a step's dropdown options depend on a previous step:

```java
public static List<Option> availableOptions() {
    Step1Plugin step1 = OneStepOfMultiSteps.getPreviousStepInstance(Step1Plugin.class);
    // Generate options based on step1's values
    return generateOptionsFor(step1.selectedValue);
}
```

### 4. Validation Across Steps

For validation that spans multiple steps, implement in the host plugin's `manipuldateProcess()`:

```java
@Override
public void manipuldateProcess(IPluginContext currentCtx) {
    Step1Plugin step1 = (Step1Plugin) stepsPlugin[0];
    Step2Plugin step2 = (Step2Plugin) stepsPlugin[1];
    
    // Validate cross-step business rules
    if (!isCompatible(step1.type, step2.option)) {
        throw new IllegalStateException("Incompatible configuration");
    }
}
```

### 5. JSON Descriptor Format

Use the same object-to-object format as regular plugins:
- ✅ Correct: `{"propertyName": {"label": "...", "help": "..."}}`
- ❌ Wrong: `{"formFields": [{"key": "propertyName", ...}]}`

### 6. Number of Steps

TIS supports up to 7 steps (Step1 through Step7). Choose the minimum number of steps needed for clear user workflow.

### 7. Final Step Requirements

The last step MUST:
- Return `true` from `isFinalStep()`
- Return `Optional.empty()` from `nextPluginDesc()`

### 8. Identity Field Requirement

If implementing `IdentityName`, you must add a dummy identity field even if unused. This is a TIS framework requirement.

## Example Interaction

```
User: /create-multi-steps-plugin

I want to create a user registration plugin with two steps:
- Step 1: Select province (dropdown)
- Step 2: Select city (dropdown based on selected province)