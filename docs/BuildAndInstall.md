# Build and Install

The Filter DevKit uses a Gradle build process. The process of building and installing from sources is quite simple.

## Prerequisites

As a prerequisite, you need to have API Gateway and Policy Studio installed. A JDK is also required. The required version depends on the installed API Gateway. If developping on Windows, you can inflate an API Gateway installation archive which comes from Linux and use it for building. Otherwise, you may use WSL2 for build.

You must set two properties in the global gradle properties file (*Home Directory*/.gradle/gradle.properties).

```properties
# installation directory for api gateway
apigw_vdistdir=/opt/axway/apigateway

# installation directory for the policy studio
studio_vdistdir=/opt/axway/policystudio
```

In order to launch the gradle build, you must set the JAVA_HOME variable if not already set.

git is also required.

## Building

Start by cloning the project on github and set working directory to the root of the project

```
git clone -b develop https://github.com/Axway-API-Management-Plus/filter-devkit.git
```

Once prerequisites are set (api gateway install, gradle global properties and JAVA_HOME) and repository cloned, you're ready to launch the build process. The following tasks are available

 - *clean* : clean build files and directories (but keep gradle cache)
 - *cleanEclipse* : remove eclipse IDE configuration
 - *eclipse* : create eclipse project configuration
 - *build* : build all projects without deploy
 - *copyArchives* : Copies all produced jars into the root build directory (filter-devkit/build/archives)
 - *deployRuntime* : Install runtime jars in the local installed API Gateway (in the distribution ext/lib directory)
 - *deployPlugin* : Deploy Filters and filter-devkit runtime in Policy Studio (drop jars in the plugins directory)

When *deployPlugin* task is used, do not forget to restart the Policy Studio with the *-clean* command line argument.

Typical command to rebuild and install everything

*For Windows :*

```
gradlew.bat clean cleanEclipse eclipse build copyArchives deployRuntime deployPlugin
```

*For Linux :*

```
./gradlew clean cleanEclipse eclipse build copyArchives deployRuntime deployPlugin
```

## Additional steps after install

After libraries installation, the filter DevKit must be registered in the Entity Store. This task is done under policy studio.
Any new Quick Filter must also be registered in the policy studio.

You must stop and start each instance after runtime installation in the API Gateway directory (*deployRuntime*). Restart option will not work.

### Base Filter DevKit activation

Open a configuration in the Policy Studio and choose File -> Import -> Import Custom Filter (this option is not available in YAML entity store as time of writing) and choose the [apigwsdkset.xml](../filter-devkit-runtime/src/main/typesets/apigwsdkset.xml) file.

### Quick Filter activation

Each QuickFilter archive embeds the typesets for Policy Studio. Prior to filter registration, you must inflate those files. Once typesets are inflated, Open a configuration in the Policy Studio and choose File -> Import -> Import Custom Filter and choose the typeset.xml inflated file (which is the root typeset for each QuickFilter archive).
