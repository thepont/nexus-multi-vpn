FROM alpine:latest

# Install OpenVPN and curl
RUN apk add --no-cache openvpn curl bash

# Create directory for configs
WORKDIR /app

# Copy test script
COPY test-credentials.sh /app/
RUN chmod +x /app/test-credentials.sh

CMD ["/app/test-credentials.sh"]


