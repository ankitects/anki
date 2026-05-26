set windows-shell := ["cmd.exe", "/c"]

version := `cat .version`

# Prepare a release: validate version, check CI, update .version
[arg("version", long)]
[arg("ref", long)]
[arg("skip-ci-check", long)]
prepare version ref skip-ci-check="false":
    gh workflow run prepare-release.yml --ref {{ ref }} \
        -f version="{{ version }}" \
        -f skip-ci-check={{ skip-ci-check }}

# Build all platforms, no signing or publishing
[arg("ref", long)]
build ref:
    gh workflow run release.yml --ref {{ ref }} \
        -f version="{{ version }}" \
        -f sign=false \
        -f draft-release=false \
        -f publish-testpypi=false \
        -f publish-pypi=false

# Build and sign artifacts
[arg("ref", long)]
sign ref:
    gh workflow run release.yml --ref {{ ref }} \
        -f version="{{ version }}" \
        -f sign=true \
        -f draft-release=false \
        -f publish-testpypi=false \
        -f publish-pypi=false

# Full public release: sign, draft, testpypi, pypi
[arg("ref", long)]
public ref:
    gh workflow run release.yml --ref {{ ref }} \
        -f version="{{ version }}" \
        -f sign=true \
        -f draft-release=true \
        -f publish-testpypi=true \
        -f publish-pypi=true

# Publish Python packages to TestPyPI only
[arg("ref", long)]
testpypi ref:
    gh workflow run release.yml --ref {{ ref }} \
        -f version="{{ version }}" \
        -f sign=false \
        -f draft-release=false \
        -f publish-testpypi=true \
        -f publish-pypi=false

# Publish Python packages to TestPyPI and PyPI
[arg("ref", long)]
pypi ref:
    gh workflow run release.yml --ref {{ ref }} \
        -f version="{{ version }}" \
        -f sign=false \
        -f draft-release=false \
        -f publish-testpypi=true \
        -f publish-pypi=true

# Manual release with explicit flags
[arg("ref", long)]
[arg("sign", long)]
[arg("draft-release", long)]
[arg("publish-testpypi", long)]
[arg("publish-pypi", long)]
[arg("skip-ci-check", long)]
custom ref sign="false" draft-release="false" publish-testpypi="false" publish-pypi="false" skip-ci-check="false":
    gh workflow run release.yml --ref {{ ref }} \
        -f version="{{ version }}" \
        -f sign={{ sign }} \
        -f draft-release={{ draft-release }} \
        -f publish-testpypi={{ publish-testpypi }} \
        -f publish-pypi={{ publish-pypi }} \
        -f skip-ci-check={{ skip-ci-check }}
