FROM alpine:3.19

RUN apk add --no-cache openvpn iptables bash iptables-legacy && \
    ln -sf /sbin/iptables-legacy /sbin/iptables && \
    ln -sf /sbin/ip6tables-legacy /sbin/ip6tables

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

CMD ["/entrypoint.sh"]
