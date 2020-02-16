#!/bin/bash

set -eu

echo "$PGP_PRIVATE_KEY" > /tmp/private.key
gpg \
--pinentry-mode=loopback \
--passphrase="$PGP_PASSPHRASE" \
--import \
/tmp/private.key
rm /tmp/private.key

gpg --list-secret-keys
