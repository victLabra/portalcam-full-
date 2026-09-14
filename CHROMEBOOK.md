
# Como compilar en Chromebook (sin Android Studio)

## Opción 1 - La más fácil: GitHub en la nube (recomendada para Chromebook)

1. Crea una cuenta gratis en github.com (si no tienes)
2. Crea un repo nuevo: https://github.com/new -> nombre portalcam-full
3. En tu Chromebook, abre la app Archivos, descomprime PortalCam-Full-Grupo.zip
4. Sube todo a GitHub:
   - Entra a tu repo > Add file > Upload files > arrastra todo lo de PortalCam-Full
5. GitHub compila solo:
   - Ve a pestaña Actions > verás "Build PortalCam Full APK" corriendo
   - Espera 3-4 min > cuando termine, entra al workflow > baja en Artifacts > PortalCam-Full-Grupo-APK
   - Ahí está tu app-debug.apk lista para instalar en Portal TV

## Opción 2 - Linux de Chromebook (Crostini)

Tu Chromebook ya tiene Linux:

1. Configuración > Desarrolladores > Entorno de desarrollo Linux > Activar (si no está)
2. Abre Terminal Linux
3. Ejecuta:

sudo apt update
sudo apt install openjdk-17-jdk wget unzip -y
mkdir -p ~/android && cd ~/android
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-*.zip
mkdir -p cmdline-tools/latest
mv cmdline-tools/* cmdline-tools/latest/ 2>/dev/null; true
export ANDROID_HOME=~/android
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
yes | sdkmanager --licenses
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"

cd ~/Downloads
unzip PortalCam-Full-Grupo.zip
cd PortalCam-Full/android
./gradlew assembleDebug

4. APK en app/build/outputs/apk/debug/

## Instalar en Portal TV desde Chromebook

1. Conecta Portal TV por USB-C a tu Chromebook (usa cable de datos, no solo carga)
2. En Portal TV: Ajustes > Acerca de > toca 7 veces "Número de compilación" para activar depuración
3. En Terminal Linux de Chromebook:
adb devices  # debe aparecer tu Portal TV
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start-foreground-service com.portalcam/.UsbCamFullService

¡Listo! Ya tienes seguimiento grupal 4-5 personas + audio por USB.
