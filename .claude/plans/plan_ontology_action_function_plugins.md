# Implementation Plan: DefaultOntologyAction and DefaultOntologyFunction Plugins

## Overview
Create two new Ontology plugin types (Actions and Functions) following the established TIS multi-steps plugin pattern, similar to ObjectType, ValueType, Linker, SharedProperty, and Glossary.

## Context Analysis

### 1. Existing Ontology Architecture
Based on exploration of the codebase:

- **Parent class**: `Ontology.java` - abstract base class for all ontology entities
- **Ontology.OntologyEnum**: Defines 5 types currently:
  - ObjectType (对象类型)
  - ValueType (值类型)
  - Linker (关系类型)
  - SharedProperty (共享属性)
  - Glossary (业务术语)
  
- **Need to add**: 
  - Action (操作/动作)
  - Function (函数/计算逻辑)

### 2. Reference Implementation Pattern
From `DefaultOntologyObjectType.java`:
- Extends `OntologyObjectType` (which extends `Ontology`)
- Implements multi-steps support (3 steps)
- Implements `IPluginStore.AfterPluginSaved` for Neo4j sync
- Uses `OntologySyncQueue` for async graph database updates
- Steps:
  1. `ObjectTypeProfile` - Basic metadata
  2. `ObjectTypeProperties` - Properties definition
  3. `ObjectTypePropertiesRelevant` - Relationships and constraints

### 3. Design Document Analysis
From `/opt/misc/palantir-study/use-case-data-quality/arch/04-ontology-architecture.md`:

**Actions (ActionType)**:
- Purpose: Define write operations on objects
- Core components:
  - Parameters: Global input parameter pool
  - Rules: Composable operation rules (Object/Link/Function/Notification/Webhook/Schedule/Scenario)
  - SubmissionCriteria: Conditions for when action can be executed
  - ConflictResolution: Strategy for handling conflicts
- Lifecycle: DRAFT → CONFIGURATION_INCOMPLETE → READY_FOR_TESTING → PUBLISHED

**Functions**:
- Purpose: Define computation/query logic
- Types:
  - Query Functions (read-only)
  - Ontology Edit Functions (write operations)
- Core components:
  - FunctionSignature: Input parameters and return type
  - Language: TypeScript/Python
  - Implementation: Code body
  - TestCases: Validation tests

## Design Decisions

### 1. Base Class Structure

#### OntologyAction (to be created)
```java
public abstract class OntologyAction extends Ontology 
        implements IdentityName, MultiStepsSupportHost, IPluginStore.ManipuldateProcessor {
    
    public static final String KEY_ACTION_TYPE = "action-type";
    
    protected OneStepOfMultiSteps[] stepsPlugin;
    
    // Identity field (required by TIS framework)
    @FormField(identity = true, ordinal = 0, validate = {Validator.require, Validator.identity})
    public String useless;
    
    @Override
    public String identityValue() {
        // Return action name from first step
    }
    
    @Override
    public void setSteps(OneStepOfMultiSteps[] stepsPlugin) {
        // Validate 3 steps expected
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
    
    // Abstract methods for implementation
    public abstract String getActionName();
    public abstract List<Parameter> getParameters();
    public abstract List<Rule> getRules();
    public abstract SubmissionCriteria getSubmissionCriteria();
}
```

#### OntologyFunction (to be created)
```java
public abstract class OntologyFunction extends Ontology 
        implements IdentityName, MultiStepsSupportHost, IPluginStore.ManipuldateProcessor {
    
    public static final String KEY_FUNCTION = "function";
    
    protected OneStepOfMultiSteps[] stepsPlugin;
    
    @FormField(identity = true, ordinal = 0, validate = {Validator.require, Validator.identity})
    public String useless;
    
    @Override
    public String identityValue() {
        // Return function name from first step
    }
    
    @Override
    public void setSteps(OneStepOfMultiSteps[] stepsPlugin) {
        // Validate 2 steps expected
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
    
    // Abstract methods
    public abstract String getFunctionName();
    public abstract FunctionSignature getSignature();
    public abstract String getImplementation();
}
```

### 2. Multi-Steps Design

#### DefaultOntologyAction (3 steps)
**Step 1: ActionMetadata**
- name: String (action identifier)
- displayName: String (display name)
- description: String (action description)
- targetObjectType: String (which object type this action operates on)

