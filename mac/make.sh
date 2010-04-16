#!/bin/bash

rsync -av --delete $c/libanki $c/ankiqt mari:anki/
ssh mari 'cd anki && ./make.sh && scp Anki.dmg twitch:'
