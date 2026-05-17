# Reference

Complete reference for all FDK annotations, script runtime functions, and the `ScriptContextBuilder` API.

---

## Annotations

### Extension Context annotations

#### @ExtensionContext

Registers the class in the FDK extension namespace.

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExtensionContext {
    String value();  // Namespace name: ${extensions['<value>'].method}
}
```

---

#### @ExtensionInstance

Instantiates the class with its no-arg constructor and optionally registers it under a Java interface.

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExtensionInstance {
    Class<?>[] value() default {};  // Interfaces to register under (Extension Interface)
}
```

Without a `value`, the class is instantiated but not registered under any interface. With a `value`, it is retrievable via `ExtensionLoader.getExtensionInstance(Interface.class)`.

For static-only extensions, `@ExtensionInstance` is not required. Without it, only `static` methods can be exported.

---

#### @ExtensionLibraries

Activates the child-first ClassLoader for this extension module.

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExtensionLibraries {
    String[] value();    // JUEL selector expressions pointing to JARs or directories
    String[] classes();  // Binary class names to load in the child ClassLoader
}
```

---

#### @ExtensionLink

Marks a helper class as belonging to the same child-first ClassLoader as the target class.

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExtensionLink {
    Class<?> value();  // The @ExtensionLibraries-annotated class this belongs to
}
```

---

#### @InvocableMethod

Exports a method callable from an (Extended) Eval Selector. Returns `boolean`. Can throw `CircuitAbortException`.

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InvocableMethod {
    String value() default "";  // Export name. Defaults to the method name.
}
```

**Constraints:** must return `boolean`; mutually exclusive with `@ExtensionFunction`; parameters injected via `@DictionaryAttribute` / `@SelectorExpression` or unannotated `Message`/`Dictionary`.

Selector syntax: `${extensions['name'].exportedName}`

---

#### @SubstitutableMethod

Exports a method callable anywhere a JUEL selector expression is accepted. Returns any type. Exceptions silenced at DEBUG.

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SubstitutableMethod {
    String value() default "";  // Export name. Defaults to the method name.
}
```

**Constraints:** mutually exclusive with `@ExtensionFunction`; same parameter injection rules as `@InvocableMethod`.

Selector syntax: `${extensions['name'].exportedName}`

---

#### @ExtensionFunction

Exports a method as a JUEL (JSR-341) function. The caller supplies arguments as JUEL sub-expressions at the call site. Can throw `CircuitAbortException`.

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExtensionFunction {
    String value() default "";  // Export name. Defaults to the method name.
}
```

**Constraints:** first parameter must be `Message` or `Dictionary` (injected automatically); remaining parameters provided by caller as JUEL expressions; may declare `throws CircuitAbortException`; mutually exclusive with `@InvocableMethod` and `@SubstitutableMethod`.

Selector syntax: `${extensions['name'].exportedName(arg1, arg2, …)}`

---

#### @DictionaryAttribute

Parameter annotation. Retrieves a message attribute by key, coerced to the parameter type.

```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface DictionaryAttribute {
    String value();  // Message attribute key
}
```

---

#### @SelectorExpression

Parameter annotation. Evaluates a JUEL selector expression on the message dictionary, coerced to the parameter type.

```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface SelectorExpression {
    String value();  // Selector expression (without the ${} delimiters)
}
```

---

### Quick Filter annotations

#### @QuickFilterType

Triggers annotation-processor code generation. Placed on the definition class.

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface QuickFilterType {
    int    version()   default 1;
    String name()      default "";   // Entity Store type name
    String category()  default "";   // Palette category
    String icon()      default "";   // Resource name of the palette icon
    String page();                   // Resource name of the declarative UI XML
    String resources();              // Resource name of the NLS properties file
}
```

---

#### @QuickFilterField

Setter for a simple scalar configuration field.

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface QuickFilterField {
    String name();
    String cardinality();   // "1" or "?"
    String type();          // "string", "integer", "boolean", …
    String defaults() default "";
}
```

Setter signature: `private void setXxx(ConfigContext ctx, Entity entity, String fieldName)`

---

#### @QuickFilterComponent

Setter for a field backed by a collection of entity references (ESPKs).

Setter signature: `private void setXxx(ConfigContext ctx, Entity entity, Collection<ESPK> espks)`

---

#### @QuickFilterRequired / @QuickFilterGenerated / @QuickFilterConsumed

Class-level annotations. Declare message attributes this filter requires, generates, or consumes — static declarations only, known at compile time.

---

### Script Extension annotation

#### @ScriptExtension

Registers a class as a Script Extension.

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ScriptExtension {
    Class<?>[] value();  // Interfaces to register under
}
```

