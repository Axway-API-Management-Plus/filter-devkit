# Architecture

This document describes how the Filter DevKit fits inside the API Gateway runtime, how its components relate to each other, and how class loading works.

---

## FDK in the API Gateway ecosystem

The API Gateway processes messages through **policies** — chains of **filters**. Each filter is a Java class whose configuration is stored in the Entity Store and whose UI is defined in Policy Studio. The FDK adds new extension points at every layer of this stack.

```mermaid
graph LR
    subgraph Build
        Annotations[filter-devkit-annotations]
        Tools[filter-devkit-tools annotation processor]
        Annotations -->|processed by| Tools
    end

    subgraph PolicyStudio
        PSPlugin[filter-devkit-studio OSGi bundle]
        TypeSet[Entity Store typeset XML]
    end

    subgraph Gateway
        Core[Policy Engine]
        ES[Entity Store]
        ExtLib[ext/lib JARs]
        Core -->|configured by| ES
    end

    Tools -->|generates| PSPlugin
    Tools -->|generates| ExtLib
    TypeSet -->|imported into| ES
    ExtLib -->|loaded by| Core
```

---

## FDK component map

| Module | Role | Deployed to |
|---|---|---|
| `filter-devkit-annotations` | Compile-time annotations only; no runtime dependency | Build classpath |
| `filter-devkit-tools` | Annotation processor; generates filter boilerplate | Build classpath |
| `filter-devkit-runtime` | Core runtime: extension loader, script filter, resource system | Gateway `ext/lib` |
| `filter-devkit-studio` | Policy Studio OSGi bundle; embeds `filter-devkit-annotations` and `filter-devkit-runtime` | Policy Studio `dropins` or `plugins` |
| `filter-devkit-dynamic` | Deploy-time Java compiler | Gateway `ext/lib` |
| `filter-devkit-plugins-eval` | Extended Eval Selector filter (example Quick Filter) | Gateway `ext/lib` + PS `dropins` |
| `filter-devkit-plugins-*` | Other bundled filters and extensions | Gateway `ext/lib` + PS `dropins` |

> `filter-devkit-runtime` is **not** deployed to Policy Studio. Policy Studio uses `filter-devkit-studio`, which is a dedicated OSGi bundle that embeds both `filter-devkit-annotations` and `filter-devkit-runtime`.

---

## The FDK layer model

```mermaid
graph TB
    L0[Layer 0 - API Gateway base]
    L1[Layer 1 - Script Enhancements]
    L2[Layer 2 - Java Extensions]
    L3[Layer 3 - Quick Filters]

    L0 -->|extended by| L1
    L1 -->|callable from| L2
    L2 -->|usable in| L3
```

| Layer | Features | Typeset required |
|---|---|:---:|
| **0** — API Gateway base | Script Filter, Custom Filter, Selectors | — |
| **1** — Script Enhancements | Advanced Script Filter, Script Context, Script Extensions | Advanced Script Filter only |
| **2** — Java Extensions | Extension Context, Extension Interface, Child-first ClassLoader | **Yes** |
| **3** — Quick Filters | Full PS palette filter with generated UI | No (own typeset); runtime required |

Note: some Quick Filter plugins register additional extensions that are only available when the main FDK typeset is also imported.

---

## Installation modes

```mermaid
graph TD
    Q1{Need a new filter in the PS palette?}
    Q2{Need Extension Context, Extension Interface,
child-first ClassLoader, or Dynamic Compiler?}
    M1[Mode 1 - Full install]
    M2[Mode 2 - Script-only]
    M3[Mode 3 - Quick Filter]
    F1[All features available]
    F2[Script Context and Script Extensions only]
    F3[Quick Filter in palette]

    Q1 -->|Yes| M3
    Q1 -->|No| Q2
    Q2 -->|Yes| M1
    Q2 -->|No| M2
    M1 --> F1
    M2 --> F2
    M3 --> F3
```

---

## Metadata layout

The annotation processor generates index files embedded in extension JARs at build time. At startup the gateway reads those files directly — no classpath walk, no bytecode analysis.