**Step 2: ActionParameters**
- parameters: List<Parameter> (input parameters definition)
  - Each parameter: name, type, required, defaultValue, validation

**Step 3: ActionRules**
- ruleType: Enum (ObjectRule, LinkRule, FunctionRule, NotificationRule, etc.)
- ruleConfig: JSON (configuration for the selected rule type)
- submissionCriteria: Conditions when action can be executed

#### DefaultOntologyFunction (2 steps)
**Step 1: FunctionMetadata**
- name: String (function identifier)
- displayName: String (display name)
- description: String (function description)
- language: Enum (TypeScript, Python, Java)
- functionType: Enum (Query, OntologyEdit)

**Step 2: FunctionImplementation**
- inputParameters: List<FunctionParameter> (name, type, required)
- returnType: String (return data type)
- implementation: String (code body - large textarea)
- testCases: List<TestCase> (optional test cases)

### 3. Directory Structure

```
/Users/mozhenghua/j2ee_solution/project/plugins/tis-ontology-plugin/src/main/java/com/qlangtech/tis/plugin/ontology/impl/
├── action/
│   ├── DefaultOntologyAction.java (host plugin)
│   ├── ActionMetadata.java (Step 1)
│   ├── ActionParameters.java (Step 2)
│   └── ActionRules.java (Step 3)
└── function/
    ├── DefaultOntologyFunction.java (host plugin)
    ├── FunctionMetadata.java (Step 1)
    └── FunctionImplementation.java (Step 2)
```

### 4. Ontology.OntologyEnum Extension

Need to add to `Ontology.java`:

```java
Action(OntologyAction.KEY_ACTION_TYPE,
        IEndTypeGetter.EndType.OntologyAction,
        new BaiscAssistStoreGetter<OntologyAction>() {
            @Override
            public IPluginStore<OntologyAction> getPluginStore(OntologyPluginMeta pluginMeta) {
                return super.getPluginStore(pluginMeta.setPersistence());
            }
            
            @Override
            public File getAssistRootDir(String ontologyName) {
                return OntologyDomain.getActionDir(ontologyName);
            }
        }),

Function(OntologyFunction.KEY_FUNCTION,
        IEndTypeGetter.EndType.OntologyFunction,
        new BaiscAssistStoreGetter<OntologyFunction>() {
            @Override
            public IPluginStore<OntologyFunction> getPluginStore(OntologyPluginMeta pluginMeta) {
                return super.getPluginStore(pluginMeta.setPersistence());
            }
            
            @Override
            public File getAssistRootDir(String ontologyName) {
                return OntologyDomain.getFunctionDir(ontologyName);
            }
        })
```

### 5. IEndTypeGetter.EndType Extension

Need to add to `IEndTypeGetter` interface:
```java
EndType OntologyAction = new EndType("ontology-action");
EndType OntologyFunction = new EndType("ontology-function");
```

### 6. OntologyDomain Extension

Need to add to `OntologyDomain.java`:
```java
public static File getActionDir(String ontologyName) {
    return new File(getDomainDir(ontologyName), "actions");
}

public static File getFunctionDir(String ontologyName) {
    return new File(getDomainDir(ontologyName), "functions");
}
```

### 7. Neo4j Sync Integration

Similar to ObjectType, need to add sync methods in `OntologyNeo4jSyncService`:
```java
public void syncAction(String domain, OntologyAction action) {
    // Sync action to Neo4j graph
}

public void syncFunction(String domain, OntologyFunction function) {
    // Sync function to Neo4j graph
}
```

## Implementation Steps

### Phase 1: Create Base Classes in tis-plugin module

1. **Create `OntologyAction.java`** in `tis-plugin/src/main/java/com/qlangtech/tis/plugin/ontology/`
   - Extend `Ontology`
   - Implement `IdentityName`, `MultiStepsSupportHost`, `IPluginStore.ManipuldateProcessor`
   - Define abstract methods for action metadata

2. **Create `OntologyFunction.java`** in `tis-plugin/src/main/java/com/qlangtech/tis/plugin/ontology/`
   - Extend `Ontology`
   - Implement `IdentityName`, `MultiStepsSupportHost`, `IPluginStore.ManipuldateProcessor`
   - Define abstract methods for function metadata

