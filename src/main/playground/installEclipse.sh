#!/bin/sh

cd /home/theia && \
wget "https://www.eclipse.org/downloads/download.php?file=/technology/epp/downloads/release/2023-12/R/eclipse-jee-2023-12-R-linux-gtk-x86_64.tar.gz&r=1" -O eclipse-jee-linux-gtk-x86_64.tar.gz && \
tar -xpvzf eclipse-jee-linux-gtk-x86_64.tar.gz

sudo mv /home/theia/eclipse /opt/eclipse
