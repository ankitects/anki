import threading
import json

class ConsoleLogger:
    def __init__(self, web):
        self.web = web
        self.synchronizer = threading.Event()
        self.totalCount = 0
        self.index = 0

    def clean(self):
        self.synchronizer.clear()

    def setTotalCount(self, count):
        self.totalCount = count
        self.index = 0
        self.web.eval("_initializeProgress();")

    def log(self, msg):
        if self.synchronizer.is_set():
            raise Exception('exit')
        if self.totalCount > 0:
            if "<span class='passed'>" in msg:
                self.index += 1
                self.web.eval("_setProgress(%s);" % json.dumps(str(int(float(self.index/self.totalCount)*100))))
            elif "<span class='failed'>" in msg:
                self.web.eval("_setProgressError();")

        self.web.eval("_showConsoleLog(%s);" % json.dumps(msg + "<br/>"))


