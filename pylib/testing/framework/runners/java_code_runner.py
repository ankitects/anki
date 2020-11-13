import os
import subprocess
import tempfile

from testing.framework.runners.code_runner import CodeRunner
from testing.framework.runners.console_logger import ConsoleLogger


class JavaCodeRunner(CodeRunner):
    #todo: fix paths
    COMPILE_CMD = '{}/Users/aleksandr.zakshevskii/Library/Java/JavaVirtualMachines/corretto-11.0.7/Contents/Home/bin/javac {} -cp /opt/dev/dave8/anki/testing/java/build/libs/java.jar'
    RUN_CMD = '{}/Users/aleksandr.zakshevskii/Library/Java/JavaVirtualMachines/corretto-11.0.7/Contents/Home/bin/java -classpath {}:/opt/dev/dave8/anki/testing/java/build/libs/java.jar {}'
    # COMPILE_CMD = '{}/libs/jdk/bin/javac {} -cp {}/libs/jdk/lib/java.jar'
    # RUN_CMD = '{}/libs/jdk/bin/java -classpath {}:{}/libs/jdk/lib/java.jar {}'
    CLASS_NAME = 'Solution'
    PKG_NAME = 'test_engine'

    def run(self, src: str, logger: ConsoleLogger):
        workdir, javasrc = self._create_src_file(src, self.CLASS_NAME + '.java')
        # resource_path = os.environ['RESOURCEPATH']
        resource_path = ''

        cmd = self.COMPILE_CMD.format(resource_path, javasrc.name, resource_path)
        proc = subprocess.Popen(cmd, shell=True, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out, err = proc.communicate()
        if err is not None:
            print(err.decode('utf-8'))
        if len(err) > 0:
            return err

        cmd = self.RUN_CMD.format(resource_path, workdir.name, self.CLASS_NAME)
        proc = subprocess.Popen(cmd, shell=True, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        for line in proc.stdout:
            logger.log(line.decode("utf-8"))
