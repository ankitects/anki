from typing import List, Dict

import typing
from testing.framework.dto.test_arg import TestArg
from testing.framework.dto.test_case import TestCase


class TestSuite:

    func_name: str
    description: str
    test_args: List[TestArg]
    test_cases: List[TestCase]
    result_type: str
    user_types: Dict[str, str]
    test_cases_file: str

    def __init__(self, func_name: str):
        self.func_name = func_name
        self.test_args = []
        self.test_cases = []
        self.user_types = {}