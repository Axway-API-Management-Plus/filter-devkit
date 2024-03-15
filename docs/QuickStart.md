# Quick start

A playground Dockerfile is provided. This Dockerfile will build a Theia IDE, install an API Gateway (with minimal ANM configuration) and deploy the FDK on it.

Start by cloning the project on github and set working directory to the root of the project

```
git clone -b develop https://github.com/Axway-API-Management-Plus/filter-devkit.git
```

Copy the Axway setup file (APIGateway_7.7.20231130_Install_linux-x86-64_BN02.run) and licence file in the project 'dist' directory

Execute the following docker build command (replace filenames according to your need, but keep the dist prefix with a forward slash).

*For Windows :*

```
docker build --build-arg APIM_RUN_FILE=dist/APIGateway_7.7.20231130_Install_linux-x86-64_BN02.run --build-arg APIM_LIC_FILE=dist/licence.lic -t filter-devkit-docker -f src\main\docker\Dockerfile .
```

*For Linux :*

```
docker build --build-arg APIM_RUN_FILE=dist/APIGateway_7.7.20231130_Install_linux-x86-64_BN02.run --build-arg APIM_LIC_FILE=dist/licence.lic -t filter-devkit-docker -f src/main/docker/Dockerfile .
```

At the end of the build, you should have a (big) docker image called 'filter-devkit-docker'. run it with the following docker command

```
docker run -p=6080:6080 -p=3000:3000 -p=8090:8090 -p=8080:8080 -p=8065:8065 -p=8075:8075 -p=9777:9777 --mount type=tmpfs,destination=/tmp filter-devkit-docker
```

once started, go to

- [http://localhost:3000/](http://localhost:3000/) for the Theia Java IDE
- [http://localhost:6080/](http://localhost:6080/) for the VNC server (Policy Studio)
- [https://localhost:8090/](http://localhost:8090/) for the Admin Node Manager

The filter DevKit sources are available in /home/project/filter-devkit (copied from local clone of git repository). filter-devkit jar and policy studio plugins are deployed in the relevant directories.

To correctly setup theia, type the following commands in the terminal :

```
cd /home/project/filter-devkit
./gradlew clean cleanEclipse eclipse build
```

This will rebuild jars locally. reload Theia after the first build has been completed.

## Exposed services in the base image

The following ports are bound by the installation
 - 3000 : eclipse theia IDE
 - 6080 : noVNC X11 interface
 - 8080 : instance default services (not started on boot)
 - 8090 : Admin Node Manager interface
 - 9777 : Java Debug port for the instance (useable with 'Debug Remote Java Application' in IDE)
