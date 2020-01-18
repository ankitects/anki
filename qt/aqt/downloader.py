# Copyright: Ankitects Pty Ltd and contributors
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import re
import time

import aqt
from anki import hooks
from anki.httpclient import AnkiRequestsClient
from anki.lang import _
from aqt.qt import *


def download(mw, code):
    "Download addon from AnkiWeb. Caller must start & stop progress diag."
    # create downloading thread
    thread = Downloader(code)
    done = False

    def onRecv():
        if done:
            return
        mw.progress.update(label="%dKB downloaded" % (thread.recvTotal / 1024))

    thread.recv.connect(onRecv)
    thread.start()
    while not thread.isFinished():
        mw.app.processEvents()
        thread.wait(100)

    # make sure any posted events don't fire after we return
    done = True

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

        hooks.http_data_did_receive.append(recvEvent)
        client = AnkiRequestsClient()
        try:
            resp = client.get(aqt.appShared + "download/%s?v=2.1" % self.code)
            if resp.status_code == 200:
                data = client.streamContent(resp)
            elif resp.status_code in (403, 404):
                self.error = _(
                    "Invalid code, or add-on not available for your version of Anki."
                )
                return
            else:
                self.error = _("Unexpected response code: %s" % resp.status_code)
                return
        except Exception as e:
            self.error = _("Please check your internet connection.") + "\n\n" + str(e)
            return
        finally:
            hooks.http_data_did_receive.remove(recvEvent)

        self.fname = re.match(
            "attachment; filename=(.+)", resp.headers["content-disposition"]
        ).group(1)
        self.data = data
