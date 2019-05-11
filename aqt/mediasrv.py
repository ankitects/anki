# Copyright: Ankitects Pty Ltd and contributors
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
from http import HTTPStatus
import http.server
import socketserver
import socket
from anki.utils import devMode
import threading
import re

# locate web folder in source/binary distribution
def _getExportFolder():
    # running from source?
    srcFolder = os.path.join(os.path.dirname(__file__), "..")
    webInSrcFolder = os.path.abspath(os.path.join(srcFolder, "web"))
    if os.path.exists(webInSrcFolder):
        return webInSrcFolder
    elif isMac:
        dir = os.path.dirname(os.path.abspath(__file__))
        return os.path.abspath(dir + "/../../Resources/web")
    else:
        raise Exception("couldn't find web folder")

_exportFolder = _getExportFolder()

# webengine on windows sometimes opens a connection and fails to send a request,
# which will hang the server if unthreaded
class ThreadedHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    # allow for a flood of requests before we've started up properly
    request_queue_size = 100

    # work around python not being able to handle non-latin hostnames
    def server_bind(self):
        """Override server_bind to store the server name."""
        socketserver.TCPServer.server_bind(self)
        host, port = self.server_address[:2]
        try:
            self.server_name = socket.getfqdn(host)
        except:
            self.server_name = "server"
        self.server_port = port

class MediaServer(threading.Thread):

    _port = None
    _ready = threading.Event()
    daemon = True

    def __init__(self, mw, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.mw = mw

    def run(self):
        RequestHandler.mw = self.mw
        self.server = ThreadedHTTPServer(("127.0.0.1", 0), RequestHandler)
        self._ready.set()
        self.server.serve_forever()

    def getPort(self):
        self._ready.wait()
        port = self.server.server_port
        f = open(".server_url","w+")
        f.write("http://127.0.0.1:%d/" % port)
        f.close()
        return port

    def shutdown(self):
        self.server.shutdown()

class RequestHandler(http.server.SimpleHTTPRequestHandler):

    timeout = 1
    mw = None

    def do_GET(self):
        f = self.send_head()
        if f:
            try:
                self.copyfile(f, self.wfile)
            except Exception as e:
                if devMode:
                    print("http server caught exception:", e)
                else:
                    # swallow it - user likely surfed away from
                    # review screen before an image had finished
                    # downloading
                    pass
            finally:
                f.close()

    def send_head(self):
        path = self.translate_path(self.path)
        path = self._redirectWebExports(path)
        try:
            isdir = os.path.isdir(path)
        except ValueError:
            # path too long exception on Windows
            self.send_error(HTTPStatus.NOT_FOUND, "File not found")
            return None

        if isdir:
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
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            return f
        except:
            f.close()
            raise

    def log_message(self, format, *args):
        if not devMode:
            return
        print("%s - - [%s] %s" %
                         (self.address_string(),
                          self.log_date_time_string(),
                          format%args))


    def _redirectWebExports(self, path):
        # catch /_anki references and rewrite them to web export folder
        targetPath = os.path.join(os.getcwd(), "_anki", "")
        if path.startswith(targetPath):
            newPath = os.path.join(_exportFolder, path[len(targetPath):])
            return newPath
        
        # catch /_addons references and rewrite them to addons folder
        targetPath = os.path.join(os.getcwd(), "_addons", "")
        if path.startswith(targetPath):
            try:
                addMgr = self.mw.addonManager
            except AttributeError:
                return path
            
            addonPath = path[len(targetPath):]
            
            try:
                addon, subPath = addonPath.split(os.path.sep, 1)
            except ValueError:
                return path
            if not addon:
                return path
            
            pattern = addMgr.getWebExports(addon)
            if not pattern:
                return path
            
            if not re.fullmatch(pattern, subPath):
                return path
            
            newPath = os.path.join(addMgr.addonsFolder(), addonPath)
            return newPath
        
        return path

# work around Windows machines with incorrect mime type
RequestHandler.extensions_map['.css'] = "text/css"
