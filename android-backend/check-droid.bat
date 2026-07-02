if not exist rsdroid\build\outputs\aar\rsdroid-release.aar (
    echo "Run ./build.bat first"
    exit 1
)

./gradlew rsdroid:lint rsdroid-instrumented:connectedCheck
