#!/bin/bash

if [ "$KEYSTOREPATH" == "" ]; then
  read -rsp "Enter keystore path: " KEYSTOREPATH; echo
  export KEYSTOREPATH
fi

if [ "$KEYSTOREPWD" == "" ]; then
  read -rsp "Enter keystore password: " KEYSTOREPWD; echo
  export KEYSTOREPWD
fi

if [ "$KEYALIAS" == "" ]; then
  read -rsp "Enter key alias: " KEYALIAS; echo
  export KEYALIAS
fi

if [ "$KEYPWD" == "" ]; then
  read -rsp "Enter key password: " KEYPWD; echo
  export KEYPWD
fi