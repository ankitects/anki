#!/bin/bash

rsync -av --delete $c/libanki $c/ankiqt mari:anki/
ssh mari "open anki/ankiqt/anki"
