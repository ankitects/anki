set windows-shell := ["cmd.exe", "/c"]

version := `cat .version`

# Build all platforms, no signing or publishing
build ref="HEAD":
    gh workflow run release.yml --ref {{ ref }} \
        -f version="{{ version }}" \
        -f sign=false \
        -f draft-release=false \
        -f publish-testpypi=false \
        -f publish-pypi=false

# Build and sign artifacts
sign ref="HEAD":
    gh workflow run release.yml --ref {{ ref }} \
        -f version="{{ version }}" \
        -f sign=true \
        -f draft-release=false \
        -f publish-testpypi=false \
        -f publish-pypi=false

# Full public release: sign, draft, testpypi, pypi
public:
    gh workflow run release.yml --ref main \
        -f version="{{ version }}" \
        -f sign=true \
        -f draft-release=true \
        -f publish-testpypi=true \
        -f publish-pypi=true

# Custom release with explicit flags
custom ref="HEAD" sign="false" draft-release="false" publish-testpypi="false" publish-pypi="false":
    gh workflow run release.yml --ref {{ ref }} \
        -f version="{{ version }}" \
        -f sign={{ sign }} \
        -f draft-release={{ draft-release }} \
        -f publish-testpypi={{ publish-testpypi }} \
        -f publish-pypi={{ publish-pypi }}
