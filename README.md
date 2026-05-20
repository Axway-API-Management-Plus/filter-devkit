# Filter Development Kit (FDK)

The Filter Development Kit is a Java framework that extends Axway API Gateway with capabilities the platform does not provide out of the box: calling policies from scripts, resolving selectors cleanly, sharing code between scripts, exposing Java methods as selectors, creating fully packaged custom filters with a Policy Studio UI, and hot-reloading code changes without a gateway restart.

A Docker-based playground is provided so you can have a working gateway, an IDE, and all FDK features running in a single container within minutes. See [Getting Started](docs/GettingStarted.md).

---

## What problem does it solve?

| Pain point | FDK solution |
|---|---|
| Scripts cannot call other policies | Advanced Script Filter / Script Context |
| Scripts cannot resolve selectors properly | Resource binding with type coercion |
| Scripts cannot access KPS or caches with proper references | KPS and Cache resources |
| Code is duplicated across scripts (copy/paste) | Groovy resource reflection + export |
| JUEL's built-in reflection resolver can call Java methods at invocation time, but the message is not part of the JUEL context — it is designed for self-contained objects, with no type coercion and no message access | Extension Context pre-registers functions at configuration time; the message is injected directly from the resolver context, and parameter type coercion is handled automatically — no glue filters needed to convert arguments before the call |
| Custom filters require complex GUI maintenance | Java Quick Filters (generated from annotations) |
| Adding a library causes dependency conflicts | Child-first ClassLoader |
| Testing requires a gateway restart | Dynamic Compiler |

---

## Feature overview

| Feature | What it does | Requires typeset | Standalone |
|---|---|:---:|:---:|
| **Script Context** | Adds resource binding to the standard Groovy script filter | No | — |
| **Script Extensions** | Adds Java-backed top-level functions to any script | No | — |
| **Advanced Script Filter** | Enhanced script filter with full resource and reflection API | Yes | — |
| **Extension Context** | Exposes Java methods as API Gateway selectors | **Yes** | — |
| **Extension Interface** | Shares a Java interface between FDK and custom filters | Yes | — |
| **Child-first ClassLoader** | Isolates conflicting third-party libraries | Yes | — |
| **Java Quick Filters** | Generates complete Policy Studio filters from annotated Java | No¹ (own typeset) | Yes |
| **Dynamic Compiler** | Compiles Java on deploy; no restart needed | **Yes** | — |

¹ Quick Filter archives embed their own typesets and are imported independently, but `filter-devkit-runtime` must be installed in the gateway `ext/lib`.

---

## Choosing your installation mode

```
Do you want to create a new filter that appears in the Policy Studio palette?
├─ Yes → Java Quick Filters (docs/QuickFilters.md)
│         Each filter archive is self-contained; no other installation needed.
│
└─ No → Do you want the full feature set (Extension Context global namespace,
        Extension Interface, child-first ClassLoader, Advanced Script Filter)?
        ├─ Yes → Full install: deploy runtime jars + import typeset
        │         → docs/BuildAndInstall.md, then docs/Architecture.md (§ Installation modes)
        │
        └─ No  → Script-only install: deploy runtime jars, no typeset import
                  Gives you: Script Context, Script Extensions
                  → docs/GettingStarted.md (§ Manual install)
```

---

## Documentation map

| Document | Read it when… |
|---|---|
| [Getting Started](docs/GettingStarted.md) | You want a working example as fast as possible |
| [Architecture](docs/Architecture.md) | You want to understand how the pieces fit together |
| [Concepts](docs/Concepts.md) | You want the mental model before diving into features |
| [Scripting Guide](docs/Scripting.md) | You are enhancing scripts (calling policies, resolving selectors, sharing code) |
| [Java Extensions](docs/JavaExtensions.md) | You are exposing Java methods to selectors or custom filters |
| [Java Quick Filters](docs/QuickFilters.md) | You are building a new filter with a Policy Studio UI |
| [Dynamic Compiler](docs/DynamicCompiler.md) | You want faster iteration without gateway restarts |
| [Build & Install](docs/BuildAndInstall.md) | You are setting up the build from source |
| [Migration](docs/Migration.md) | You are migrating from a legacy FDK release |
| [Reference](docs/Reference.md) | You need the full annotation and runtime API |

---

## Prerequisites

- JDK 11
- API Gateway and Policy Studio 7.7.20260228
- Gradle (wrapper included)
- `git`

The codebase has been tested from version 20220530 (JDK 8) through 20260228 (JDK 11). Always use artifacts built for the API Gateway version you are targeting — bytecode differences between releases can trigger `MethodNotFoundException`. If no matching branch exists, open an issue or submit a pull request.

> **Note:** The FDK runtime contains no code specific to either Entity Store variant (one log directive in `AbstractScriptProcessor` aside), so YAML should work at runtime — it has simply not been tested. Typeset generation targets the XML Entity Store tooling; YAML tooling support is not currently planned.

---

## Available plugins

| Plugin | Description | Typeset required |
|---|---|:---:|
| **Extended Eval Selector** | Selector evaluation with native `CircuitAbortException` propagation | Yes (own typeset) |
| **OAuth 2.0 extended Token service** | Token exchange, `assertion` and `client_assertion` grant support | Yes (own typeset) |
| **OAuth 2.0 extended Authorize service** | Built-in PKCE support | Yes (own typeset) |
| **HTTP Signature** | Generation and validation per draft-cavage-http-signatures-12 | Yes (own typeset) |
| **JAXRS support** | JAX-RS web service behaviour in Groovy scripts | No (extension loading) |
| **Circuit Loop** | Controlled iteration within policy execution | Yes (own typeset) |
| **Script Extensions** | Built-in script extension module | No (extension loading) |
| **Certificate Manager** | Certificate manipulation and JWKS export *(work in progress)* | No (extension loading) |

---

## Known limitations and TODO

| Area | Limitation | Status |
|---|---|---|
| **Docker playground** | No gateway configuration is provided out of the box. The user must create an XML configuration and import typesets manually before policies can run. Automation of this step is under investigation. | In progress |
| **Entity Store** | The FDK runtime contains no code specific to either Entity Store variant (one log directive in `AbstractScriptProcessor` aside), so YAML should work at runtime — it has simply not been tested. Typeset generation targets the XML Entity Store tooling; YAML tooling support is not currently planned. | Not planned |
| **Quick Filter UI** | No sub-dialogs in generated filter UIs. Complex multi-panel configurations requiring sub-dialogs (such as the Resources tab) cannot be expressed with the current `xmlui` code generator. | Not planned |
| **Certificate Manager plugin** | Certificate manipulation and JWKS export for OpenID/OAuth services. | In progress |
