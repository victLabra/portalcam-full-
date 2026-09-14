#!/system/bin/sh
# True UVC + UAC2 gadget para Portal TV Full con audio
mount -t configfs none /config 2>/dev/null
mkdir -p /config/usb_gadget/g1
cd /config/usb_gadget/g1
echo 0x18d1 > idVendor
echo 0x4e11 > idProduct
mkdir -p strings/0x409
echo "PortalCam" > strings/0x409/manufacturer
echo "Portal TV Full Webcam+Mic" > strings/0x409/product
mkdir -p configs/c.1/strings/0x409
echo "UVC+UAC" > configs/c.1/strings/0x409/configuration
mkdir -p functions/uvc.0
mkdir -p functions/uac2.0
echo 48000 > functions/uac2.0/c_srate
echo 1 > functions/uac2.0/c_chmask
ln -s functions/uvc.0 configs/c.1/
ln -s functions/uac2.0 configs/c.1/
UDC=$(ls /sys/class/udc | head -n1)
echo $UDC > UDC
echo "[*] UVC+UAC gadget activo $UDC - Aparece como webcam+microfono USB"
