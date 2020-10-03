import os
import subprocess
import tempfile


class CodeRunner:
    @classmethod
    def renderTemplate(cls, method, data):
        pass

    @classmethod
    def run(cls, src, method, data):
        pass


class LangTypeBindings:
    @classmethod
    def bindings(cls):
        pass

    @classmethod
    def unwrap(cls, arg):
        if callable(arg):
            arg = arg()
        return arg

    @classmethod
    def parse(cls, src):
        args = src.split(';')
        res = []
        i = 0
        for arg in args:
            arg = arg.split(' ')
            if len(arg) < 2:
                var = 'arg' + str(i)
                vartype = arg[0]
                i += 1
            else:
                var = arg[0]
                vartype = arg[1]

            res.append({'var': var, 'type': cls.unwrap(eval(vartype, cls.bindings()))[0]})
        return res


class JavaTypeBindings(LangTypeBindings):
    @classmethod
    def int(cls):
        return ['int', 'Integer']

    @classmethod
    def long(cls):
        return ['long', 'Long']

    @classmethod
    def double(cls):
        return ['double', 'Double']

    @classmethod
    def array(cls, arg):
        return [cls.unwrap(arg)[0] + '[]']

    @classmethod
    def list(cls, arg):
        t = cls.unwrap(arg)
        i = 0
        if len(t) == 2:
            i = 1
        return ['List<' + t[i] + '>']

    @classmethod
    def bindings(cls):
        return {
            'int': cls.int,
            'long': cls.long,
            'double': cls.double,
            'array': cls.array,
            'list': cls.list
        }


class JavaRunner(CodeRunner):

    COMPILE_CMD = '/Users/aleksandr.zakshevskii/Library/Java/JavaVirtualMachines/corretto-11.0.7/Contents/Home/bin/javac {} -cp /opt/dev/anki/java/build/libs/java.jar'
    RUN_CMD = '/Users/aleksandr.zakshevskii/Library/Java/JavaVirtualMachines/corretto-11.0.7/Contents/Home/bin/java -classpath {}:/opt/dev/dave8/anki/java/build/libs/java.jar {}'

    CLASS_NAME = 'Solution'
    PKG_NAME = 'test_engine'

    CODE_TEMPLATE = """
public class {} {{
    public {} {}({}) {{
        //your code here
    }}
}}
"""
    CODE_IMPORTS = """
import test_engine.TestRunner;"""
    MAIN_METHOD = """
     public static void main(String[] args) {{
         TestRunner.run({}.class, "{}", "{}");
     }}
"""

    @classmethod
    def stripHTML(cls, str):
        return str.replace('<br>', '\n')

    @classmethod
    def wrap(cls, src, method, data):
        data = cls.stripHTML(data)
        src = cls.CODE_IMPORTS + src
        idx = src.rfind('}')
        data = data.replace("\n", "\\n")
        return src[:idx] + cls.MAIN_METHOD.format(cls.CLASS_NAME, method, data) + src[idx:]

    @classmethod
    def renderTemplate(cls, method, testcases):
        testcases = cls.stripHTML(testcases)
        lines = testcases.splitlines()
        args = JavaTypeBindings.parse(lines[0])
        methodargs = ', '.join([arg['type'] + ' ' + arg['var'] for arg in args[0:-1]])
        restype = args[len(args) - 1]['type']
        return cls.CODE_TEMPLATE.format(cls.CLASS_NAME, restype, method, methodargs)

    @classmethod
    def run(cls, src, method, data):
        src = cls.wrap(src, method, data)
        workdir = tempfile.TemporaryDirectory()
        os.makedirs(workdir.name + '/' + cls.PKG_NAME)
        javasrc = open(workdir.name + '/' + cls.CLASS_NAME + '.java', 'w')
        javasrc.write(src)
        javasrc.close()

        cmd = cls.COMPILE_CMD.format(javasrc.name)
        proc = subprocess.Popen(cmd, shell=True, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out, err = proc.communicate()
        if len(err) > 0:
            return err

        cmd = cls.RUN_CMD.format(workdir.name, cls.CLASS_NAME)
        proc = subprocess.Popen(cmd, shell=True, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out, err = proc.communicate()
        if len(err) > 0:
            return err
        else:
            return out


runner = JavaRunner()
tmpl = runner.renderTemplate("hello", "testArray array(array(double));int;int\n2;2;4")
print(tmpl)

data = 'a int;b int;int\\n2;2;4'
src = """
public class Solution {
    public int sumUp(int a, int b) {
        return a + b;
    }
}
"""

result = runner.run(src, 'sumUp', data)
print(result)
j = 0