# Builder stage
FROM node:16-bookworm as build-stage

# install required tools to build the application
RUN apt-get update && apt-get install -y libxkbfile-dev libsecret-1-dev git

WORKDIR /home/theia

RUN git clone https://github.com/eclipse-theia/theia-blueprint.git . && \
	yarn --pure-lockfile && \
	yarn build:extensions && \
	yarn download:plugins && \
	yarn browser build && \
	yarn --production && \
	yarn autoclean --init && \
	echo *.ts >> .yarnclean && \
	echo *.ts.map >> .yarnclean && \
	echo *.spec.* >> .yarnclean && \
	yarn autoclean --force && \
	yarn cache clean && \
	rm -r .git applications/electron theia-extensions/launcher theia-extensions/updater node_modules

FROM node:16-bookworm

ARG APIM_RUN_FILE
ARG APIM_LIC_FILE

RUN --mount=type=tmpfs,target=/tmp

COPY . /tmp/filter-devkit

RUN apt update && \
	apt install -y wget apt-transport-https && \
	wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | tee /usr/share/keyrings/adoptium.asc && \
	echo "deb [signed-by=/usr/share/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | tee /etc/apt/sources.list.d/adoptium.list && \
	apt update && \
	apt upgrade -y && \
	apt install -y git openssh-client bash libsecret-1-0 temurin-8-jdk temurin-11-jdk temurin-17-jdk && \
	apt --no-install-recommends install -y build-essential libx11-dev libxkbfile-dev sudo && \
	apt clean autoclean && apt autoremove --yes && rm -rf /var/lib/apt/lists/*

# Create theia user and directories
# Application will be copied to /home/theia
# Default workspace is located at /home/project
RUN adduser theia --shell /bin/bash && \
	mkdir -p /etc/sudoers.d && \
	echo "theia ALL=(ALL) NOPASSWD: ALL" > /etc/sudoers.d/010_theia-nopasswd && \
	chmod 644 /etc/sudoers.d/010_theia-nopasswd && \
	chmod 700 /etc/sudoers.d && \
	chmod g+rw /home && \
	mkdir -p /home/theia/.gradle && \
	mkdir -p /opt/axway && \
	echo 'org.gradle.java.home=/usr/lib/jvm/temurin-8-jdk-amd64' >> /home/theia/.gradle/gradle.properties && \
	echo 'apigw_vdistdir=/opt/axway/apigateway' >> /home/theia/.gradle/gradle.properties && \
	echo 'studio_vdistdir=/opt/axway/policystudio' >> /home/theia/.gradle/gradle.properties && \
	mkdir -p /home/project && \
	mv /tmp/filter-devkit/${APIM_RUN_FILE} /tmp/APIGateway_Install_linux-x86-64.run && \
	mv /tmp/filter-devkit/${APIM_LIC_FILE} /tmp/API.lic && \
	mv /tmp/filter-devkit /home/project/ &&\
	chown -R theia:theia /home/theia && \
	chown -R theia:theia /home/project && \
	chown -R theia:theia /opt/axway

WORKDIR /home/theia

RUN chmod 755 /tmp/APIGateway_Install_linux-x86-64.run && \
	su theia -c "/tmp/APIGateway_Install_linux-x86-64.run \
		--mode unattended \
		--acceptGeneralConditions yes \
		--setup_type advanced \
		--enable-components nodemanager,apigateway,apimgmt,analytics,policystudio,configurationstudio,packagedeploytools \
		--disable-components cassandra,qstart,agentsConfiguration \
		--prefix /opt/axway \
		--changeCredentials 0 \
		--firstInNewDomain 1 \
		--nmHostnameOrIpChoice 127.0.0.1 \
		--licenseFilePath /tmp/API.lic" && \
	su theia -c "/tmp/APIGateway_Install_linux-x86-64.run \
		--mode unattended \
		--acceptGeneralConditions yes \
		--setup_type advanced \
		--enable-components cassandra \
		--disable-components nodemanager,apigateway,apimgmt,analytics,policystudio,configurationstudio,packagedeploytools,qstart,agentsConfiguration \
		--prefix /opt/axway \
		--cassandraInstalldir /opt/axway \
		--cassandraJDK /opt/axway/apigateway/Linux.x86_64/jre \
		--startCassandra 0 " && \
	su theia -c "/opt/axway/apigateway/posix/bin/managedomain --initialize --nm_name ANM-FDK --host 127.0.0.1 --port 8090 --domain_name Domain-FDK" && \
	rm -f /tmp/APIGateway_Install_linux-x86-64.run && \
	rm -f /tmp/API.lic && \
	chmod 755 /home/project/filter-devkit/gradlew && \
	su theia -c "cd /home/project/filter-devkit && ./gradlew clean cleanEclipse build jar copyArchives deployRuntime" && \
	su theia -c "mkdir -p /opt/axway/apigateway/ext/dynamic"

ENV HOME /home/theia

# Copy application from builder-stage
COPY --from=build-stage --chown=theia:theia /home/theia /home/theia

EXPOSE 3000
EXPOSE 8090

# Specify default shell for Theia and the Built-In plugins directory
ENV SHELL=/bin/bash \
    THEIA_DEFAULT_PLUGINS=local-dir:/home/theia/plugins

# Use installed git instead of dugite
ENV USE_LOCAL_GIT true

# Switch to Theia user
USER theia
WORKDIR /home/theia/applications/browser

# Launch the backend application via node
ENTRYPOINT [ "node", "/home/theia/applications/browser/lib/backend/main.js" ]

# Arguments passed to the application
CMD [ "/home/project", "--hostname=0.0.0.0" ]
