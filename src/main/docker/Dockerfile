# Cache stage
from ubuntu:22.04 as stage_cache

### refresh the 'apt' cache and download debian packages
run --mount=type=tmpfs,target=/tmp \
    apt-get update

from ubuntu:22.04 as base_image

run --mount=type=tmpfs,target=/tmp \
    --mount=type=cache,from=stage_cache,sharing=locked,source=/var/cache/apt,target=/var/cache/apt \
    --mount=type=cache,from=stage_cache,sharing=locked,source=/var/lib/apt,target=/var/lib/apt \
    apt-get install -y wget apt-transport-https gpg && \
    wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor | tee /etc/apt/trusted.gpg.d/adoptium.gpg > /dev/null && \
    echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | tee /etc/apt/sources.list.d/adoptium.list && \
    apt-get update && \
    apt-get install -y libxkbfile-dev libsecret-1-dev git sudo curl && \
    apt-get autoremove --yes

## node install stuff from https://github.com/nodejs/docker-node/blob/main/22/trixie-slim/Dockerfile
env NODE_VERSION=22.22.0
env YARN_VERSION=1.22.22

run --mount=type=tmpfs,target=/tmp \
    --mount=type=cache,from=stage_cache,sharing=locked,source=/var/cache/apt,target=/var/cache/apt \
    --mount=type=cache,from=stage_cache,sharing=locked,source=/var/lib/apt,target=/var/lib/apt \
    ARCH= && dpkgArch="$(dpkg --print-architecture)" && \
    case "${dpkgArch##*-}" in \
      amd64) ARCH='x64';; \
      ppc64el) ARCH='ppc64le';; \
      s390x) ARCH='s390x';; \
      arm64) ARCH='arm64';; \
      armhf) ARCH='armv7l';; \
      i386) ARCH='x86';; \
      *) echo "unsupported architecture"; exit 1 ;; \
    esac && \
    set -ex && \
    apt-get install -y ca-certificates curl wget gnupg dirmngr xz-utils libatomic1 --no-install-recommends && \
    echo "using https://nodejs.org/dist/v$NODE_VERSION/node-v$NODE_VERSION-linux-$ARCH.tar.xz" && \
    curl -fsSLO --compressed "https://nodejs.org/dist/v$NODE_VERSION/node-v$NODE_VERSION-linux-$ARCH.tar.xz" && \
    tar -xJf "node-v$NODE_VERSION-linux-$ARCH.tar.xz" -C /usr/local --strip-components=1 --no-same-owner && \
    rm "node-v$NODE_VERSION-linux-$ARCH.tar.xz" && \
    ln -s /usr/local/bin/node /usr/local/bin/nodejs && \
    node --version && \
    npm --version

run --mount=type=tmpfs,target=/tmp \
    --mount=type=cache,from=stage_cache,sharing=locked,source=/var/cache/apt,target=/var/cache/apt \
    --mount=type=cache,from=stage_cache,sharing=locked,source=/var/lib/apt,target=/var/lib/apt \
    set -ex && \
    apt-get install -y ca-certificates curl wget gnupg dirmngr --no-install-recommends && \
    curl -fsSLO --compressed "https://yarnpkg.com/downloads/$YARN_VERSION/yarn-v$YARN_VERSION.tar.gz" && \
    mkdir -p /opt && \
    tar -xzf yarn-v$YARN_VERSION.tar.gz -C /opt/ && \
    ln -s /opt/yarn-v$YARN_VERSION/bin/yarn /usr/local/bin/yarn && \
    ln -s /opt/yarn-v$YARN_VERSION/bin/yarnpkg /usr/local/bin/yarnpkg && \
    rm yarn-v$YARN_VERSION.tar.gz && \
    yarn --version

# Create theia user
run adduser theia --shell /bin/bash && \
    chown -R theia:theia /home/theia && \
    chmod 755 /home/theia