| File | Purpose |
|---|---|
| `META-INF/vordel/extensions` | List of extension class names to instantiate at startup |
| `META-INF/vordel/libraries/{className}` | JARs to include in the extension's isolated child-first ClassLoader |
| `META-INF/vordel/forceLoad/{className}` | Classes that must share the extension's ClassLoader (inner classes, anonymous classes) |
| `META-INF/vordel/scriptextensions/{className}` | Script extension interfaces implemented by the class |

This layout means extension discovery has zero startup overhead proportional to classpath size — the gateway reads a fixed set of small index files rather than scanning all loaded classes.

---

## Extension discovery

FDK extensions are discovered at gateway startup via a service-loader-like mechanism driven by the annotation processor. No manual registration is needed.

```mermaid
sequenceDiagram
    participant AP as Annotation Processor at build time
    participant JAR as Extension JAR
    participant EL as ExtensionLoader at startup
    participant GW as Gateway Runtime

    AP->>JAR: Write META-INF/vordel/extensions
    AP->>JAR: Write META-INF/vordel/libraries
    GW->>EL: attachModule(ctx)
    EL->>JAR: Read META-INF/vordel/extensions
    EL->>EL: Instantiate and register each class
    EL->>GW: Extensions available in global namespace
```

---

## Class loading architecture

By default, all JARs in `ext/lib` share the API Gateway class loader. This causes problems when your library ships a different version of a class already loaded by the gateway. The `@ExtensionLibraries` annotation activates a private child-first class loader for the annotated class.

```mermaid
graph TB
    subgraph Default
        GWL[API Gateway ClassLoader]
        YC[YourExtension]
        LV2[your-lib-v2]
        LV1[gateway-lib-v1]
        GWL --> YC
        GWL --> LV2
        GWL --> LV1
        LV2 -.->|version conflict| LV1
    end

    subgraph Isolated
        GWL2[API Gateway ClassLoader parent]
        CL[Private ClassLoader child-first]
        SI[SharedInterface loaded by parent]
        YC2[YourExtension loaded by child]
        IL[your-lib-v2 isolated in child]
        GWL2 --> SI
        CL --> YC2
        CL --> IL
        CL -->|delegates shared types| GWL2
        YC2 -->|implements| SI
    end
```

The shared interface must not reference any class from the isolated library. The implementation can use the library freely; only the interface boundary must be clean.

---

## Dynamic Compiler

The Dynamic Compiler is an extension module with the lowest load priority (`@Priority(Integer.MAX_VALUE)`), so it is configured last — after all static extensions are already loaded. This means dynamic sources can reference classes from any static JAR already on the class path. It uses the Eclipse Compiler for Java (ECJ) running in its own isolated child-first class loader.

No compiled class is available outside registered extensions — the compiler output is only accessible through the normal extension discovery mechanism described above.

```mermaid
sequenceDiagram
    participant GW as Gateway Deploy
    participant DC as DynamicCompilerModule
    participant ECJ as Eclipse Compiler ECJ in isolated CL
    participant EL as ExtensionLoader

    GW->>DC: attachModule(ctx)
    DC->>DC: Delete previous compiled output
    DC->>ECJ: Compile VDISTDIR/ext/dynamic
    ECJ-->>DC: Classes and generated META-INF
    DC->>EL: scanClasses(ctx, sharedClassLoader)
    Note over EL: Extensions visible to all local instances
    DC->>ECJ: Compile VINSTDIR/ext/dynamic
    ECJ-->>DC: Classes and generated META-INF
    DC->>EL: scanClasses(ctx, instanceClassLoader)
    Note over EL: Extensions visible to this instance only
```

`VDISTDIR` is the shared API Gateway distribution directory. Extensions compiled from it are loaded into a class loader shared by all local gateway instances (Linux processes). `VINSTDIR` is a specific gateway instance directory. Extensions compiled from it are loaded into that instance's class loader only.

Compiled classes are discarded on shutdown and recompiled fresh on every deployment.
