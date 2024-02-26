#!/bin/bash


if [ -n "$SYNC_USERS" ]; then
    # split provided var on space
    IFS=' ' read -r -a USERS <<< "$SYNC_USERS"
    
    for ((i = 0; i < ${#USERS[@]}; i++)); do
        export "SYNC_USER$((i+1))=${USERS[i]}"
    done
else
    export SYNC_USER1="admin:admin"
fi

exec "$@"