# Create axway user and directories with sudo configuration
# Application will be copied to /home/axway
# Default workspace is located at /home/project
run adduser axway --shell /bin/bash && \
    mkdir -p /etc/sudoers.d && \
    echo "axway ALL=(ALL) NOPASSWD: ALL" > /etc/sudoers.d/010_axway-nopasswd && \
    chmod 644 /etc/sudoers.d/010_axway-nopasswd && \
    chmod 700 /etc/sudoers.d && \
    chmod g+rw /home && \
    mkdir -p /home/project && \
    chown -R axway:axway /home/axway && \
    chown -R axway:axway /home/project

from base_image as build_stage

run --mount=type=tmpfs,target=/tmp \
    --mount=type=cache,from=stage_cache,sharing=locked,source=/var/cache/apt,target=/var/cache/apt \
    --mount=type=cache,from=stage_cache,sharing=locked,source=/var/lib/apt,target=/var/lib/apt \
    --mount=target=/tmp/export,type=bind \
    apt-get install -y build-essential make gcc

env HOME=/home/theia
user theia
workdir /home/theia

run cd $HOME && \
    git init . && \
    git remote add origin https://github.com/eclipse-theia/theia-blueprint.git && \
    git fetch origin 30ddd9f86e7093fb16ca322d9c4e3cdfae53d279 && \
    git reset --hard FETCH_HEAD

run yarn --pure-lockfile && \
    yarn build:extensions

run yarn download:plugins

run yarn browser build && \
    yarn

run yarn autoclean --init && \
    echo *.ts >> .yarnclean && \
    echo *.ts.map >> .yarnclean && \
    echo *.spec.* >> .yarnclean && \
    yarn autoclean --force && \
    yarn cache clean && \
    rm -rf .git applications/electron theia-extensions/launcher theia-extensions/updater node_modules

from base_image

arg APIM_RUN_FILE
arg APIM_LIC_FILE

run --mount=type=tmpfs,target=/tmp \
    --mount=type=cache,from=stage_cache,sharing=locked,source=/var/cache/apt,target=/var/cache/apt \
    --mount=type=cache,from=stage_cache,sharing=locked,source=/var/lib/apt,target=/var/lib/apt \
    DEBIAN_FRONTEND=noninteractive apt-get install -y git openssh-client bash libsecret-1-0 && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y novnc websockify && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends openbox tint2 xdg-utils lxterminal hsetroot tigervnc-standalone-server supervisor && \
    DEBIAN_FRONTEND=noninteractive apt-get --no-install-recommends install -y build-essential libx11-dev libxkbfile-dev sudo && \
    apt-get autoremove --yes

run --mount=type=tmpfs,target=/tmp \
    --mount=type=cache,from=stage_cache,sharing=locked,source=/var/cache/apt,target=/var/cache/apt \
    --mount=type=cache,from=stage_cache,sharing=locked,source=/var/lib/apt,target=/var/lib/apt \
    --mount=target=/tmp/export,type=bind \
    echo 'hsetroot -solid "#123456" &' >> /etc/xdg/openbox/autostart && \
    ln -s vnc.html /usr/share/novnc/index.html && \
    cp /tmp/export/src/main/playground/supervisord.conf /etc/ && \
    cp /tmp/export/src/main/playground/menu.xml /etc/xdg/openbox/ && \
    su axway -c "mkdir -p /home/axway/.config/tint2" && \
    su axway -c "cp /tmp/export/src/main/playground/tint2rc /home/axway/.config/tint2 && \
        mkdir -p /home/axway/.local/share/icons/dist && \
        mkdir -p /home/axway/.local/share/applications && \
        cp /tmp/export/src/main/playground/policystudio.png /home/axway/.local/share/icons/dist && \
        cp /tmp/export/src/main/playground/policystudio.desktop /home/axway/.local/share/applications"

