# Getting Started

The fastest path to a working FDK environment is the Docker playground. It gives you a pre-configured API Gateway, Policy Studio (via VNC), and a Theia IDE in a single container.

---

## Path A — Docker playground (recommended)

### 1. Clone the repository

```bash
git clone -b develop https://github.com/Axway-API-Management-Plus/filter-devkit.git
cd filter-devkit
```

### 2. Copy your Axway files into `dist/`

Place the API Gateway installer and your license file in the `dist/` directory:

```
dist/
  APIGateway_7.7.20260228_Install_linux-x86-64_BN03.run
  licence.lic
```

### 3. Build the Docker image

**Linux:**

```bash
docker build \
  --build-arg APIM_RUN_FILE=dist/APIGateway_7.7.20260228_Install_linux-x86-64_BN03.run \
  --build-arg APIM_LIC_FILE=dist/licence.lic \
  -t filter-devkit-docker \
  -f src/main/docker/Dockerfile .
```

**Windows:**

```bat
docker build ^
  --build-arg APIM_RUN_FILE=dist/APIGateway_7.7.20260228_Install_linux-x86-64_BN03.run ^
  --build-arg APIM_LIC_FILE=dist/licence.lic ^
  -t filter-devkit-docker ^
  -f src\main\docker\Dockerfile .
```

### 4. Run the container

```bash
docker run \
  -p 6080:6080 -p 3000:3000 \
  -p 8090:8090 -p 8080:8080 \
  -p 8065:8065 -p 8075:8075 \
  -p 9777:9777 \
  --mount type=tmpfs,destination=/tmp \
  filter-devkit-docker
```

### 5. Open the services

| URL | Service |
|---|---|
| http://localhost:3000 | Theia Java IDE |
| http://localhost:6080 | VNC — Policy Studio |
| https://localhost:8090 | Admin Node Manager |

### 6. Initialize the IDE

In the Theia terminal:

```bash
cd /home/project/filter-devkit
./gradlew clean cleanEclipse eclipse build
```

Reload Theia after the first build completes.

### What the Docker image provides

The Docker image pre-installs all FDK JARs in the gateway (`ext/lib`) and registers `filter-devkit-studio` in Policy Studio via the `dropins` directory. However, **no gateway configuration is provided**. Before you can run policies you must:

1. **Create an XML configuration** — open Policy Studio, connect to the Admin Node Manager at `https://localhost:8090`, and create a new gateway group and instance configuration.
2. **Import the typesets for the plugins you want to use** — for the base FDK: **File → Import → Import Custom Filter → select `apigwsdkset.xml`**. Repeat for any Quick Filter plugins.
3. Deploy the configuration to the gateway.

Once a configuration is deployed, the gateway instance is ready and you can start creating policies.

### Exposed ports

| Port | Service |
|---|---|
| 3000 | Theia IDE |
| 6080 | noVNC / Policy Studio |
| 8080 | Gateway default services |
| 8090 | Admin Node Manager |
| 9777 | Java remote debug |

---

## Path B — Manual install

If you prefer to build from source against an existing API Gateway installation, follow [Build & Install](BuildAndInstall.md) first, then return here.

---

## Your first example: calling a policy from a script

This example uses the **Advanced Script Filter** (requires the FDK typeset to be imported).

1. In Policy Studio, create a simple policy called `Token Validation` in `Policy Library`.
2. Add an **Advanced Script Filter** to another policy.
3. In the filter's **Resources** tab, add a resource:
   - **Name:** `validate`
   - **Type:** Policy
   - **Value:** `Token Validation`
4. In the script body:

```javascript
function invoke(msg) {
    return invokeResource(msg, "validate");
}
```

The policy is called by reference — Policy Studio tracks the link, and exporting the script also exports the target policy.

---

## Your first example: a Java method as a selector

This example uses **Extension Context** to expose a static Java method under the selector `${extensions['hello'].greet}`. Using a static method means no instantiation annotation is needed, which keeps the example minimal.

Place `HelloExtension.java` in `$VDISTDIR/ext/dynamic/` — the Dynamic Compiler picks it up on the next deployment:

```java
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionContext;
import com.vordel.circuit.filter.devkit.context.annotations.SubstitutableMethod;
import com.vordel.circuit.filter.devkit.context.annotations.SelectorExpression;

@ExtensionContext("hello")
public class HelloExtension {

    @SubstitutableMethod
    public static String greet(
            @SelectorExpression("http.querystring.name") String name) {
        return "Hello, " + name + "!";
    }
}
```

Deploy the configuration. The selector `${extensions['hello'].greet}` is now available everywhere in the gateway.

---

## Next steps

- [Concepts](Concepts.md) — the mental model, JUEL selectors, and the method export triad.
- [Architecture](Architecture.md) — how FDK components fit together.
- [Scripting Guide](Scripting.md) — the full scripting API.
- [Java Extensions](JavaExtensions.md) — exposing Java code to selectors and filters.
