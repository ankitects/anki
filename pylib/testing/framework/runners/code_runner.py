import json
import os
import sys
import tempfile
import numbers

from abc import ABC, abstractmethod
from json import JSONDecodeError
from typing import Dict
from deepdiff import DeepDiff

from testing.framework.runners.console_logger import ConsoleLogger
from anki.utils import isMac, isWin


def create_src_file(src, name):
    """
    Stores the source code provided into temporary file
    :param src: target source code
    :param name: name of the file
    :return: Tuple - the file's parent dir and the target file
    """
    workdir = tempfile.TemporaryDirectory()
    javasrc = open(os.path.join(workdir.name, name), 'w')
    javasrc.write(src)
    javasrc.close()
    return workdir, javasrc


def get_resource_path():
    """
    Returns the Resource's base path, depending on the current OS
    :return: the path of the Resources folder in the system
    """
    if isWin:
        result = sys._MEIPASS
    elif isMac:
        result = os.environ['RESOURCEPATH']
    else:
        raise Exception('not supported OS')

    return '"' + result + '"'


def get_code_offset(src: str, user_src_start_marker: str):
    """
    Returns number of lines which precede solution src
    :param src: solution src
    :param user_src_start_marker: begin of solution src marker
    """
    start_src_index = src.index(user_src_start_marker)
    return len(src[:start_src_index].split('\n'))


def are_same_numbers(arg1, arg2):
    """
    Checks if both arguments are of numerical type and the delta between them is less than 1e-4
    :param arg1: first arg
    :param arg2: second arg
    :return: True - args both numbers and their values are equal, False - otherwise
    """
    if isinstance(arg1, numbers.Number) and isinstance(arg2, numbers.Number):
        if abs(arg1 - arg2) < 0.0001:
            return True


def compare(obj1, obj2) -> bool:
    """
    Performs deep difference between two objects, calculates absolute delta for numerical fields, if the delta is less
    than 1e-4, then the numbers are considered to be equal
    :return True - if objects are equal, False otherwise
    """
    are_equal = True
    ddiff = DeepDiff(obj1, obj2, ignore_order=True)
    if 'values_changed' in ddiff:
        changed_values = ddiff['values_changed']
        for key in changed_values:
            changed_item = changed_values[key]
            new_val = changed_item['new_value']
            old_val = changed_item['old_value']
            if are_same_numbers(new_val, old_val):
                continue
            are_equal = False
            break
    if 'type_changes' in ddiff:
        changed_types = ddiff['type_changes']
        numeric_types = False
        for key in changed_types:
            changed_type = changed_types[key]
            new_val = changed_type['new_value']
            old_val = changed_type['old_value']
            if are_same_numbers(new_val, old_val):
                numeric_types = True
        if not numeric_types:
            are_equal = False
    for key in ddiff.keys():
        if not (key in ['values_changed', 'type_changes']):
            are_equal = False
    return are_equal


class CodeRunner(ABC):
    """
    Base class for all language specific code runners
    """

    def __init__(self):
        self._pid = None
        self._stopped = False

    def run(self, src: str, logger: ConsoleLogger, messages: Dict[str, str]):
        """
        Submits source code for execution
        :param src: input source code to be executed
        :param logger: logger to display messages in the console
        :param messages: map containing predefined messages to be displayed then a test passed or failed
        """
        self._stopped = False
        result = True
        logger.activate()
        if self._pid is not None:
            raise Exception('Another test is already running')
        try:
            logger.log(messages['start_msg'])
            result = self._run(src, logger, messages)
        except Exception as e:
            result = None
            raise e
        finally:
            if result and not self._stopped:
                logger.log(messages['success_msg'])
            self.stop()

    @abstractmethod
    def _run(self, src: str, logger: ConsoleLogger, messages: Dict[str, str]) -> bool:
        """
        This method is supposed to be overriden in the sub-types, it should contain logic
        of compiling and executing the source code provided
        :param src: input source code to be executed
        :param logger: logger to display messages in the console
        :param messages: map containing predefined messages to be displayed then a test passed or failed
        :return: bool - was execution successful or not
        """
        pass

    def _set_result(self, result: str, logger: ConsoleLogger, messages: Dict[str, str]):
        """
        :param result:
        :param logger:
        :param messages:
        :return:
        """
        try:
            tc = json.loads(result)
        except JSONDecodeError:
            logger.log(result)
            return True

        if compare(tc['expected'], tc['result']):
            test_passed_msg = messages['passed_msg'] % dict(
                index=tc['index'],
                total=tc['test_case_count'],
                duration=tc['duration'])
            logger.log(test_passed_msg)
            return True
        else:
            test_failed_msg = messages['failed_msg'] % dict(
                index=tc['index'],
                total=tc['test_case_count'],
                args=tc['args'],
                expected=tc['expected'],
                result=tc['result'])
            logger.log(test_failed_msg)
            logger.deactivate()
            self.stop()
            return False

    def stop(self):
        """
        Stops the running process abnormally (if pid exists)
        """
        self._stopped = True
        if self._pid is not None:
            try:
                os.kill(self._pid, 9)
            except:
                pass
            finally:
                self._pid = None
