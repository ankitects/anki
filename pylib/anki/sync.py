# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from .httpclient import HttpClient

AnkiRequestsClient = HttpClient


class Syncer:
    def sync(self) -> str:
        pass
