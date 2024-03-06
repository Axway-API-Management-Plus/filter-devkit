# Filter Development Kit

This project contains a set of libraries for implementing API Gateway Filters and making advanced scripts.

## Prerequisites

To build this artefact, you need the following items:
 - JDK 11
 - API Gateway and Policy Studio 7.7.20231130
 
Code base is actually stable and has been tested from 20220530 (JDK 8) to 20231130 (JDK 11). Even if jars compiled using another release should work, it is recommended to use artifacts produced for a particular release using the branch specific the the API Gateway version you're targeting (some bytecode changes can trigger a MethodNotFoundException). If no branch already exists please open an issue for it or make a Pull Request for this new branch.

A playground Dockerfile is provided. It provides an on-premise API Gateway, Policy Studio, In-Browser GUI and IDE with the FDK pre-installed.

## Basic Features

 - [Filter Code Generator (Java Quick Filters)](docs/QuickJavaFilter.md)
 - [Advanced script filter](docs/AdvancedScriptFilter.md)
 - Extension subsystem (child first ClassLoader, selector accessible methods and script extensions)

## Developer Feature

 - [Dynamic compilation support](docs/DynamicCompiler.md)

## Quick start

A playground Dockerfile is provided. This Dockerfile will build a Theia IDE, install an API Gateway (with minimal ANM configuration) and deploy the FDK on it.

Start by cloning the project on github and set working directory to the root of the project

```
git clone -b develop https://github.com/Axway-API-Management-Plus/filter-devkit.git
```

Copy the Axway setup file (APIGateway_7.7.20231130_Install_linux-x86-64_BN02.run) and licence file in the project 'dist' directory

Execute the following docker build command (replace filenames according to your need, but keep the dist prefix with a forward slash).

*For Windows :*

```
docker build --build-arg APIM_RUN_FILE=dist/APIGateway_7.7.20220530_Install_linux-x86-64_BN02.run --build-arg APIM_LIC_FILE=dist/licence.lic -t filter-devkit-docker -f src\main\docker\Dockerfile .
```

*For Linux :*

```
docker build --build-arg APIM_RUN_FILE=dist/APIGateway_7.7.20220530_Install_linux-x86-64_BN02.run --build-arg APIM_LIC_FILE=dist/licence.lic -t filter-devkit-docker -f src/main/docker/Dockerfile .
```

At the end of the build, you should have a (big) docker image called 'filter-devkit-docker'. run it with the following docker command

```
docker run -p=6080:6080 -p=3000:3000 -p=8090:8090 -p=8080:8080 -p=8065:8065 -p=8075:8075 -p=9777:9777 --mount type=tmpfs,destination=/tmp filter-devkit-docker
```

once started, go to

- [http://localhost:3000/](http://localhost:3000/) for the Theia Java IDE
- [http://localhost:6080/](http://localhost:6080/) for the VNC server (Policy Studio)
- [https://localhost:8090/](http://localhost:8090/) for the Admin Node Manager

YAML Entity Store is actually not supported.

Happy testing !