from abc import abstractmethod, ABC
from testing.framework.dto.test_suite import TestSuite


class SolutionTemplateGenerator(ABC):
    """
    Base class for a solution template code generators.
    """

    @abstractmethod
    def generate_solution_template(self, test_suite: TestSuite) -> str:
        """
        Generates a solution's source code for a specific programming language
        :param test_suite: input test suite with all neccessary information
        """
        pass
