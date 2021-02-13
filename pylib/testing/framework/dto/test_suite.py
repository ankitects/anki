from typing import List, Dict
from testing.framework.dto.test_arg import TestArg


class TestSuite:
    """
    This class represents a test suite. It contains information needed to run a test suite.
       - testing function name
       - challenge description
       - function arguments
       - test cases count
       - function result type
       - user types definition map
       - test cases file name
    """
    description: str
    func_name: str
    test_args: List[TestArg]
    result_type: str
    classes: Dict[str, str]
    test_case_count: int
    test_cases_file: str

    def __init__(self, func_name: str):
        self.func_name = func_name
        self.test_args = []
        self.test_cases = []
        self.classes = {}
