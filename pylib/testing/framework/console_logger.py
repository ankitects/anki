# Copyright: Daveight and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import json
import threading

import throttle

INVOCATION_LIMIT = 5
INVOCATION_DELAY_SEC = 0.1
DEBOUNCE_DELAY_SEC = 0.5


def debounce(delay):
    """
    Debounce a function's execution
    :param delay: debounce delay
    :return: decorator function
    """

    def decorate(f):
        """
        adapted decorator
        :param f: input function
        """

        def wrapped(*args, **kwargs):
            """
            wrapped function, sets timer execution for the original function,
            cancels the previous timer
            :param args: function args
            :param kwargs: function kwargs
            """
            if hasattr(decorate, 'timer'):
                decorate.timer.cancel()
            decorate.timer = threading.Timer(delay, f, args=args, kwargs=kwargs)
            decorate.timer.start()

        return wrapped

    return decorate


class TestLogger:
    """
    Logger which is used to log testing events, it is backed by throttle.Valve
    to prevent a big amount of console.logger invocations (because it can affect
    the browser)
    """
    def __init__(self, console, web, tests_count: int):
        self.console = console
        self.web = web
        self.message_buf = []
        self.valve = throttle.Valve()
        self.active = True
        self.tests_count = tests_count
        self.index = 0
        self.progress = 0

    def passed(self, index: int, duration_ms: int):
        """
        Display "passed" message
        :param index: a test's index
        :param duration_ms: a test's duration time in ms
        """
        self.progress = int(float(index / self.tests_count) * 100)
        self.log(f'Test <span class="passed">PASSED</span> ({index}/{self.tests_count}) - {duration_ms} ms<br/>')

    def fail(self, index: int, args, expected, result):
        """
        Display "passed" message
        :param index: a test's index
        :param args: a test's arguments in JSON
        :param expected: expected value in JSON
        :param result: actual result in JSON
        """
        self.progress = -1
        self.log(f'''Test <span class='failed'>FAILED</span> ({index}/{self.tests_count})<br/>
            &nbsp;&nbsp;&nbsp;&nbsp;args: {args}<br/>
            &nbsp;&nbsp;&nbsp;&nbsp;expected: {expected}<br/>
            &nbsp;&nbsp;&nbsp;&nbsp;result: {result}<br/>''', flush=True)

    def cancel(self):
        """
        Empties current message buffer
        Displays progress bar's cancel state
        Displays cancelled message to console
        """
        self.active = False
        self.message_buf.clear()
        self.console.log(f'<br/><span class="cancel">Tests execution was interrupted...</span><br/><br/>')
        self.web.eval('_setProgressCancelled()')

    def log(self, msg: str, flush: bool = False):
        """
        Internal function which is used to display log
        It doesn't display log message directly, instead, it puts message to a buffer
        which is used to accumulate message and display them regarding INVCATION_DELAY_SEC
        and INVOCATION_LIMIT settings
        :param msg: target message to log
        :param flush: force mode, if buffer must be flushed and displayed right now
        """
        self.index += 1
        self.message_buf.append(msg)
        allow = self.valve.check(INVOCATION_DELAY_SEC, INVOCATION_LIMIT, self.index)
        if flush or allow:
            self.flush_buffer()
        else:
            self.log_debounce()

    def flush_buffer(self):
        """
        Flushes and displayes the current message buffer
        """
        if not self.active:
            return
        self.console.log(''.join(self.message_buf))
        self.message_buf.clear()
        if self.progress >= 0:
            self.web.eval('_setProgress(%s)' % json.dumps(str(self.progress)))
        else:
            self.web.eval('_setProgressError()')

    @debounce(DEBOUNCE_DELAY_SEC)
    def log_debounce(self):
        """
        Adds debounce logs flushing, after execution is completed but there are some messages in the buf
        """
        self.flush_buffer()


class ConsoleLogger:
    """
    Used for displaying running test suites STDOUT in the UI
    """

    def __init__(self, web):
        self.web = web

    def get_testing_logger(self, tests_count: int) -> TestLogger:
        """
        Updates progress bar with the total count of tests
        :param tests_count: count of tests to be executed
        """
        return TestLogger(self, self.web, tests_count)

    def info(self, msg: str):
        """
        Logs message to the web console
        :param msg: target message
        """
        self.log(f'{msg}<br/>')

    def error(self, msg: str):
        """
        Logs message to the web console, which will be marked by "failed" css class
        Sets error state to the Progress Bar
        :param msg: target message
        """
        self.log(f'<span class="failed">{msg}</span><br/>')
        self.web.eval('_setProgressError()')

    def log(self, msg: str):
        """
        Internal function to log message to the web console
        :param msg: target message
        """
        self.web.eval('_showConsoleLog(%s)' % json.dumps(msg))

    def clear(self):
        """
        Clears the console and resets the progress bar
        """
        self.web.eval('_cleanConsoleLog()')
        self.web.eval('_initializeProgress()')
