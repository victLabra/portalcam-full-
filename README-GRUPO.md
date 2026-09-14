
# PortalCam Full - Grupo Grande 4-5 personas

Este build esta afinado para reunion familiar:

Cambios vs version normal:
- Padding 35% para 5 personas, 30% para 4, para que no corte a nadie en los bordes
- Zoom max 0.85-1.0 en grupo grande = casi full sensor, mantiene a todos
- Centro Y 0.48 (ligeramente arriba) para no cortar cabezas
- Suavizado lento 0.08 para grupo (vs 0.18 individual) = no tiembla si alguien se mueve
- Crop baja 5% en grupo para aire arriba

Para compilar APK:
  En Android Studio: Build > Make Project
  O: bash build-apk.sh

El APK queda en app/build/outputs/apk/debug/app-debug.apk

Instalar en Portal TV:
  Provision-Portal.bat (Immortal) ya debe estar instalado
  adb install -r app-debug.apk
  adb shell am start-foreground-service com.portalcam/.UsbCamFullService

Luego en PC:
  python pc_client/portalcam-full.py -> crea webcam virtual con tracking grupal
