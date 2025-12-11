# Use the official linuxserver/wireguard image as base
FROM linuxserver/wireguard:latest

# Set environment variables for client generation
ENV PUID=1000
ENV PGID=1000
ENV TZ=Europe/London
ENV SERVERURL=wireguard-test-server
ENV SERVERPORT=51820
ENV PEERS=androidclient
ENV PEERDNS=auto
ENV INTERNAL_SUBNET=10.13.13.0
ENV ALLOWEDIPS=0.0.0.0/0