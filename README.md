# Filter Development Kit

This project contains a set of libraries for implementing API Gateway Filters and making advanced scripts.

## Prerequisites

To build this artefact, you need the following items:
 - JDK 1.8
 - Apache Maven 3.6.1
 - API Gateway and Policy Studio 7.7 with no fixpack

Produced libraries should be useable on API Gateway and Policy Studio from version 7.5.3 to 7.7

## Basic Features

 - [Quick Filters integration for scripts](docs/QuickScriptFilter.md) (Javascript, Jython, Groovy)
 - [Quick Filters integration for Java](docs/QuickJavaFilter.md) using annotations
 - [Pluggable module support for Java](docs/ClassPathScanning.md)
 - [Extended Groovy script filter](docs/GroovyScriptFilter.md)
 - Assertion Filter (trigger error handling on condition)
 - Initializer Shortcut Filter
 - KPS Read/Write Framework
 - Deployment time compilation support for developers

## Developer Feature

 - [Dynamic compilation support](docs/DynamicCompiler.md)

## Installation

Start by cloning the project on github and set working directory to the root of the project

```
git clone https://github.com/Axway-API-Management-Plus/filter-devkit.git
```

The Filter development kit is provided with a bootstrap script. Boostrap process will populate the local maven repository with artifacts retrieved from a local fresh install of API Gateway and Policy Studio after configuration of API Manager on a linux machine with direct access to internet. Please note that the first active proxy in the maven configuration may be used as well as configured credentials. Maven proxy configuration with proxy credentials is however not supported as time of writing (the bootstrap script is still a work in progress).

Type the following command in the linux shell (replace api gateway path and policy studio path by relevant information on your local machine).

```
./boostrap.sh /Volumes/Unix/linux-x86_64/apigateway/opt/Axway/7.7.0/apigateway /Volumes/Unix/linux-x86_64/apigateway/opt/Axway/7.7.0/policystudio
```

The bootstrap script will search and create maven artifacts in the local repository. Once this is done, check out the project from GitHub and build modules using the following command:

```
mvn clean package
```

The build command will compile all module, produce an export archive and deploy directories.

### Developer Install

Stop you API Gateway instance, copy the following jars from the 'filter-devkit-delivery/target/developer' directory in the ext/lib directory of instance and start it back
 - ecj-*.jar (eclipse compiler plugin)
 - filter-devkit-dynamic-*.jar (dynamic compilation support for instance)
 - filter-devkit-runtime-*.jar (basic runtime)
 - filter-devkit-samples-*.jar (sample implementations)

In the policy studio, import the following jars as runtime dependencies and restart the policy studio with the '-clean' option:
 - filter-devkit-runtime-*.jar
 - filter-devkit-studio-*.jar

In the policy studio, import the following typesets into a open configuration:
 - filter-devkit-delivery/target/developer/typesets/apigwsdkset.xml
 - filter-devkit-delivery/target/developer/typesets/apigwsdk-advancedset.xml

_Warning_ : If you're in a Team Dev configuration, import sets in the project with Server Settings.

Once all those actions are done, you have an API Gateway instance and configuration ready for Quick Filter and advanced scripting.

### Production Install (for production or deployment pipeline)

Stop you API Gateway instance, copy the following jars from the 'filter-devkit-delivery/target/production-base' directory in the ext/lib directory of instance and start it back
 - filter-devkit-runtime-*.jar (basic runtime)

In the policy studio, import the following jars as runtime dependencies and restart the policy studio with the '-clean' option:
 - filter-devkit-runtime-*.jar
 - filter-devkit-studio-*.jar

In the policy studio, import the following typesets into a open configuration:
 - filter-devkit-delivery/target/production-base/typesets/apigwsdkset.xml

_Warning_ : If you're in a Team Dev configuration, import sets in the project with Server Settings.
