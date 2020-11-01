from abc import ABC, abstractmethod
from typing import List

from testing.framework.dto.test_case import TestCase
from testing.framework.dto.test_suite import TestSuite


class TestSuiteGenerator(ABC):

    @abstractmethod
    def inject_imports(self, solution_src: str, test_suite: TestSuite) -> str:
        pass

    @abstractmethod
    def inject_test_suite_invocation(self,
                                     solution_src: str,
                                     test_cases_src: List[str],
                                     test_suite: TestSuite,
                                     test_summary_msg: str) -> str:
        pass

    @abstractmethod
    def generate_test_case_invocations(self,
                                       test_suite: TestSuite,
                                       test_passed_msg_format: str,
                                       test_failed_msg_format: str) -> List[str]:
        pass
