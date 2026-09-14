
package com.portalcam

import android.app.Service
import android.content.Intent
import android.graphics.*
import android.hardware.camera2.*
import android.media.*
import android.media.ImageReader
import android.os.IBinder
import android.util.Log
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.common.InputImage
import java.io.*
import java.net.ServerSocket
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class UsbCamFullService : Service() {
    private var running = true
    private var cameraDevice: CameraDevice? = null
    private var imageReaderYuv: ImageReader? = null
    private var imageReaderJpeg: ImageReader? = null
    private val tracker = FaceTracker()
    private var currentCrop = Rect()
    private var latestJpegCropped: ByteArray? = null
    private var audioRecord: AudioRecord? = null

    // MLKit offline (sin GMS)
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .enableTracking()
            .build()
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAudio()
        startCamera()
        thread { startUsbServer() }
        Log.i("PortalCam-Full", "Servicio iniciado con tracking + audio")
        return START_STICKY
    }

    private fun startAudio() {
        try {
            val sampleRate = 48000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            audioRecord = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, sampleRate, channelConfig, audioFormat, minBuf * 2)
            // Activa cancelacion de eco y supresion de ruido si el hardware lo soporta (Portal TV si)
            if (AcousticEchoCanceler.isAvailable()) {
                AcousticEchoCanceler.create(audioRecord!!.audioSessionId)?.enabled = true
            }
            if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(audioRecord!!.audioSessionId)?.enabled = true
            }
            audioRecord?.startRecording()
            Log.i("PortalCam-Full", "Audio mic array activo 48kHz")
        } catch (e: Exception) { Log.e("PortalCam-Full","audio",e) }
    }

    private fun startCamera() {
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        val camId = manager.cameraIdList.first { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        }

        // YUV para MLKit, JPEG para stream final
        imageReaderYuv = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2)
        imageReaderJpeg = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 2)

        imageReaderYuv?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            processFaceDetection(image)
            image.close()
        }, null)

        imageReaderJpeg?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining()); buffer.get(bytes)
            // Aplica crop del tracker si hay caras
            latestJpegCropped = if (currentCrop.width() > 0) cropJpeg(bytes, currentCrop) else bytes
            image.close()
        }, null)

        manager.openCamera(camId, object : CameraDevice.StateCallback() {
            override fun onOpened(c: CameraDevice) {
                cameraDevice = c
                val yuvSurf = imageReaderYuv!!.surface
                val jpegSurf = imageReaderJpeg!!.surface
                c.createCaptureSession(listOf(yuvSurf, jpegSurf), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        val req = c.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                            addTarget(yuvSurf); addTarget(jpegSurf)
                            set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                        }
                        s.setRepeatingRequest(req.build(), null, null)
                    }
                    override fun onConfigureFailed(s: CameraCaptureSession) {}
                }, null)
            }
            override fun onDisconnected(c: CameraDevice) {}
            override fun onError(c: CameraDevice, error: Int) {}
        }, null)
    }

    private fun processFaceDetection(image: Image) {
        try {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer
            // Conversion rapida a Bitmap para MLKit (simplificada)
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize+vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0,0,image.width,image.height), 80, out)
            val bitmap = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
            val input = InputImage.fromBitmap(bitmap, 0)

            detector.process(input).addOnSuccessListener { faces ->
                val rects = faces.map { f ->
                    // f.boundingBox ya esta en coordenadas de la imagen 640x480
                    f.boundingBox
                }
                val tracked = tracker.update(rects, image.width, image.height)
                currentCrop = tracked.cropRect
                Log.d("PortalCam-Full", "Faces=${tracked.faceCount} group=${tracked.isGroup} crop=${tracked.cropRect}")
            }
        } catch (e: Exception) { Log.e("PortalCam-Full","mlkit",e) }
    }

    private fun cropJpeg(jpegBytes: ByteArray, crop: Rect): ByteArray {
        try {
            val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            // Mapea crop de 640x480 a 1280x720
            val scaleX = bmp.width / 640f
            val scaleY = bmp.height / 480f
            val left = (crop.left * scaleX).toInt().coerceIn(0, bmp.width-1)
            val top = (crop.top * scaleY).toInt().coerceIn(0, bmp.height-1)
            val w = (crop.width() * scaleX).toInt().coerceAtMost(bmp.width-left)
            val h = (crop.height() * scaleY).toInt().coerceAtMost(bmp.height-top)
            val cropped = Bitmap.createBitmap(bmp, left, top, w, h)
            val out = ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.JPEG, 85, out)
            return out.toByteArray()
        } catch (e: Exception) { return jpegBytes }
    }

    private fun startUsbServer() {
        val server = ServerSocket(8888)
        val audioServer = ServerSocket(8889)
        Log.i("PortalCam-Full","USB Server 8888 video / 8889 audio listo")

        // Audio thread
        thread {
            while (running) {
                try {
                    val client = audioServer.accept()
                    val out = DataOutputStream(client.getOutputStream())
                    val buf = ByteArray(960) // 20ms @48k
                    while (running) {
                        val read = audioRecord?.read(buf, 0, buf.size) ?: 0
                        if (read > 0) {
                            out.writeInt(read)
                            out.write(buf, 0, read)
                        }
                    }
                } catch (e: Exception) {}
            }
        }

        // Video thread
        while (running) {
            try {
                val client = server.accept()
                thread {
                    val out = DataOutputStream(client.getOutputStream())
                    while (running && client.isConnected) {
                        val jpeg = latestJpegCropped ?: continue
                        out.writeInt(jpeg.size)
                        out.write(jpeg)
                        out.flush()
                        Thread.sleep(33)
                    }
                }
            } catch (e: Exception) {}
        }
    }

    override fun onDestroy() {
        running = false
        cameraDevice?.close()
        audioRecord?.stop(); audioRecord?.release()
        super.onDestroy()
    }
}
