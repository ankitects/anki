import tempfile
from abc import ABC, abstractmethod

from testing.framework.runners.console_logger import ConsoleLogger


class CodeRunner(ABC):

    def _create_src_file(self, src, name):
        workdir = tempfile.TemporaryDirectory()
        # os.makedirs(workdir.name + '/' + self.PKG_NAME)
        javasrc = open(workdir.name + '/' + name, 'w')
        javasrc.write(src)
        javasrc.close()
        return workdir, javasrc

    @abstractmethod
    def run(self, src: str, logger: ConsoleLogger):
        pass