3. **Update `Ontology.java`**:
   - Add `Action` and `Function` to `OntologyEnum`
   - Add helper methods: `loadAllActions()`, `loadAllFunctions()`, `loadActionDetail()`, `loadFunctionDetail()`

4. **Update `IEndTypeGetter.java`**:
   - Add `OntologyAction` and `OntologyFunction` EndTypes

5. **Update `OntologyDomain.java`**:
   - Add `getActionDir()` and `getFunctionDir()` methods

### Phase 2: Implement DefaultOntologyAction Plugin

6. **Create host plugin**: `DefaultOntologyAction.java`
   - Extends `OntologyAction`
   - Implements `IPluginStore.AfterPluginSaved` for Neo4j sync
   - Descriptor implements `MultiStepsSupportHostDescriptor`

7. **Create Step 1**: `ActionMetadata.java`
   - Extends `OneStepOfMultiSteps`
   - Properties: name, displayName, description, targetObjectType
   - Descriptor with step navigation

8. **Create Step 2**: `ActionParameters.java`
   - Extends `OneStepOfMultiSteps`
   - Properties: parameters list
   - Descriptor with validation

9. **Create Step 3**: `ActionRules.java`
   - Extends `OneStepOfMultiSteps`
   - Properties: ruleType, ruleConfig, submissionCriteria
   - Descriptor (final step)

10. **Create property descriptors** (`.json` files) for all Action classes

### Phase 3: Implement DefaultOntologyFunction Plugin

11. **Create host plugin**: `DefaultOntologyFunction.java`
    - Extends `OntologyFunction`
    - Implements `IPluginStore.AfterPluginSaved` for Neo4j sync
    - Descriptor implements `MultiStepsSupportHostDescriptor`

12. **Create Step 1**: `FunctionMetadata.java`
    - Extends `OneStepOfMultiSteps`
    - Properties: name, displayName, description, language, functionType
    - Descriptor with step navigation

13. **Create Step 2**: `FunctionImplementation.java`
    - Extends `OneStepOfMultiSteps`
    - Properties: inputParameters, returnType, implementation, testCases
    - Descriptor (final step)

14. **Create property descriptors** (`.json` files) for all Function classes

### Phase 4: Neo4j Sync Integration

15. **Update `OntologyNeo4jSyncService.java`**:
    - Add `syncAction()` method
    - Add `syncFunction()` method
    - Add Cypher queries for creating/updating Action and Function nodes

16. **Update graph statistics**:
    - Add action count and function count to `OntologyGraphStats`

### Phase 5: Testing and Documentation

17. **Create unit tests** for both plugins
18. **Create documentation** (`.md` help files) for complex properties
19. **Test multi-step workflow** in TIS UI
20. **Verify Neo4j sync** works correctly

## File Manifest

### tis-plugin module (base classes)
- `tis-plugin/src/main/java/com/qlangtech/tis/plugin/ontology/OntologyAction.java` (NEW)
- `tis-plugin/src/main/java/com/qlangtech/tis/plugin/ontology/OntologyFunction.java` (NEW)
- `tis-plugin/src/main/java/com/qlangtech/tis/plugin/ontology/Ontology.java` (MODIFY)
- `tis-plugin/src/main/java/com/qlangtech/tis/plugin/ontology/OntologyDomain.java` (MODIFY)
- `tis-plugin/src/main/java/com/qlangtech/tis/plugin/IEndTypeGetter.java` (MODIFY)

### tis-ontology-plugin module (implementations)
**Action Plugin**:
- `impl/action/DefaultOntologyAction.java` (NEW)
- `impl/action/ActionMetadata.java` (NEW)
- `impl/action/ActionParameters.java` (NEW)
- `impl/action/ActionRules.java` (NEW)
- `resources/.../action/DefaultOntologyAction.json` (NEW)
- `resources/.../action/ActionMetadata.json` (NEW)
- `resources/.../action/ActionParameters.json` (NEW)
- `resources/.../action/ActionRules.json` (NEW)

