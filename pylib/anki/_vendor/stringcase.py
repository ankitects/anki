# stringcase 1.2.0 with python warning fix applied
# MIT: https://github.com/okunishinishi/python-stringcase

branch_coverage_instr1 = {
    "capitalcase_1": False, 
    "capitalcase_2": False,
}

branch_coverage_instr2 = {
    "camelcase_1": False,
    "camelcase_2": False   
}

"""
String convert functions
"""

import re


def camelcase(string):
    """Convert string into camel case.

    Args:
        string: String to convert.

    Returns:
        string: Camel case string.

    """

    string = re.sub(r"\w[\s\W]+\w", "", str(string))
    if not string:
        branch_coverage_instr2["camelcase_1"] = True
        return string
    branch_coverage_instr2["camelcase_2"] = True
    return lowercase(string[0]) + re.sub(
        r"[\-_\.\s]([a-z])", lambda matched: uppercase(matched.group(1)), string[1:]
    )

def print_coverage_2():
    for branch, hit in branch_coverage_instr2.items():
        print(f"{branch} was {'hit' if hit else 'not hit'}")


def capitalcase(string):
    """Convert string into capital case.
    First letters will be uppercase.

    Args:
        string: String to convert.

    Returns:
        string: Capital case string.

    """

    string = str(string)
    if not string:
        branch_coverage_instr1["capitalcase_1"] = True
        return string
    branch_coverage_instr1["capitalcase_2"] = True
    return uppercase(string[0]) + string[1:]

def print_coverage_1():
    for branch, hit in branch_coverage_instr1.items():
        print(f"{branch} was {'hit' if hit else 'not hit'}")


def constcase(string):
    """Convert string into upper snake case.
    Join punctuation with underscore and convert letters into uppercase.

    Args:
        string: String to convert.

    Returns:
        string: Const cased string.

    """

    return uppercase(snakecase(string))


def lowercase(string):
    """Convert string into lower case.

    Args:
        string: String to convert.

    Returns:
        string: Lowercase case string.

    """

    return str(string).lower()


def pascalcase(string):
    """Convert string into pascal case.

    Args:
        string: String to convert.

    Returns:
        string: Pascal case string.

    """

    return capitalcase(camelcase(string))


def pathcase(string):
    """Convert string into path case.
    Join punctuation with slash.

    Args:
        string: String to convert.

    Returns:
        string: Path cased string.

    """
    string = snakecase(string)
    if not string:
        return string
    return re.sub(r"_", "/", string)


def backslashcase(string):
    """Convert string into spinal case.
    Join punctuation with backslash.

    Args:
        string: String to convert.

    Returns:
        string: Spinal cased string.

    """
    str1 = re.sub(r"_", r"\\", snakecase(string))

    return str1
    # return re.sub(r"\\n", "", str1))  # TODO: make regex for \t ...


def sentencecase(string):
    """Convert string into sentence case.
    First letter capped and each punctuations are joined with space.

    Args:
        string: String to convert.

    Returns:
        string: Sentence cased string.

    """
    joiner = " "
    string = re.sub(r"[\-_\.\s]", joiner, str(string))
    if not string:
        return string
    return capitalcase(
        trimcase(
            re.sub(
                r"[A-Z]", lambda matched: joiner + lowercase(matched.group(0)), string
            )
        )
    )


def snakecase(string):
    """Convert string into snake case.
    Join punctuation with underscore

    Args:
        string: String to convert.

    Returns:
        string: Snake cased string.

    """

    string = re.sub(r"[\-\.\s]", "_", str(string))
    if not string:
        return string
    return lowercase(string[0]) + re.sub(
        r"[A-Z]", lambda matched: "_" + lowercase(matched.group(0)), string[1:]
    )


def spinalcase(string):
    """Convert string into spinal case.
    Join punctuation with hyphen.

    Args:
        string: String to convert.

    Returns:
        string: Spinal cased string.

    """

    return re.sub(r"_", "-", snakecase(string))


def dotcase(string):
    """Convert string into dot case.
    Join punctuation with dot.

    Args:
        string: String to convert.

    Returns:
        string: Dot cased string.

    """

    return re.sub(r"_", ".", snakecase(string))


def titlecase(string):
    """Convert string into sentence case.
    First letter capped while each punctuations is capitalsed
    and joined with space.

    Args:
        string: String to convert.

    Returns:
        string: Title cased string.

    """

    return " ".join([capitalcase(word) for word in snakecase(string).split("_")])


def trimcase(string):
    """Convert string into trimmed string.

    Args:
        string: String to convert.

    Returns:
        string: Trimmed case string
    """

    return str(string).strip()


def uppercase(string):
    """Convert string into upper case.

    Args:
        string: String to convert.

    Returns:
        string: Uppercase case string.

    """

    return str(string).upper()


def alphanumcase(string):
    """Cuts all non-alphanumeric symbols,
    i.e. cuts all expect except 0-9, a-z and A-Z.

    Args:
        string: String to convert.

    Returns:
        string: String with cut non-alphanumeric symbols.

    """
    # return filter(str.isalnum, str(string))
    return re.sub(r"\W+", "", string)

result = capitalcase("")
print_coverage_1()

branch_coverage_instr1["capitalcase_1"] = False
branch_coverage_instr1["capitalcase_2"] = False

result = capitalcase("software")
print_coverage_1()

result = camelcase("")
print_coverage_2()

branch_coverage_instr2["camelcase_1"] = False
branch_coverage_instr2["camelcase_2"] = False

result = camelcase("software_eng_pr")
print_coverage_2()