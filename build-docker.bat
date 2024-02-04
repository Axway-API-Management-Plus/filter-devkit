docker build --build-arg APIM_RUN_FILE=%1 --build-arg APIM_LIC_FILE=%2 -t filter-devkit-docker -f src\main\docker\Dockerfile .
