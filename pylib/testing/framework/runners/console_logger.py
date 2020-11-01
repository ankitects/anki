import threading


class ConsoleLogger:
    def __init__(self, logging_callback):
        self.logging_callback = logging_callback
        self.synchronizer = threading.Event()

    def clean(self):
        self.synchronizer.clear()

    def log(self, msg):
        if self.synchronizer.is_set():
            raise Exception('exit')
        self.logging_callback(msg)


