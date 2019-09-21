#!/bin/bash
set -ex
export ADOPTOPENJDK=${JAVA_VERSION:-8}
curl -sL https://get.sdkman.io | bash
echo sdkman_auto_answer=true > $HOME/.sdkman/etc/config
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java $(sdk list java | grep -o "$ADOPTOPENJDK\.[0-9\.]*hs-adpt" | head -1)
unset JAVA_HOME
java -Xmx32m -version
git fetch --tags
echo 'source "$HOME/.sdkman/bin/sdkman-init.sh"' >> $HOME/.bash_profile
