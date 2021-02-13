import json
import threading
import throttle

INVOCATION_LIMIT = 1
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


class ConsoleLogger:
    """
    Used for displaying running test suites STDOUT in the UI
    """

    def __init__(self, web):
        self._web = web
        self._total = 0
        self._index = 0
        self._progress = 0
        self._msg = ''
        self._valve = throttle.Valve()
        self._lock = threading.Semaphore()
        self._active = False

    def activate(self):
        """
        Activates console logger
        """
        self._index = 0
        self._progress = 0
        self._msg = ''
        self._web.eval("_cleanConsoleLog();")
        self._active = True

    def deactivate(self):
        """
        Deactivates console logger
        """
        self._active = False

    def setTotalCount(self, count):
        """
        Updates progress bar with the total count of tests
        :param count: count of tests to be executed
        """
        if not self._active:
            raise Exception('logger is deactivated, cant set total count')
        self._index = 0
        self._total = count
        self._web.eval("_initializeProgress();")

    def log(self, msg):
        """
        Used to display a message in the UI
        :param msg: target message
        """
        if not self._active:
            return

        cancelled = "<span class='cancel'>" in msg
        failed = "<span class='failed'>" in msg
        tc = "<span class='passed'>" in msg
        info = "<span class='info'>" in msg

        self._msg += msg + '<br/>'

        if failed:
            self._displayLogError()
        elif cancelled:
            self._displayLogCancelled()
        elif info:
            self._displayLog()
        else:
            if tc:
                self._index += 1
                self._progress = int(float(self._index / self._total) * 100)
            allow = self._valve.check(INVOCATION_DELAY_SEC, INVOCATION_LIMIT, self._index)
            if allow:
                self._displayLog()
            else:
                self._displayLogDebounce()

    def _displayLog(self):
        if not self._active:
            return
        self._lock.acquire()
        self._web.eval("_setProgress(%s);" % json.dumps(str(self._progress)))
        self._web.eval("_showConsoleLog(%s);" % json.dumps(self._msg))
        self._msg = ''
        self._lock.release()

    @debounce(DEBOUNCE_DELAY_SEC)
    def _displayLogDebounce(self):
        self._displayLog()

    def _displayLogError(self):
        if not self._active:
            return
        self._lock.acquire()
        self._web.eval("_setProgressError();")
        self._web.eval("_showConsoleLog(%s);" % json.dumps(self._msg))
        self._msg = ''
        self._lock.release()

    def _displayLogCancelled(self):
        if not self._active:
            return
        self._lock.acquire()
        self._web.eval("_setProgressCancelled();")
        self._web.eval("_showConsoleLog(%s);" % json.dumps(self._msg))
        self._msg = ''
        self._lock.release()