# install temurin jdk 11 and 17 and fdk sources
run --mount=type=tmpfs,target=/tmp \
	--mount=type=cache,from=stage_cache,sharing=locked,source=/var/cache/apt,target=/var/cache/apt \
	--mount=type=cache,from=stage_cache,sharing=locked,source=/var/lib/apt,target=/var/lib/apt \
    --mount=target=/tmp/export,type=bind \
	cd /tmp && tar --exclude='dist/*.run' --exclude='dist/*.lic' -cpf - export | tar -C /home/project -xpf - && \
	mv /home/project/export /home/project/filter-devkit && \
	mkdir -p /home/axway/.gradle && \
	cp /home/project/filter-devkit/src/main/playground/gradle.properties /home/axway/.gradle && \
	chmod 755 /home/project/filter-devkit/gradlew && \
	chown -R axway:axway /home/project/filter-devkit && \
	chown -R axway:axway /home/axway/.gradle && \
	apt-get install -y git temurin-11-jdk temurin-17-jdk && \
	apt-get autoremove --yes

    run --mount=type=tmpfs,target=/tmp \
    --mount=target=/tmp/export,type=bind \
    cp /tmp/export/${APIM_RUN_FILE} /tmp/APIGateway_Install_linux-x86-64.run && \
    cp /tmp/export/${APIM_LIC_FILE} /tmp/API.lic && \
    mkdir -p /opt/axway && \
    chown -R axway:axway /opt/axway && \
    chmod 755 /tmp/APIGateway_Install_linux-x86-64.run && \
    # install API Gateway and Policy Studio
    su axway -c "/tmp/APIGateway_Install_linux-x86-64.run \
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
    # install Cassandra
    su axway -c "/tmp/APIGateway_Install_linux-x86-64.run \
        --mode unattended \
        --acceptGeneralConditions yes \
        --setup_type advanced \
        --enable-components cassandra \
        --disable-components nodemanager,apigateway,apimgmt,analytics,policystudio,configurationstudio,packagedeploytools,qstart,agentsConfiguration \
        --prefix /opt/axway \
        --cassandraInstalldir /opt/axway \
        --cassandraJDK /opt/axway/apigateway/Linux.x86_64/jre \
        --startCassandra 0 " && \
    # initialize Admin Node Manager
    su axway -c "/opt/axway/apigateway/posix/bin/managedomain --initialize --nm_name anm-playground --host 127.0.0.1 --port 8090 --domain_name domain-playground" && \
    # create first instance in the domain
    su axway -c "cd /opt/axway/apigateway && \
        posix/bin/nodemanager -d && \
        posix/bin/managedomain --username admin --password changeme --create_instance --name playground-instance --group playground-group && \
        posix/bin/nodemanager -k"

# generate temporary (build time) gradle configuration
# build and deploy filter-devkit on the local API Gateway
run --mount=type=tmpfs,target=/tmp \
	--mount=type=tmpfs,target=/home/axway/.gradle \
	--mount=type=tmpfs,target=/home/project/filter-devkit/.gradle \
	cp /home/project/filter-devkit/src/main/playground/gradle.properties /home/axway/.gradle && \
	chown -R axway:axway /home/axway/.gradle && \
	chown -R axway:axway /home/project/filter-devkit/.gradle && \
	su axway -c "cd /home/project/filter-devkit && \
		./gradlew clean cleanEclipse build jar copyArchives deployRuntime deployPlugin && \
		./gradlew clean && \
		mkdir -p /opt/axway/apigateway/ext/dynamic" && \
	su axway -c "cp /home/project/filter-devkit/src/main/playground/jvm.xml /opt/axway/apigateway/groups/topologylinks/playground-group-playground-instance/conf"

copy --from=build_stage --chown=theia:theia /home/theia /home/theia

# expose theia port and noVNC port
expose 3000
expose 6080

# Specify default shell for Theia and the Built-In plugins directory
env SHELL=/bin/bash
env THEIA_DEFAULT_PLUGINS=local-dir:/home/theia/plugins

# Use installed git instead of dugite
env USE_LOCAL_GIT=true

# launch supervisord
entrypoint ["/bin/bash", "-c", "/usr/bin/supervisord"]