---

## Script runtime functions

Available as top-level functions in the **Advanced Script Filter**. All phases means attach, invoke, and detach unless otherwise noted.

### Resource retrieval

| Function | Returns | Notes |
|---|---|---|
| `getContextResource(String name)` | `Object` | All phases |
| `getInvocableResource(String name)` | `InvocableResource` or `null` | All phases |
| `getFunctionResource(String name)` | `FunctionResource` or `null` | All phases |
| `getSubstitutableResource(String name)` | `SubstitutableResource` or `null` | All phases |
| `getKPSResource(String name)` | `KPSResource` or `null` | Top-level script only |
| `getCacheResource(String name)` | `CacheResource` or `null` | Top-level script only |

### Resource invocation

| Function | Returns | Notes |
|---|---|---|
| `invokeResource(Message msg, String name)` | `Boolean` or `null` | Invoke phase |
| `invokeFunction(Dictionary dict, String name, Object... args)` | return value or `null` | Invoke phase, Groovy only |
| `substituteResource(Dictionary dict, String name)` | result or `null` | Invoke phase; exceptions silenced |

### Context export

| Function | Returns | Notes |
|---|---|---|
| `getExportedResources()` | `ContextResourceProvider` | Full resource set for export to message; does not expose `getKPSResource` or `getCacheResource` |

### Diagnostics

| Function | Returns |
|---|---|
| `getFilterName()` | `String` — the filter's configured display name |

### Configuration (attach phase only)

| Function | Description |
|---|---|
| `setUnwrapCircuitAbortException(boolean)` | Allows the script to throw and propagate `CircuitAbortException`. Also applies to Script Extension calls. |
| `setExtendedInvoke(boolean)` | Passes `Circuit` as the first argument to `invoke(circuit, msg)`. |
| `attachExtension(String fqn)` | Binds a Script Extension by fully qualified interface name. |

### Groovy-only (attach phase)

| Function | Description |
|---|---|
| `reflectResources(Object script)` | Reflects `this` for `@InvocableMethod`, `@SubstitutableMethod`, `@ExtensionFunction` and adds them to the resource set. |
| `reflectEntryPoints(Object script)` | Reflects `this` for strongly-typed `invoke()` and `detach()` signatures with injected parameters. |

---

## ScriptContextBuilder API

Used to set up FDK features in the standard Groovy Script Filter (no typeset required). Called once at the top level of the script before `invoke`.

```groovy
import com.vordel.circuit.filter.devkit.script.context.ScriptContextBuilder

ScriptContextBuilder.bindGroovyScriptContext(this, { builder ->
    // builder methods listed below
})
```

### Policy binding

| Method | Description |
|---|---|
| `attachPolicyByPortableESPK(String name, String espk)` | Binds a policy resource by portable ESPK XML string. |
| `attachPolicyByShorthandKey(String name, String key)` | Binds a policy resource by shorthand key path (e.g. `/[CircuitContainer]name=X/[FilterCircuit]name=Y`). |

### Selector binding

| Method | Description |
|---|---|
| `attachSelectorResourceByExpression(String name, String expression, Class<?> type)` | Binds a selector expression resource with explicit type coercion. |

### KPS and Cache binding

| Method | Description |
|---|---|
| `attachKPSResourceByAlias(String name, String alias)` | Binds a KPS table resource by its alias. |
| `attachCacheResourceByName(String name, String cacheName)` | Binds a cache resource by its configured name. |

### Reflection

| Method | Description |
|---|---|
| `reflectClass(Class<?> clazz)` | Reflects static `@InvocableMethod`, `@SubstitutableMethod`, and `@ExtensionFunction` methods from an existing class and adds them to the script's resource set. |

### Extension binding

| Method | Description |
|---|---|
| `attachExtension(String fqn)` | Binds a Script Extension by fully qualified interface name. Without the typeset, a new extension instance is created for this script. With the typeset, the shared singleton is used. |

---

## Selector syntax quick reference

| Syntax | Feature | Description |
|---|---|---|
| `${extensions['name'].method}` | `@InvocableMethod` or `@SubstitutableMethod` | Call an exported method (no caller arguments) |
| `${extensions['name'].method(a, b)}` | `@ExtensionFunction` | Call a JUEL function (arguments are sub-expressions) |
| `${attr.sub}` | Selector | Equivalent to `${attr["sub"]}` — JUEL dot is map access |
| `${exported.method}` | Script resource export | Call a method exported from a script into the message |
