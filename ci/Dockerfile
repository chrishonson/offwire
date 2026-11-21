# Builder image for the Cloud Build gates: JDK 21, python3 (scripts/run-gate.py)
# and the Android SDK the quality gate's assembleDebug needs. Built inside each
# Cloud Build run from this file (cloudbuild.yaml step "image"), so nothing is
# pushed to a registry. This image never receives a secret: the merge step runs
# in a Google-maintained image instead.
FROM eclipse-temurin:21-jdk

RUN apt-get update \
  && apt-get install -y --no-install-recommends python3 curl unzip ca-certificates \
  && rm -rf /var/lib/apt/lists/*

# Android SDK: compileSdk 36 (gradle/libs.versions.toml). Licences are accepted
# here so Gradle never prompts.
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
RUN mkdir -p $ANDROID_HOME/cmdline-tools \
  && curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o /tmp/cmdline-tools.zip \
  && unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools \
  && mv /tmp/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest \
  && rm -rf /tmp/cmdline-tools /tmp/cmdline-tools.zip \
  && yes | sdkmanager --licenses > /dev/null \
  && sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0" > /dev/null

ENV CI=true
