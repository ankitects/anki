set windows-shell := ["cmd.exe", "/c"]

version := `cat .version`

# Prepare a release: validate version, check CI, sync translations, update .version
prepare version ref="HEAD" skip-ci-check="false":
    gh workflow run prepare-release.yml --ref {{ ref }} \
        -f version="{{ version }}" \
        -f skip-ci-check={{ skip-ci-check }}

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
public ref="main":
    gh workflow run release.yml --ref {{ ref }} \
        -f version="{{ version }}" \
        -f sign=true \
        -f draft-release=true \
        -f publish-testpypi=true \
        -f publish-pypi=true

# Custom release with explicit flags
custom ref="HEAD" sign="false" draft-release="false" publish-testpypi="false" publish-pypi="false" skip-ci-check="false":
    gh workflow run release.yml --ref {{ ref }} \
        -f version="{{ version }}" \
        -f sign={{ sign }} \
        -f draft-release={{ draft-release }} \
        -f publish-testpypi={{ publish-testpypi }} \
        -f publish-pypi={{ publish-pypi }} \
        -f skip-ci-check={{ skip-ci-check }}
