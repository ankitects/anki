#!/bin/bash

set -e

xcrun actool --app-icon AppIcon $(pwd)/Assets.xcassets --compile . --platform macosx --minimum-deployment-target 13.0 --target-device mac --output-partial-info-plist /dev/null
