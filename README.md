# Filter Development Kit

This project contains a set of libraries for implementing API Gateway Filters and making advanced scripts.

## Prerequisites

To build this artefact, you need the following items:
 - JDK 1.8
 - API Gateway and Policy Studio 7.7.20220530

Produced libraries are only valid on this particular release. new branches will appear for newer versions

Please not that this release is a work in progress and not ready yet for upgrade or production. If you wish to test, use a docker image built using the provided Dockerfile.

## Basic Features

 - [Pluggable module support for Java](docs/ClassPathScanning.md)
 - [Advanced script filter](docs/AdvancedScriptFilter.md)
 - Assertion Filter (trigger error handling on condition)
 - Deployment time compilation support for developers

QuickFilter code is still around but not ready yet for use. As time of writing, only the above feature are considered stable.

## Developer Feature

 - [Dynamic compilation support](docs/DynamicCompiler.md)

## Quick start

A QuickStart Dockerfile is provided. This Dockerfile will build a Theia IDE, install an API Gateway (with minimal ANM configuration) and deploy the FDK on it.

Start by cloning the project on github and set working directory to the root of the project

```
git clone https://github.com/Axway-API-Management-Plus/filter-devkit.git
```

Copy the Axway setup file (APIGateway_7.7.20220530_Install_linux-x86-64_BN02.run) and licence file in the project 'dist' directory

Execute the following docker build command (filenames can change, but keep the dist prefix with a forward slash).

```
docker build --build-arg APIM_RUN_FILE=dist/APIGateway_7.7.20220530_Install_linux-x86-64_BN02.run --build-arg APIM_LIC_FILE=dist/licence.lic -t filter-devkit-docker -f src\main\docker\Dockerfile .
```

At the end of the build, you should have a docker image called 'filter-devkit-docker'. run it with the following docker command

```
docker run -p=3000:3000 -p=8090:8090 -p=8080:8080 --mount type=tmpfs,destination=/tmp filter-devkit-docker
```

once started, go to http://localhost:3000/

You can now start the ANM (API Gateway is installed in /opt/axway/apigateway) using the following commands in a theia terminal

```
cd /opt/axway/apigateway
posix/bin/nodemanager
```

Create an instance using the ANM available at https://localhost:8090/
Make the instance with the default services port listening at 8080 (otherwise you have to restart you docker container with a new forwarded port) and keep the default 8085 for the management port. Do not use YAML !

Happy testing !