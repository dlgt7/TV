#!/usr/bin/env python3
"""SSDP M-SEARCH probe for UPnP MediaRenderer / MediaServer on the LAN."""
import socket
import sys
import time

ST = sys.argv[1] if len(sys.argv) > 1 else "urn:schemas-upnp-org:device:MediaRenderer:1"
msg = (
    "M-SEARCH * HTTP/1.1\r\n"
    "HOST: 239.255.255.250:1900\r\n"
    'MAN: "ssdp:discover"\r\n'
    "MX: 2\r\n"
    f"ST: {ST}\r\n"
    "\r\n"
).encode()

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
try:
    sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 2)
except Exception:
    pass
sock.bind(("", 0))
sock.settimeout(3.0)
sock.sendto(msg, ("239.255.255.250", 1900))
print(f"sent M-SEARCH ST={ST}")
end = time.time() + 3.5
seen = set()
while time.time() < end:
    try:
        data, addr = sock.recvfrom(2048)
    except socket.timeout:
        break
    key = (addr[0], data[:80])
    if key in seen:
        continue
    seen.add(key)
    text = data.decode("utf-8", "replace")
    print("---", addr[0], "---")
    for line in text.splitlines()[:12]:
        print(line)
print(f"unique responses: {len(seen)}")
