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
            echo ""
            echo "======================================"
            echo "📋 Git workflow for upstream PRs:"
            echo ""
            echo "   1. Keep 'nix' branch updated:"
            echo "      git checkout nix"
            echo "      git fetch upstream"
            echo "      git rebase upstream/main"
            echo ""
            echo "   2. Develop on branches off 'nix'"
            echo ""
            echo "   3. To submit upstream:"
            echo "      git checkout -b feature-upstream upstream/main"
            echo "      git cherry-pick <commits...>"
            echo "      git push origin feature-upstream"
            echo "======================================"
          '';
        };
      });
}
