#!/bin/bash
# Compila APK PortalCam Full Group en tu PC
# Requisitos: Android Studio + JDK 17

echo "[*] PortalCam Full Group - Build"
echo "[*] Afinado para 4-5 personas"

if ! command -v gradle &> /dev/null; then
  echo "Usando gradlew..."
  chmod +x ./gradlew
  ./gradlew :app:assembleDebug
else
  gradle assembleDebug
fi

echo "[*] APK en app/build/outputs/apk/debug/app-debug.apk"
echo "[*] Instalar:"
echo "  adb connect IP_PORTAL_TV:5555"
echo "  adb install -r app/build/outputs/apk/debug/app-debug.apk"
echo "  adb shell am startservice com.portalcam/.UsbCamFullService"
