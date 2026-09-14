#!/usr/bin/env python3
# PortalCam Full - Video + Audio + Tracking por USB
import socket, struct, subprocess, time, cv2, numpy as np, threading, queue

print("[*] PortalCam Full - Facial / Grupal / Audio por USB")
subprocess.run(["adb", "reverse", "tcp:8888", "tcp:8888"], check=False)
subprocess.run(["adb", "reverse", "tcp:8889", "tcp:8889"], check=False)
time.sleep(1)

# Video socket
vsock = socket.socket()
vsock.connect(("127.0.0.1", 8888))
# Audio socket
asock = socket.socket()
try:
    asock.connect(("127.0.0.1", 8889))
    has_audio = True
    print("[*] Audio USB conectado")
except:
    has_audio = False
    print("[!] Audio no conectado, solo video")

try:
    import pyvirtualcam
    vcam = pyvirtualcam.Camera(width=1280, height=720, fps=30, fmt=pyvirtualcam.PixelFormat.BGR)
    print(f"[*] Webcam virtual: {vcam.device}")
except:
    vcam = None
    print("[!] pyvirtualcam no instalado")

# Audio playback to virtual mic
audio_q = queue.Queue()
def audio_thread():
    try:
        import sounddevice as sd
        # Intenta crear virtual mic - en Linux usa PulseAudio null sink
        # Para Windows: instala VB-Audio Cable y selecciona como salida
        def callback(outdata, frames, time, status):
            try:
                data = audio_q.get_nowait()
                # data es PCM16 mono 48k
                pcm = np.frombuffer(data, dtype=np.int16).astype(np.float32) / 32768.0
                # upmix a stereo si hace falta
                if len(pcm) < frames:
                    pcm = np.pad(pcm, (0, frames-len(pcm)))
                outdata[:,0] = pcm[:frames]
            except:
                outdata.fill(0)
        with sd.OutputStream(samplerate=48000, channels=1, callback=callback):
            while True:
                time.sleep(1)
    except Exception as e:
        print(f"[!] Audio playback no disponible: {e}. Audio se guardara en archivo")
        # fallback: guarda a wav
        import wave
        wf = wave.open("portalcam_audio.wav","wb")
        wf.setnchannels(1); wf.setsampwidth(2); wf.setframerate(48000)
        while True:
            data = audio_q.get()
            wf.writeframes(data)

threading.Thread(target=audio_thread, daemon=True).start()

def recv_loop(sock):
    while True:
        data = sock.recv(1024*1024)
        if not data: break
        audio_q.put(data)

if has_audio:
    threading.Thread(target=lambda: recv_loop_audio(), daemon=True).start()
    def recv_loop_audio():
        while True:
            raw_len = asock.recv(4)
            if not raw_len: break
            alen = struct.unpack('>I', raw_len)[0]
            if alen > 10000: alen = struct.unpack('<I', raw_len)[0]
            adata = b''
            while len(adata) < alen:
                adata += asock.recv(alen-len(adata))
            audio_q.put(adata)
    threading.Thread(target=recv_loop_audio, daemon=True).start()

def recvall(sock, n):
    d=b''
    while len(d)<n:
        p=sock.recv(n-len(d))
        if not p: return None
        d+=p
    return d

print("[*] Streaming - seguimiento facial activo en TV")
cv2.namedWindow("PortalCam Full", cv2.WINDOW_NORMAL)
while True:
    raw_len = recvall(vsock, 4)
    if not raw_len: break
    jlen = struct.unpack('>I', raw_len)[0]
    if jlen>5000000: jlen=struct.unpack('<I', raw_len)[0]
    jdata = recvall(vsock, jlen)
    if not jdata: break
    frame = cv2.imdecode(np.frombuffer(jdata, np.uint8), cv2.IMREAD_COLOR)
    if frame is None: continue
    frame = cv2.resize(frame, (1280,720))
    # overlay info tracking
    cv2.putText(frame, f"PortalCam Full - Seguimiento IA activo", (20,40), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0,255,0), 2)
    cv2.imshow("PortalCam Full", frame)
    if vcam:
        vcam.send(frame)
        vcam.sleep_until_next_frame()
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break
