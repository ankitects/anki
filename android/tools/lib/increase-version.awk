#!/usr/bin/gawk -f
#
# Increase version string
# For instance 12.4alpha9 -> 12.4alpha10
#

1 {
    prefix = gensub(/^(.*[^0-9])([0-9]+)$/, "\\1", "1")
    testingversion = gensub(/^(.*[^0-9])([0-9]+)$/, "\\2", "1")

    next_testingversion = (1*testingversion)+1
    printf "%s%s\n", prefix, next_testingversion
}
