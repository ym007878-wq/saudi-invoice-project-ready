
name: Build Android APK

on:
  workflow_dispatch:
  push:
    paths:
      - '.github/workflows/build.yml'

permissions:
  contents: read

jobs:
  build:
    name: Build Saudi Invoice Scanner
    runs-on: ubuntu-latest

    steps:
      - name: Checkout project
        uses: actions/checkout@v4

      - name: Set up Java
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '9.3.1'

      - name: Locate Android SDK
        shell: bash
        run: |
          echo "ANDROID_HOME=${ANDROID_HOME:-/usr/local/lib/android/sdk}" >> "$GITHUB_ENV"
          echo "ANDROID_SDK_ROOT=${ANDROID_HOME:-/usr/local/lib/android/sdk}" >> "$GITHUB_ENV"

      - name: Install Android SDK packages
        shell: bash
        run: |
          SDK="${ANDROID_HOME:-/usr/local/lib/android/sdk}"
          SDKMANAGER="$(find "$SDK/cmdline-tools" -type f -name sdkmanager 2>/dev/null | sort -V | tail -n 1)"
          if [ -z "$SDKMANAGER" ]; then
            echo "ERROR: sdkmanager not found"
            exit 1
          fi
          yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses || true
          "$SDKMANAGER" --sdk_root="$SDK" \
            "platform-tools" \
            "platforms;android-36" \
            "build-tools;36.0.0"

      - name: Create debug keystore
        shell: bash
        run: |
          keytool -genkeypair -v \
            -keystore debug.keystore \
            -storepass android \
            -alias androiddebugkey \
            -keypass android \
            -dname "CN=Android Debug,O=Android,C=US" \
            -keyalg RSA \
            -keysize 2048 \
            -validity 10000

      - name: Build Debug APK
        shell: bash
        run: gradle --no-daemon assembleDebug

      - name: Upload APK
        if: success()
        uses: actions/upload-artifact@v4
        with:
          name: SaudiInvoiceScanner-APK
          path: app/build/outputs/apk/debug/*.apk
          if-no-files-found: error
          retention-days: 7
