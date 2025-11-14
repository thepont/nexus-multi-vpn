#!/bin/sh
set -e

echo "[entrypoint] Starting OpenVPN test server..."

# Ensure required directories exist
mkdir -p /etc/openvpn/ccd

# Ensure auth script executable (ignore errors if RO mount)
if [ -f /etc/openvpn/auth/checkpsw.sh ]; then
  chmod +x /etc/openvpn/auth/checkpsw.sh 2>/dev/null || true
fi

# Enable IP forwarding (best effort; container may manage via sysctls)
sysctl -w net.ipv4.ip_forward=1 >/dev/null 2>&1 || true

# Disable reverse path filtering to allow asymmetric routing between TUN and LAN
sysctl -w net.ipv4.conf.all.rp_filter=0 >/dev/null 2>&1 || true
sysctl -w net.ipv4.conf.default.rp_filter=0 >/dev/null 2>&1 || true
for path in /proc/sys/net/ipv4/conf/*/rp_filter; do
  echo 0 > "$path" 2>/dev/null || true
done

# Flush existing rules
iptables -F || true
iptables -t nat -F || true

# Allow traffic from VPN subnet to reach other networks
for iface in $(ls /sys/class/net); do
  case "$iface" in
    lo|tun*)
      continue
      ;;
  esac
  iptables -t nat -A POSTROUTING -s 10.240.0.0/12 ! -d 10.240.0.0/12 -o "$iface" -j MASQUERADE || true
done
iptables -A FORWARD -s 10.240.0.0/12 -j ACCEPT || true
iptables -A FORWARD -d 10.240.0.0/12 -j ACCEPT || true

exec openvpn --config /etc/openvpn/server.conf
