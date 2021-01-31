# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import anki._backend.backend_pb2 as _pb

# public exports
SyncAuth = _pb.SyncAuth
SyncOutput = _pb.SyncCollectionOut
SyncStatus = _pb.SyncStatusOut


# Legacy attributes some add-ons may be using

from .httpclient import HttpClient

AnkiRequestsClient = HttpClient


class Syncer:
    def sync(self) -> str:
        pass
