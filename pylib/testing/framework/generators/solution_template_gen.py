from abc import abstractmethod, ABC

from testing.framework.dto.test_suite import TestSuite


class SolutionTemplateGenerator(ABC):

    @abstractmethod
    def generate_solution_template(self, test_suite: TestSuite) -> str:
        pass