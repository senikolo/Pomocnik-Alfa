FROM eclipse-temurin:17-jdk-jammy AS builder

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends curl unzip xz-utils ca-certificates \
    && rm -rf /var/lib/apt/lists/*

ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=$PATH:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools

RUN mkdir -p /opt/android-sdk/cmdline-tools \
    && curl -fL --retry 4 "https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip" -o /tmp/cmdline.zip \
    && unzip -q /tmp/cmdline.zip -d /tmp/android-cli \
    && mkdir -p /opt/android-sdk/cmdline-tools/latest \
    && mv /tmp/android-cli/cmdline-tools/* /opt/android-sdk/cmdline-tools/latest/ \
    && yes | sdkmanager --licenses >/dev/null || true

RUN sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

WORKDIR /work
RUN curl -fL --retry 4 \
      "https://f366ae4b-1478-4dc8-b5e5-0c4f287fe888.sandbox.floot.app/_cdn/static/8c9b84bd-d903-41e1-b51b-6f52ccf67725-pa176-compile-micro.tar.xz" \
      -o source.tar.xz \
    && mkdir project \
    && tar -xJf source.tar.xz -C project

RUN set -eux; \
    BASE="https://f366ae4b-1478-4dc8-b5e5-0c4f287fe888.sandbox.floot.app"; \
    curl -fL --retry 4 "$BASE/_cdn/static/2b1aea97-103f-4ccf-ad01-567096dad8b1-pa176-storm.pcm.xz" -o storm.pcm.xz; \
    xz -dc storm.pcm.xz > project/app/src/main/assets/audio/storm.pcm; \
    curl -fL --retry 4 "$BASE/_cdn/static/192b7d3a-f2e7-4d7e-a7ed-bd1428363a2b-pa176-purr.pcm.xz" -o purr.pcm.xz; \
    xz -dc purr.pcm.xz > project/app/src/main/assets/audio/purr.pcm; \
    curl -fL --retry 4 "$BASE/_cdn/static/5c6fbef5-a7fd-4d6e-a55d-8973e9640f78-pa176-ispina_lokalnie_1_82_35.apk.xz" -o ispina.apk.xz; \
    xz -dc ispina.apk.xz > project/app/src/main/res/raw/ispina_lokalnie_1_82_35.apk; \
    curl -fL --retry 4 "$BASE/_cdn/static/d0c25682-3c57-4d98-add2-526bcccad42f-pa176-icon-legacy.png" -o project/app/src/main/res/drawable-nodpi/ic_launcher_cat_legacy.png; \
    curl -fL --retry 4 "$BASE/_cdn/static/1862bb41-c1a2-43fa-a9f5-3465625490e8-pa176-icon-foreground.png" -o project/app/src/main/res/drawable-nodpi/ic_launcher_cat_foreground.png; \
    echo "a51442994c6d8b1add65e86bf7955fd8ad9da12785a36ad59dcabfafba8d1b68  project/app/src/main/assets/audio/storm.pcm" | sha256sum -c -; \
    echo "0d8336d945050f0456557187358359560d7303116547ceb1ecd6e8c9f251d527  project/app/src/main/assets/audio/purr.pcm" | sha256sum -c -; \
    echo "0a69fc101091796ad7b2f08e385b5267dd04824ae63df5e31b48f1486de38842  project/app/src/main/res/raw/ispina_lokalnie_1_82_35.apk" | sha256sum -c -; \
    echo "35a9f2bd3fc783b221ccf3ec672fd6f2aed8480caf42c10618d12ba988866d62  project/app/src/main/res/drawable-nodpi/ic_launcher_cat_legacy.png" | sha256sum -c -; \
    echo "7b8b3afd8bf51d8aae89c23ce052d438320f3b07933f6370c2040e126d28d6ec  project/app/src/main/res/drawable-nodpi/ic_launcher_cat_foreground.png" | sha256sum -c -

RUN cd project \
    && grep -n "versionName '1.7.6'" app/build.gradle \
    && grep -n "versionCode 10706" app/build.gradle \
    && chmod +x gradlew \
    && ./gradlew --no-daemon --stacktrace assembleDebug \
    && cp app/build/outputs/apk/debug/app-debug.apk /work/Pomocnik-Alfa-1.7.6.apk \
    && sha256sum /work/Pomocnik-Alfa-1.7.6.apk > /work/Pomocnik-Alfa-1.7.6.sha256

FROM python:3.12-alpine
WORKDIR /app
COPY --from=builder /work/Pomocnik-Alfa-1.7.6.apk /app/Pomocnik-Alfa-1.7.6.apk
COPY --from=builder /work/Pomocnik-Alfa-1.7.6.sha256 /app/Pomocnik-Alfa-1.7.6.sha256
CMD ["sh","-c","python -m http.server ${PORT:-8080} --directory /app"]
