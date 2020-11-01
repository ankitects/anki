from abc import abstractmethod, ABC

from testing.framework.dto.test_suite import TestSuite


class TemplateGenerator(ABC):

    @abstractmethod
    def generate_template_src(self, test_suite: TestSuite) -> str:
        pass