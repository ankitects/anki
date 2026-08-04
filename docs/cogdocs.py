import re


def get_file_contents(file_path):
    file_path = f"docs-site/developers/{file_path}.mdx"
    with open(file_path, "r", encoding="utf-8") as f:
        text = "".join(f.readlines()[3:]) + "\n"  # Skip the first three lines (front matter)

        def rewrite_link(match):
            target = match.group(1)

            # Keep already-absolute links untouched.
            if re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", target):
                return f"]({target})"

            # Resolve same-page anchors against the current document.
            if target.startswith("#"):
                target = f"/{file_path.split('/')[-1].removesuffix('.mdx')}{target}"
            elif target.startswith("./"):
                target = target[1:]
            elif not target.startswith("/"):
                target = f"/{target}"

            return f"](https://anki.mintlify.app{target})"

        # Convert relative links to absolute links.
        text = re.sub(r"\]\(([^)]+)\)", rewrite_link, text)
        return text