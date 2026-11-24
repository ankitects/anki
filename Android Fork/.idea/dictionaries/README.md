# Dictionary Management for AnkiDroid

## Introduction

This guide addresses the issue of managing custom dictionaries within JetBrains IDE -Android Studio. Often, when adding a word to the dictionary within the IDE, it remains user-specific and doesn't propagate across a team. 
This README explores a workaround for better collaboration [regarding custom dictionaries](https://www.jetbrains.com/help/idea/spellchecking.html) in the IDE.

## Implementation Steps

### 1. Understanding Directory Structure

- The directory `idea/dictionaries` within the project root holds user-specific dictionary files.
- Each user's added words are stored in a file named after their OS login username (e.g., catio.xml for a user named "catio") and this file is shared within the project.
- anki.xml - Anki Desktop specific terminology: 'did' 
- android.xml - Android and dependencies: 'miui.securitycenter' 
- usernames.xml - Usernames in copyright declarations

### 2. Leveraging Shared Dictionary Files

- Create a dictionary file formatted as an XML (similar to the user-specific ones) containing shared project-specific words.
- Naming this file differently (not tied to a username) allows reuse across projects sharing the same terminology.

### 3. Managing Collaborative Editing

- When multiple developers commit their dictionary files, potential word duplications may occur.
- Manual editing of these XML files is necessary, as the IDE doesn't provide direct options to write to a shared dictionary.
- Syntax: Use the XML structure specified in the example below to manually craft or edit dictionary files.

### Note: Restart the IDE after editing the file or adding the file for the changes to take place

### Example Dictionary File Structure

```xml
<component name="ProjectDictionaryState">
    <dictionary name="project-dictionary">
        <words>
            <w>someword</w>
        </words>
    </dictionary>
</component>
```

### Best Practice for VCS Integration

- Consider adding manually created shared dictionary files to the VCS, while ignoring the rest of `idea/dictionaries`.
- This prevents automatic addition of words from individual IDEs to the shared dictionary, minimizing unnecessary duplications.