#!/bin/sh
USERNAME=$(echo "$username")
PASSWORD=$(echo "$password")
if [ "$USERNAME" = "testuser" ] && [ "$PASSWORD" = "testpass" ]; then
  exit 0
fi
exit 1
