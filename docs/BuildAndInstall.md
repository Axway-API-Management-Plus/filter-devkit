# Build and Install

This document covers building the FDK from source and installing it into an existing API Gateway. If you just want to try the FDK quickly, use the [Docker playground](GettingStarted.md) instead.

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 11 | Required by API Gateway 7.7.20260228 |
| API Gateway | 7.7.20260228 | Must be installed locally |
| Policy Studio | 7.7.20260228 | Must be installed locally |
| Gradle | (wrapper included) | Use `./gradlew` on Linux, `gradlew.bat` on Windows |
| `git` | any | For cloning |

On Windows you can either point Gradle at a Linux API Gateway installation extracted from the archive, or use WSL2 for the entire build.

---

## Gradle properties

Set the following in `~/.gradle/gradle.properties` on Linux/macOS, or `%USERPROFILE%\.gradle\gradle.properties` on Windows (create the file if it does not exist).

**Linux / macOS:**
```properties
apigw_vdistdir=/opt/axway/apigateway
studio_vdistdir=/opt/axway/policystudio
```

**Windows:**
```properties
apigw_vdistdir=C:/Axway/apigateway
studio_vdistdir=C:/Axway/policystudio
```

> Use forward slashes on Windows — Gradle accepts them on all platforms and avoids the need to double-escape backslashes.

Set `JAVA_HOME` in your shell or system environment if it does not already point to JDK 11.

---

## Clone and build

```bash
git clone -b develop https://github.com/Axway-API-Management-Plus/filter-devkit.git
cd filter-devkit
```

### Available Gradle tasks

| Task | What it does |
|---|---|
| `clean` | Removes build files and directories (Gradle cache is preserved) |
| `cleanEclipse` | Removes Eclipse IDE configuration files |
| `eclipse` | Generates Eclipse project configuration |
| `build` | Compiles all projects (no deployment) |
| `copyArchives` | Copies all produced JARs to `build/archives/` |
| `deployRuntime` | Installs runtime JARs to `$apigw_vdistdir/ext/lib` |
| `deployPlugin` | Copies filter JARs and the runtime to the Policy Studio `plugins/` directory |

### Full build and install

**Linux / macOS:**
```bash
./gradlew clean cleanEclipse eclipse build copyArchives deployRuntime deployPlugin
```

**Windows:**
```bat
gradlew.bat clean cleanEclipse eclipse build copyArchives deployRuntime deployPlugin
```

After `deployPlugin`, restart Policy Studio with `-clean`:

**Linux / macOS:**
```bash
/opt/axway/policystudio/policystudio -clean
```

**Windows:**
```bat
C:\Axway\policystudio\policystudio.exe -clean
```

---

## Post-install steps

### 1. Stop and start gateway instances

After `deployRuntime` copies JARs to `ext/lib`, perform a **full stop and start** of each gateway instance. A simple *restart* (which reloads configuration without restarting the JVM) is not sufficient — new JARs are not loaded until the JVM restarts.

**Linux:**
```bash
$VDISTDIR/posix/bin/gatewaymanager stop
$VDISTDIR/posix/bin/gatewaymanager start
```

**Windows:**
```bat
%VDISTDIR%\Win32\bin\gatewaymanager.bat stop
%VDISTDIR%\Win32\bin\gatewaymanager.bat start
```

### 2. Activate the base FDK typeset in Policy Studio (optional — enables full feature set)

Open a configuration in Policy Studio, then:  
**File → Import → Import Custom Filter → select `filter-devkit-runtime/src/main/typesets/apigwsdkset.xml`**

> This step is not available with the YAML Entity Store. Without it, the Advanced Script Filter, Extension Interface, and child-first ClassLoader are not available. See [Architecture — Installation modes](Architecture.md#installation-modes) for what works without the typeset.

### 3. Register Quick Filter plugins in Policy Studio

Quick Filter JARs must be registered as OSGi plugins in Policy Studio before their typeset can be imported. There are three methods — choose the one that fits your environment.

#### Method A — P2 reconcile (recommended for first-time install)

1. Copy the Quick Filter JAR to `<PS_INSTALL>/dropins/`.
2. Open `<PS_INSTALL>/configuration/config.ini` and set:
   ```
   eclipse.p2.reconciler=true
   ```
3. Restart Policy Studio. The P2 reconciler scans the `dropins/` directory and registers all new bundles.
4. Reset `eclipse.p2.reconciler=false` after restart to keep future startups fast.

#### Method B — Edit bundles.info directly

1. Copy the Quick Filter JAR to `<PS_INSTALL>/plugins/`.
2. Open `<PS_INSTALL>/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info`.
3. Add a line for the new bundle:
   ```
   <Bundle-SymbolicName>,<Bundle-Version>,plugins/<jar-filename>.jar,4,false
   ```
   Symbolic name and version come from `META-INF/MANIFEST.MF` inside the JAR.
4. Restart Policy Studio.

#### Method C — Runtime dependencies (compatibility mode)

For releases that predate filter bundle support, or when full OSGi registration is not required:

1. In Policy Studio: **Window → Preferences → Runtime Dependencies** (or the equivalent project-level setting).
2. Add the Quick Filter JAR to the runtime dependency list.
3. Restart Policy Studio.

> In this mode the filter is functional but some Policy Studio features that depend on OSGi metadata (category icons, bundle versioning) may not be available. Prefer Method A or B for current releases.

### 4. Import Quick Filter typesets into the Entity Store

After the plugin is registered (step 3), import its typeset:

1. Extract `typeset.xml` from the Quick Filter JAR:

   **Linux / macOS:**
   ```bash
   unzip /path/to/quick-filter.jar "typeset/*" -d /tmp/typeset-extract/
   ```

   **Windows (PowerShell):**
   ```powershell
   Expand-Archive -Path C:\path\to\quick-filter.jar -DestinationPath C:\temp\typeset-extract\
   ```

   *(A `.jar` is a ZIP archive — rename it to `.zip` if your tool does not accept it directly.)*

2. In Policy Studio: **File → Import → Import Custom Filter → select `typeset.xml`**.

### 5. Enable the Java Debug Agent (development only)

To enable remote debugging on a gateway instance, create a `jvm.xml` file in the instance configuration subdirectory and adjust the port as needed:

```xml
<ConfigurationFragment>
    <VMArg name="-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=9777"/>
</ConfigurationFragment>
```

In the Docker playground, port 9777 is already exposed and the agent is pre-configured. Connect via **Debug Remote Java Application** in your IDE pointing to `localhost:9777`.

---

## IDE setup

After running `./gradlew eclipse` (or `gradlew.bat eclipse` on Windows), import the generated projects into Eclipse or open the repository root in Theia. In the Docker playground the workspace is pre-configured at `/home/project/filter-devkit`.

For changes to static JARs, re-run `deployRuntime` and perform a full stop/start of the gateway instance.

For changes to Dynamic Compiler source files (`ext/dynamic/`), trigger a policy deployment from Policy Studio — no gateway restart is needed. See [Dynamic Compiler](DynamicCompiler.md).
