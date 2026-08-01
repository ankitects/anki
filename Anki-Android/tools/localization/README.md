# Localization Tool

This project provides a localization tool built with Yarn, built specifically to interact with the crowdin API via their javascript API client

Its purpose is to provide a way to upload our English-language localization files to crowdin so that translators may do their work, and then to download that translation work and faithfully copy it into the non-English Android resource locations.

## Getting Started

Follow these steps to set up and run the localization tool.

```bash
# Move to the proper directory
cd ./tools/localization
# Install the javascript dependencies
yarn
# Build the project so it is ready to run
yarn build
```

## Commands

The following commands are available. Run them from the `./tools/localization` directory:

### Upload

Uploads English `res/values` files to Crowdin.

> yarn start upload

### Download

Builds and downloads all translations from Crowdin.

> yarn start download

### Extract

Extracts files from `ankidroid.zip` into an internal staging area

> yarn start extract

### Update

Updates the files from the extracted `ankidroid.zip` file by processing them lightly (adding copyright information, fixing some common errors, etc), then copying them into the correct Android resource folders for the translated languages

> yarn start update

## Build / Execution Notes

The project is implemented in typescript, which must be transpiled into javascript before it may be executed by the node interpreter.

To transpile the project run:

> yarn build

...or alternatively if you are actively developing the project, you may wish to have the code transpiled for testing on any change, this is possible with

> yarn dev

...which starts the `tsc` typescript compiler in `--watch` mode

After building the project, you may use the `package.json` run scripts, or the following commands can also be used if you want to execute the transpiled scripts directly:

```bash
node .\dist\index.js upload
node .\dist\index.js download
node .\dist\index.js extract
node .\dist\index.js update
```
