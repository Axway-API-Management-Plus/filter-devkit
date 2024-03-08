#!/bin/sh

# Download  and inflate eclipse archive
cd /home/theia && \
wget "https://www.eclipse.org/downloads/download.php?file=/technology/epp/downloads/release/2023-12/R/eclipse-jee-2023-12-R-linux-gtk-x86_64.tar.gz&r=1" -O eclipse-jee-linux-gtk-x86_64.tar.gz && \
tar -xpzf eclipse-jee-linux-gtk-x86_64.tar.gz

# remove Archive after inflate
rm eclipse-jee-linux-gtk-x86_64.tar.gz

# move inflated directory to /opt
sudo mv /home/theia/eclipse /opt/eclipse

# create desktop shortcut for next start
cp /home/project/filter-devkit/src/main/playground/eclipse.desktop /home/theia/.local/share/applications
