#!/bin/sh
# Connect a phone to OpenCode on this computer with a QR code.
# Starts `opencode serve` on the local network with a new password and shows a QR code:
# in cl0udly, Remote devices → Add device → Scan QR code (or point the phone camera at it).
# The phone and computer must be on the same network. Stop with Ctrl+C.
#
#   curl -fsSL https://raw.githubusercontent.com/mistqkw/opencode-mobile/main/tools/connect-qr.sh | sh
#   PORT=4500 NAME=Laptop sh connect-qr.sh
set -e

PORT="${PORT:-4096}"
NAME="${NAME:-$(hostname 2>/dev/null || echo Computer)}"

# cl0udly talks to an OpenCode 1.x server (like the one in the phone container). If another
# branch (2.x) is installed here, or no OpenCode at all, download 1.19.4 once into the cache.
OC_V=1.19.4
OC=""
if command -v opencode >/dev/null 2>&1 && opencode --version 2>/dev/null | grep -Eq '(^|[^0-9])1\.'; then
  OC=opencode
else
  CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/cl0udly"
  DIR="$CACHE/opencode-$OC_V"
  case "$(uname -s)-$(uname -m)" in
    Linux-x86_64) ASSET=opencode-linux-x64.tar.gz ;;
    Linux-aarch64|Linux-arm64) ASSET=opencode-linux-arm64.tar.gz ;;
    Darwin-arm64) ASSET=opencode-darwin-arm64.zip ;;
    Darwin-x86_64) ASSET=opencode-darwin-x64.zip ;;
    *) echo "No OpenCode $OC_V build for $(uname -s) $(uname -m)."; exit 1 ;;
  esac
  if [ ! -x "$DIR/opencode" ]; then
    echo "Downloading OpenCode $OC_V for cl0udly (once, ~75 MB)…"
    mkdir -p "$DIR"
    TMP="$DIR/$ASSET.part"
    curl -fL --progress-bar -o "$TMP" "https://github.com/anomalyco/opencode/releases/download/v$OC_V/$ASSET"
    case "$ASSET" in
      *.zip) (cd "$DIR" && unzip -oq "$TMP") ;;
      *) tar -xzf "$TMP" -C "$DIR" ;;
    esac
    rm -f "$TMP"
    chmod +x "$DIR/opencode"
  fi
  OC="$DIR/opencode"
  # separate chats and database: don't interfere with the installed OpenCode of another version
  export XDG_DATA_HOME="$CACHE/data"
fi

# this computer's address on the local network
IP=""
if command -v ip >/dev/null 2>&1; then
  IP=$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{for (i = 1; i < NF; i++) if ($i == "src") { print $(i + 1); exit }}')
fi
[ -z "$IP" ] && command -v ipconfig >/dev/null 2>&1 && IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || true)
[ -z "$IP" ] && IP=$(hostname -I 2>/dev/null | awk '{print $1}')
if [ -z "$IP" ]; then
  echo "Couldn't find this computer's address on the local network."
  exit 1
fi

# a new password on every run
PASS=$(od -An -N12 -tx1 /dev/urandom | tr -d ' \n')

# the name may contain spaces and such: encode it for the link
enc() { printf '%s' "$1" | od -An -tx1 -v | tr -d '\n' | sed 's/ \{1,\}/%/g; s/^%//; s/^/%/'; }
LINK="cl0udly://connect?n=$(enc "$NAME")&u=$(enc "http://$IP:$PORT")&p=$PASS"

echo
if command -v qrencode >/dev/null 2>&1; then
  qrencode -t ansiutf8 -m 2 "$LINK"
elif command -v python3 >/dev/null 2>&1 && python3 -c 'import qrcode' 2>/dev/null; then
  python3 -c 'import sys, qrcode; q = qrcode.QRCode(border=2); q.add_data(sys.argv[1]); q.print_ascii(invert=True)' "$LINK"
else
  echo "(Install 'qrencode' to see a QR code here. Meanwhile, enter the details by hand.)"
fi
echo
echo "  cl0udly → Remote devices → Add device → Scan QR code"
echo "  Or by hand: address $IP:$PORT, password $PASS"
echo "  Stop with Ctrl+C."
# an active firewall blocks the port for the phone
if command -v firewall-cmd >/dev/null 2>&1 && firewall-cmd --state >/dev/null 2>&1; then
  echo "  Firewall is on. If the phone can't connect: sudo firewall-cmd --add-port=$PORT/tcp"
elif command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q "Status: active"; then
  echo "  Firewall is on. If the phone can't connect: sudo ufw allow $PORT/tcp"
fi
echo

OPENCODE_SERVER_PASSWORD="$PASS" exec "$OC" serve --hostname 0.0.0.0 --port "$PORT"
