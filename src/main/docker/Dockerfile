# Cache stage
FROM node:16-bookworm as stage_cache

### refresh the 'apt' cache and download debian packages
RUN --mount=type=tmpfs,target=/tmp \
	apt update

FROM node:16-bookworm as base_image

RUN --mount=type=tmpfs,target=/tmp \
	--mount=type=cache,from=stage_cache,sharing=locked,source=/var/cache/apt,target=/var/cache/apt \
	--mount=type=cache,from=stage_cache,sharing=locked,source=/var/lib/apt,target=/var/lib/apt \
	apt install -y wget apt-transport-https && \
	wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | tee /usr/share/keyrings/adoptium.asc && \
	echo "deb [signed-by=/usr/share/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | tee /etc/apt/sources.list.d/adoptium.list && \
	apt update && \
	apt install -y libxkbfile-dev libsecret-1-dev git && \
	apt upgrade -y && \
	apt autoremove --yes

# Builder stage
FROM base_image as build_stage

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

FROM base_image

ARG APIM_RUN_FILE
ARG APIM_LIC_FILE

# Create theia user and directories with sudo configuration
# Application will be copied to /home/theia
# Default workspace is located at /home/project
RUN adduser theia --shell /bin/bash && \
	mkdir -p /etc/sudoers.d && \
	echo "theia ALL=(ALL) NOPASSWD: ALL" > /etc/sudoers.d/010_theia-nopasswd && \
	chmod 644 /etc/sudoers.d/010_theia-nopasswd && \
	chmod 700 /etc/sudoers.d && \
	chmod g+rw /home && \
	mkdir -p /home/project && \
	chown -R theia:theia /home/theia && \
	chown -R theia:theia /home/project

# retrieve filter-devkit source directory in /home/project
# copy setup files in /tmp
# install API Gateway, Policy Studio and Cassandra
# initialize Admin Node Manager on port 8090
# generate persistent gradle configuration
RUN --mount=type=tmpfs,target=/tmp \
	--mount=target=/tmp/export,type=bind \
	cd /tmp && tar --exclude='dist/*.run' --exclude='dist/*.lic' -cpf - export | tar -C /home/project -xpf - && \
	cp /tmp/export/${APIM_RUN_FILE} /tmp/APIGateway_Install_linux-x86-64.run && \
	cp /tmp/export/${APIM_LIC_FILE} /tmp/API.lic && \
	mv /home/project/export /home/project/filter-devkit && \
	mkdir -p /opt/axway && \
	chmod 755 /home/project/filter-devkit/gradlew && \
	chown -R theia:theia /opt/axway && \
	chown -R theia:theia /home/project/filter-devkit && \
	chmod 755 /tmp/APIGateway_Install_linux-x86-64.run && \
	su theia -c "/tmp/APIGateway_Install_linux-x86-64.run \
		--mode unattended \
		--acceptGeneralConditions yes \
		--setup_type advanced \
		--enable-components nodemanager,apigateway,apimgmt,policystudio,packagedeploytools \
		--disable-components cassandra,qstart,agentsConfiguration,analytics,configurationstudio \
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
	su theia -c "/opt/axway/apigateway/posix/bin/managedomain --initialize --nm_name anm-fdk --host 127.0.0.1 --port 8090 --domain_name domain-fdk" && \
	mkdir -p /home/theia/.gradle && \
	cp /home/project/filter-devkit/src/main/playground/gradle.properties /home/theia/.gradle && \
	chown -R theia:theia /home/theia/.gradle

# install temurin jdk 8 and 17. Also install tiger vnc
RUN --mount=type=tmpfs,target=/tmp \
	--mount=type=cache,from=stage_cache,sharing=locked,source=/var/cache/apt,target=/var/cache/apt \
	--mount=type=cache,from=stage_cache,sharing=locked,source=/var/lib/apt,target=/var/lib/apt \
	apt upgrade -y && \
	apt install -y git openssh-client bash libsecret-1-0 temurin-8-jdk temurin-17-jdk && \
	apt install -y novnc websockify && \
	apt install -y --no-install-recommends libgtk2.0-bin openbox tint2 xdg-utils lxterminal hsetroot tigervnc-standalone-server supervisor && \
	apt --no-install-recommends install -y build-essential libx11-dev libxkbfile-dev sudo && \
	apt autoremove --yes

# generate temporary (build time) gradle configuration
# build and deploy filter-devkit on the local API Gateway
RUN --mount=type=tmpfs,target=/tmp \
	--mount=type=tmpfs,target=/home/theia/.gradle \
	--mount=type=tmpfs,target=/home/project/filter-devkit/.gradle \
	cp /home/project/filter-devkit/src/main/playground/gradle.properties /home/theia/.gradle && \
	chown -R theia:theia /home/theia/.gradle && \
	chown -R theia:theia /home/project/filter-devkit/.gradle && \
	su theia -c "cd /opt/axway/apigateway && \
		posix/bin/nodemanager -d && \
		posix/bin/managedomain --username admin --password changeme --create_instance --name fdk-instance --group fdk-group && \
		posix/bin/nodemanager -k" && \
	su theia -c "cd /home/project/filter-devkit && \
		./gradlew clean cleanEclipse build jar copyArchives deployRuntime deployPlugin && \
		./gradlew clean && \
		mkdir -p /opt/axway/apigateway/ext/dynamic" && \
	su theia -c "cp /home/project/filter-devkit/src/main/playground/jvm.xml /opt/axway/apigateway/groups/topologylinks/fdk-group-fdk-instance/conf"

ENV HOME /home/theia

# Copy application from build_stage
COPY --from=build_stage --chown=theia:theia /home/theia /home/theia

COPY src/main/playground/supervisord.conf /etc/
COPY src/main/playground/menu.xml /etc/xdg/openbox/
RUN echo 'hsetroot -solid "#123456" &' >> /etc/xdg/openbox/autostart && \
	ln -s vnc.html /usr/share/novnc/index.html && \
	su theia -c "mkdir -p /home/theia/.config/tint2" && \
	su theia -c "cp /home/project/filter-devkit/src/main/playground/tint2rc /home/theia/.config/tint2 && \
		mkdir -p /home/theia/.local/share/icons/dist && \
		mkdir -p /home/theia/.local/share/applications && \
		cp /home/project/filter-devkit/src/main/playground/policystudio.png /home/theia/.local/share/icons/dist && \
		cp /home/project/filter-devkit/src/main/playground/policystudio.desktop /home/theia/.local/share/applications"

# expose theia port and ANM port
EXPOSE 3000
EXPOSE 8090

# Specify default shell for Theia and the Built-In plugins directory
ENV SHELL=/bin/bash \
	THEIA_DEFAULT_PLUGINS=local-dir:/home/theia/plugins

# Use installed git instead of dugite
ENV USE_LOCAL_GIT true

EXPOSE 6080
ENTRYPOINT ["/bin/bash", "-c", "/usr/bin/supervisord"]
