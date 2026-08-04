def get_file_contents(file_path):
    file_path = f"docs-site/developers/{file_path}.mdx"
    with open(file_path, "r", encoding="utf-8") as f:
        return "".join(f.readlines()[3:]) + "\n"  # Skip the first three lines (front matter)