# Migration from Legacy FDK

> **Note:** Migration from any legacy FDK release to the current version requires a full refactor. There is no incremental upgrade path — existing extensions, scripts, and custom filters must be rewritten against the new APIs.

---

## Why a full refactor?

Three architectural decisions drove the scope of the break, and all three were deliberate.

### Policy Studio plugin packaging

A Policy Studio update removed the ability to access the `EntityType` object at page load time. The legacy FDK relied on this mechanism to retrieve Quick Filter UI definitions stored inside the Entity Store — for script-based filters, the script itself was also stored there, requiring no deployment to the gateway beyond the base FDK typeset.

With that mechanism gone, the packaging model changed entirely. Quick Filter UI and NLS files are now deployed as standard Policy Studio plugin JARs, and Java Quick Filters must be copied to the Policy Studio plugins folder alongside the gateway extension directory.

### OSGi bundle compatibility

The new packaging model operates at package granularity, as required by OSGi. This necessitated significant repackaging of the entire framework — class and package organisation had to be restructured to produce valid OSGi bundles.

### Removal of runtime patches

Parts of the legacy FDK relied on patches applied directly to existing gateway filters to add behaviour. These have been removed entirely. Where the patched behaviour was needed, it is now provided by dedicated FDK plugin filters that operate alongside the stock filters without modifying them.

The result is a framework that introduces no conflicts with the existing gateway runtime. Policies behave identically before and after FDK deployment.

---

## What changed

| Concern | Legacy | Current |
|---|---|---|
| Dependency setup | `GatewayMavenizer` installs artefacts permanently into `~/.m2` | `GatewayScanner` generates a versioned `apigateway.gradle` per release branch |
| Extension discovery | Runtime classpath scan at startup | Build-time `META-INF/vordel/extensions` index — zero startup overhead |
| Script context access | Explicit context object retrieved from a message attribute | Top-level runtime functions injected as closures by the Advanced Script Filter |
| Plumbing filters | `ScriptInvocationContextFilter`, `InitializerShortcutFilter`, `ScriptContextCallFilter` | Single Advanced Script Filter |
| JAXRS support | Built into core | Optional plugin |
| Filter typeset generation | Classpath scan triggered at runtime | Bundled into extension JAR at build time |
| Build system | Maven with `GatewayMavenizer` | Gradle with pre-generated `apigateway.gradle` |

---

## Quick Filter-only migration shortcut

If the migration involves only Quick Filters with no Entity schema change — meaning field
types, field names, and the overall structure of the configuration entity are unchanged —
the full refactor scope does not apply. The required operation reduces to updating the
`EntityType` class name reference in the Entity Store to match the new definition class name.
No typeset re-import, configuration rebuild, or gateway redeployment is needed beyond the
normal JAR replacement.

---

## Legacy plumbing filters

Three filters existed in the legacy FDK to address specific integration needs. All three are replaced by the Advanced Script Filter in the current version.

**`ScriptInvocationContextFilter`** (labelled *"Bind Resources"* in the UI) — created a context object stored in a message attribute that had to be retrieved explicitly before any FDK feature could be used. In the current version, the runtime interface is injected directly into the script as top-level closures.

**`InitializerShortcutFilter`** — ensured a policy executed only once, for initialisation logic requiring deep gateway lifecycle knowledge to apply correctly. Replaced by the `attach` hook in the Advanced Script Filter.

**`ScriptContextCallFilter`** — invoked extension functions via a selector. Functions could only be declared in Java classes, not in scripts. In the current version, extension functions declared in third-party JARs or Groovy scripts are directly callable from the Extended Selector filter or from other scripts.

---

## Script context migration

The most common migration change is how the script runtime context is accessed:

```groovy
// Legacy — retrieve context object from a message attribute, then invoke through it
def ctx = msg.get("saved.ctx")
ctx.invokeResource(msg, "runtime.sample")

// Current — runtime interface injected as top-level closures; call directly
invokeResource(msg, "runtime.sample")
```

---

## Build system migration

| Step | Legacy (`GatewayMavenizer`) | Current (`GatewayScanner`) |
|---|---|---|
| First-time setup | Run `GatewayMavenizer` against a Gateway installation; artefacts installed permanently into `~/.m2` | Run `GatewayScanner` once per release to generate a versioned `apigateway.gradle`; committed to the branch |
| Partial failure | Corrupts local Maven repository; no clean recovery path | Generates a fresh file; previous state not modified |
| IDE integration | No source or Javadoc attachment | Public dependencies resolved as proper Maven artefacts; IDEs attach sources and Javadoc automatically |
| Per-release update | Repeat full `GatewayMavenizer` run | Checkout the matching branch; `apigateway.gradle` is already present |

If no branch exists for your target release, open a GitHub issue to request generation of a suitable `apigateway.gradle`. See [Build & Install](BuildAndInstall.md) for the standard build procedure.
