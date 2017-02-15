# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import time, re, traceback
from aqt.qt import *
from anki.sync import AnkiRequestsClient
from aqt.utils import showWarning
from anki.hooks import addHook, remHook
import aqt

def download(mw, code):
    "Download addon/deck from AnkiWeb. Caller must start & stop progress diag."
    # create downloading thread
    thread = Downloader(code)
    def onRecv():
        try:
            mw.progress.update(label="%dKB downloaded" % (thread.recvTotal/1024))
        except NameError:
            # some users report the following error on long downloads
            # NameError: free variable 'mw' referenced before assignment in enclosing scope
            # unsure why this is happening, but guard against throwing the
            # error
            pass
    thread.recv.connect(onRecv)
    thread.start()
    while not thread.isFinished():
        mw.app.processEvents()
        thread.wait(100)
    if not thread.error:
        # success
        return thread.data, thread.fname
    else:
        return "error", thread.error

class Downloader(QThread):

    recv = pyqtSignal()

    def __init__(self, code):
        QThread.__init__(self)
        self.code = code
        self.error = None

    def run(self):
        # setup progress handler
        self.byteUpdate = time.time()
        self.recvTotal = 0
        def recvEvent(bytes):
            self.recvTotal += bytes
            self.recv.emit()
        addHook("httpRecv", recvEvent)
        client = AnkiRequestsClient()
        try:
            resp = client.get(
                aqt.appShared + "download/%d" % self.code)
            if resp.status_code == 200:
                data = client.streamContent(resp)
            elif resp.status_code in (403,404):
                self.error = _("Invalid code")
                return
            else:
                self.error = _("Error downloading: %s" % resp.status_code)
                return
        except Exception as e:
            exc = traceback.format_exc()
            try:
                self.error = str(e[0])
            except:
                self.error = str(exc)
            return
        finally:
            remHook("httpRecv", recvEvent)

        self.fname = re.match("attachment; filename=(.+)",
                              resp.headers['content-disposition']).group(1)
        self.data = data
