import os
import subprocess
import tempfile

from testing.framework.runners.code_runner import CodeRunner
from testing.framework.runners.console_logger import ConsoleLogger


class JavaCodeRunner(CodeRunner):
    #todo: fix paths
    COMPILE_CMD = '/Users/aleksandr.zakshevskii/Library/Java/JavaVirtualMachines/corretto-11.0.7/Contents/Home/bin/javac {} -cp /opt/dev/dave8/anki/testing/java/build/libs/java.jar'
    RUN_CMD = '/Users/aleksandr.zakshevskii/Library/Java/JavaVirtualMachines/corretto-11.0.7/Contents/Home/bin/java -classpath {}:/opt/dev/dave8/anki/testing/java/build/libs/java.jar {}'
    CLASS_NAME = 'Solution'
    PKG_NAME = 'test_engine'

    def run(self, src: str, logger: ConsoleLogger):
        # workdir = tempfile.TemporaryDirectory()
        # os.makedirs(workdir.name + '/' + self.PKG_NAME)
        # javasrc = open(workdir.name + '/' + self.CLASS_NAME + '.java', 'w')
        # javasrc.write(src)
        # javasrc.close()
        workdir, javasrc = self._create_src_file(src, self.CLASS_NAME + '.java')

        cmd = self.COMPILE_CMD.format(javasrc.name)
        proc = subprocess.Popen(cmd, shell=True, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out, err = proc.communicate()
        if len(err) > 0:
            return err

        cmd = self.RUN_CMD.format(workdir.name, self.CLASS_NAME)
        proc = subprocess.Popen(cmd, shell=True, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        for line in proc.stdout:
            logger.log(line.decode("utf-8"))
        # out, err = proc.communicate()
        # if len(err) > 0:
        #     return err
        # else:
        #     return out
        # pass