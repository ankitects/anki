# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import json
import sys

import pip_system_certs.wrapt_requests
import requests

pip_system_certs.wrapt_requests.inject_truststore()


def main():
    """Fetch and return all versions from PyPI, sorted by upload time."""
    url = "https://pypi.org/pypi/aqt/json"

    try:
        response = requests.get(url, timeout=30)
        response.raise_for_status()
        data = response.json()
        releases = data.get("releases", {})

        # Create list of (version, upload_time) tuples
        version_times = []
        for version, files in releases.items():
            if files:  # Only include versions that have files
                # Use the upload time of the first file for each version
                upload_time = files[0].get("upload_time_iso_8601")
                if upload_time:
                    version_times.append((version, upload_time))

        # Sort by upload time
        version_times.sort(key=lambda x: x[1])

        # Extract just the version names
        versions = [version for version, _ in version_times]
        print(json.dumps(versions))
    except Exception as e:
        print(f"Error fetching versions: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
