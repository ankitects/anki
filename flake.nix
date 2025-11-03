{
  description = "Anki - powerful, intelligent flashcards";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; };
      in
      {
        packages.default = pkgs.callPackage ./default.nix { };

        devShells.default = pkgs.mkShell {
          inputsFrom = [ self.packages.${system}.default ];
          shellHook = ''
            echo "======================================"
            echo "⚠️  Remember to build with submodules:"
            echo ""
            echo "   nix build '.?submodules=1'"
            echo "======================================"
          '';
        };
      });
}
