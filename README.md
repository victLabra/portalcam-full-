
# PortalCam Full - Seguimiento Facial + Grupal + Audio por USB

Esta version replica Smart Camera original de Portal TV:

- Deteccion facial con MLKit offline (sin GMS, funciona en Portal TV API 28/29)
- FaceTracker: suavizado exponencial, evita temblor
- Modo individual: zoom 0.55x close-up centrado en cara
- Modo grupal: si detecta >1 cara, calcula bounding box grupal y hace zoom out para incluir a todos
- Audio: AudioRecord VOICE_COMMUNICATION 48kHz + AcousticEchoCanceler + NoiseSuppressor (usa el array de micros de Portal TV)

Transporte 100% por cable USB:
 adb reverse tcp:8888 tcp:8888  (video)
 adb reverse tcp:8889 tcp:8889  (audio)

PC crea webcam virtual + micro virtual.

Instalacion Android:
 - dependencies: com.google.mlkit:face-detection:16.1.7
 - minSdk 28 targetSdk 29
 - Permisos CAMERA, RECORD_AUDIO
 - Inicia UsbCamFullService

PC:
 pip install pyvirtualcam opencv-python sounddevice numpy
 python pc_client/portalcam-full.py

True UVC+UAC:
 Si tienes root, sh enable-uvc-full.sh convierte la Portal TV en webcam+microfono USB estandar plug&play sin drivers.
