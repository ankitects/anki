#!/usr/bin/gawk -f
#
# Increase version string
# For instance 12.4alpha9 -> 12.4alpha10
#

1 {
    # strip the numeric suffix, obtaining '12.4alpha'
    prefix = gensub(/[0-9]+$/, "", "1")
    testingversion = substr($0, length(prefix) + 1)

    next_testingversion = (1*testingversion)+1
    printf "%s%s\n", prefix, next_testingversion
}
