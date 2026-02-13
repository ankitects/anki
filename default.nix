{ fetchFromGitHub, anki, lib, rustPlatform, yarn-berry, rsync }:

let
  version = "local";
  src = ./.;

  # Sanity check: verify git submodules are included
  checkSubmodules =
    if !builtins.pathExists "${src}/ftl/core-repo/core" then
      throw ''
        ❌ Git submodules are missing from the source!

        The ftl/core-repo and ftl/qt-repo submodules are required for building.

        Fix: Build with the submodules parameter:
          nix build '.?submodules=1'
      ''
    else true;
in assert checkSubmodules; anki.overrideAttrs (oldAttrs: {

  inherit version src;

  patches = lib.filter (patch: !lib.hasInfix "rust-1.89" (toString patch))
    oldAttrs.patches;

  cargoDeps = rustPlatform.importCargoLock {
    lockFile = "${src}/Cargo.lock";
    outputHashes = {
      "linkcheck-0.4.1" = "sha256-S93J1cDzMlzDjcvz/WABmv8CEC6x78E+f7nzhsN7NkE=";
      "percent-encoding-iri-2.2.0" =
        "sha256-kCBeS1PNExyJd4jWfDfctxq6iTdAq69jtxFQgCCQ8kQ=";
    };
  };

  yarnOfflineCache = yarn-berry.fetchYarnBerryDeps {
    yarnLock = "${src}/yarn.lock";
    hash = "sha256-lxRdOFDdNsNvsd4UMZZoES4En4EGOr1nGLKV/QyWahs=";
    missingHashes = ./missingHashes.json;
  };
})
