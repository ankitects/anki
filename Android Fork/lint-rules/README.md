# Lint Rules Module

This module contains custom Android lint checks for AnkiDroid.

## How to add a new lint check

1. Create a new .kt file in com.ichi2.anki.lint/
2. Create a class that extends Detector
3. Define an Issue object with:
   - id - unique identifier
   - briefDescription - short summary
   - explanation - detailed description
   - category - use constants from Category class
   - priority - use constants from Priority class
   - severity - use constants from Severity class
4. Add the issue to IssueRegistry.kt

See DirectDateFormatDetector.kt for a small example.
Link: https://github.com/ankidroid/Anki-Android/blob/main/lint-rules/src/main/java/com/ichi2/anki/lint/DirectDateFormatDetector.kt

## How to run checks locally

### Terminal

./gradlew :lint-rules:assemble

This refreshes the checks in the IDE. Alternatively, run ./gradlew lint to check the whole codebase.

### Android Studio

1. Make changes in lint-rules/
2. Build -> Rebuild Project
3. Restart Android Studio
4. Analyze -> Run Inspection by Name -> Type "Lint"

## Documentation

Custom Lint Rules Documentation: https://github.com/googlesamples/android-custom-lint-rules