**Function Plugin**:
- `impl/function/DefaultOntologyFunction.java` (NEW)
- `impl/function/FunctionMetadata.java` (NEW)
- `impl/function/FunctionImplementation.java` (NEW)
- `resources/.../function/DefaultOntologyFunction.json` (NEW)
- `resources/.../function/FunctionMetadata.json` (NEW)
- `resources/.../function/FunctionImplementation.json` (NEW)

**Neo4j Sync**:
- `sync/OntologyNeo4jSyncService.java` (MODIFY)

## Technical Considerations

### 1. Parameter and Rule Serialization
Actions have complex nested structures (Parameters + Rules). Consider:
- Use JSON serialization for complex rule configurations
- Store as String property in the plugin, deserialize on demand
- Or create separate plugin types for different rule types

### 2. Function Implementation Storage
Function code can be large (multi-line). Use:
- `FormFieldType.TEXTAREA` with large row count
- Consider syntax highlighting hints in UI
- Validate syntax before saving (if possible)

### 3. Cross-Step Dependencies
- Step 2 (ActionParameters) may need to know targetObjectType from Step 1
- Use `OneStepOfMultiSteps.getPreviousStepInstance()` pattern
- Example: Load object type properties to validate parameter types

### 4. Identity Validation
Both Actions and Functions need unique names within an ontology domain:
- Implement `Validator.identity` on name field
- Check for conflicts in the validator method

### 5. Neo4j Graph Model
Design graph schema:
```cypher
// Action node
(:Action {name, displayName, description, targetObjectType, domain})
-[:HAS_PARAMETER]-> (:Parameter {name, type, required})
-[:HAS_RULE]-> (:Rule {ruleType, config})
-[:OPERATES_ON]-> (:ObjectType)

// Function node
(:Function {name, displayName, description, language, functionType, domain})
-[:HAS_INPUT]-> (:FunctionParameter {name, type, required})
-[:RETURNS]-> (:DataType)
```

## Risks and Mitigations

### Risk 1: Complex Rule Configuration
**Risk**: Rules have many types and configurations, hard to model in plugin form
**Mitigation**: 
- Phase 1: Start with simple rule types (ObjectRule only)
- Phase 2: Add more rule types incrementally
- Use JSON textarea for advanced configurations initially

### Risk 2: Function Code Validation
**Risk**: No syntax validation for function code before saving
**Mitigation**:
- Add warning message that code will be validated on first execution
- Future: Integrate syntax parser for validation

### Risk 3: Large Code/Config Storage
**Risk**: Function implementations or rule configs might be very large
**Mitigation**:
- Use CLOB/TEXT fields in storage
- Consider external file storage for very large functions

## Success Criteria

1. ✅ Both base classes (`OntologyAction`, `OntologyFunction`) created in tis-plugin
2. ✅ `DefaultOntologyAction` plugin with 3-step workflow working
3. ✅ `DefaultOntologyFunction` plugin with 2-step workflow working
4. ✅ Both plugins registered in `Ontology.OntologyEnum`
5. ✅ Neo4j sync working for both types
6. ✅ Can create, edit, delete Actions and Functions via TIS UI
7. ✅ Data persisted correctly to ontology domain directories
8. ✅ Graph statistics include action and function counts

## Open Questions for User

1. **Simplified vs Full Implementation**:
   - Start with simplified Action (fewer rule types)?
   - Or implement full Action model from design doc?

2. **Function Language Support**:
   - Which languages to support initially? (Java, TypeScript, Python, Groovy?)
   - Need to integrate with existing TIS script execution infrastructure?

3. **Rule Types Priority**:
   - Which rule types are most important for MVP?
   - ObjectRule, LinkRule, FunctionRule, NotificationRule?

4. **Neo4j Schema**:
   - Should Actions/Functions be synced to Neo4j immediately?
   - Or defer graph integration to later phase?

5. **UI Complexity**:
   - For Action rules configuration, use JSON textarea or build structured form?
   - For Function implementation, plain textarea or code editor?

## Next Steps

**After plan approval**:
1. Start with Phase 1: Create base classes in tis-plugin module
2. Use `/create-multi-steps-plugin` skill for DefaultOntologyAction
3. Use `/create-multi-steps-plugin` skill for DefaultOntologyFunction
4. Integrate Neo4j sync
5. Test end-to-end workflow

---

**Plan ready for review. Awaiting user feedback before implementation.**
