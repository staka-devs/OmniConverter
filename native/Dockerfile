# arm64-v8a 向けにエンジンをソースからビルドする環境。ADR-0005
FROM ubuntu:24.04

ARG NDK_VERSION=r30
ARG NDK_SHA1=5107f898313790e449e87eee2183d9a20602dee9
ENV ANDROID_NDK_HOME=/opt/android-ndk

RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        autoconf automake autopoint bison ca-certificates cmake curl file flex gettext git gperf libtool \
        make meson nasm ninja-build patch pkg-config python3 texinfo unzip xz-utils yasm \
    && rm -rf /var/lib/apt/lists/* \
    && curl -fsSL -o /tmp/ndk.zip https://dl.google.com/android/repository/android-ndk-${NDK_VERSION}-linux.zip \
    && echo "${NDK_SHA1}  /tmp/ndk.zip" | sha1sum -c - \
    && unzip -q /tmp/ndk.zip -d /opt \
    && mv /opt/android-ndk-${NDK_VERSION} ${ANDROID_NDK_HOME} \
    && rm /tmp/ndk.zip

WORKDIR /native
ENTRYPOINT ["/native/build.sh"]
