FROM ubuntu:18.04

MAINTAINER Krystian Zlomek <k.zlomek@adblockplus.org>

RUN apt-get update -qyy && \
    apt-get install -qyy \
    sudo \
    dumb-init \
    curl wget \
    p7zip-full unzip \
    python \
    npm \
    openjdk-8-jdk \
    build-essential clang libc++-dev libc++abi-dev

WORKDIR /opt

RUN wget https://dl.google.com/android/repository/android-ndk-r16b-linux-x86_64.zip -O ./android-ndk.zip && \
    unzip -q ./android-ndk.zip -d ./ && \
    rm ./android-ndk.zip

RUN wget https://dl.google.com/android/repository/sdk-tools-linux-4333796.zip -O ./sdk-tools.zip && \
    unzip -q ./sdk-tools.zip -d ./android-sdk && \
    rm ./sdk-tools.zip

RUN echo y | /opt/android-sdk/tools/bin/sdkmanager "build-tools;28.0.3" "platforms;android-28"

ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_NDK_ROOT=/opt/android-ndk-r16b
ENV ANDROID_NDK_HOME=/opt/android-ndk-r16b

RUN curl -L https://packages.gitlab.com/install/repositories/runner/gitlab-runner/script.deb.sh | bash

COPY pin-gitlab-runner.pref /etc/apt/preferences.d/pin-gitlab-runner.pref

RUN apt-get install -qyy gitlab-runner

RUN adduser --gecos "" --disabled-password ci_user && \
    usermod -aG sudo ci_user && \
    echo "ci_user ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers && \
    mkdir /opt/ci && \
    chown -R ci_user:ci_user /opt/ci

ENTRYPOINT ["/usr/bin/dumb-init", "--"]

CMD ["gitlab-runner", "run", "--working-directory", "/opt/ci", "--user", "ci_user"]
