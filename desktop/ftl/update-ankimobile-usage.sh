#!/bin/bash
# This script can only be run by Damien, as it requires a copy of AnkiMobile's sources.

cargo run --bin write_ftl_json ftl/usage/ankimobile.json ../../mobile/ankimobile/src
