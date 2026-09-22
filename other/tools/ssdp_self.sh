#!/system/bin/sh
# SSDP M-SEARCH self-probe on the TV (same L2 as other 10.0.0.x clients).
# Usage: adb shell sh /sdcard/ssdp_self.sh
MSG='M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: "ssdp:discover"\r\nMX: 2\r\nST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n'
echo "$MSG" | nc -u -w 3 239.255.255.250 1900 &
sleep 1
# Also listen briefly for responses (best-effort; toybox nc may not support -l on udp well)
echo "$MSG" | nc -u -w 2 239.255.255.250 1900
echo SSDP_SENT
