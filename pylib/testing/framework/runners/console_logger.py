import threading
import json


class ConsoleLogger:
    """
    Used for displaying running test suites STDOUT in the UI
    """
    def __init__(self, web):
        self._web = web
        self._synchronizer = threading.Event()
        self._totalCount = 0
        self._index = 0

    def clean(self):
        """
        Clears the state by reseting the internal synchronizer
        """
        # TODO: do I need it?
        self._synchronizer.clear()

    def setTotalCount(self, count):
        """
        Updates progress bar with the total count of tests
        :param count: count of tests to be executed
        """
        self._totalCount = count
        self._index = 0
        self._web.eval("_initializeProgress();")

    def log(self, msg):
        """
        Used to display a message in the UI
        :param msg: target message
        """
        # if self.synchronizer.is_set():
        #     raise Exception('exit')
        if self._totalCount > 0:
            if "<span class='passed'>" in msg:
                self._index += 1
                self._web.eval("_setProgress(%s);" % json.dumps(str(int(float(self._index / self._totalCount) * 100))))
            elif "<span class='failed'>" in msg:
                self._web.eval("_setProgressError();")

        self._web.eval("_showConsoleLog(%s);" % json.dumps(msg + "<br/>"))
