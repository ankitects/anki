# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
from http import HTTPStatus
import http.server
import errno

class MediaServer(QThread):

    def run(self):
        self.port = 8080
        self.server = None
        while not self.server:
            try:
                self.server = http.server.HTTPServer(
                        ("localhost", self.port), RequestHandler)
            except OSError as e:
                if e.errno == errno.EADDRINUSE:
                    self.port += 1
                    continue
                raise
            break
        self.server.serve_forever()

class RequestHandler(http.server.SimpleHTTPRequestHandler):

    def send_head(self):
        path = self.translate_path(self.path)
        if os.path.isdir(path):
            self.send_error(HTTPStatus.NOT_FOUND, "File not found")
            return None
        ctype = self.guess_type(path)
        try:
            f = open(path, 'rb')
        except OSError:
            self.send_error(HTTPStatus.NOT_FOUND, "File not found")
            return None
        try:
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-type", ctype)
            fs = os.fstat(f.fileno())
            self.send_header("Content-Length", str(fs[6]))
            self.send_header("Last-Modified", self.date_time_string(fs.st_mtime))
            self.end_headers()
            return f
        except:
            f.close()
            raise

    def log_message(self, format, *args):
        if not os.getenv("ANKIDEV"):
            return
        print("%s - - [%s] %s" %
                         (self.address_string(),
                          self.log_date_time_string(),
                          format%args))
