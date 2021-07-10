# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from anki import sync_pb2

# public exports
SyncAuth = sync_pb2.SyncAuth
SyncOutput = sync_pb2.SyncCollectionResponse
SyncStatus = sync_pb2.SyncStatusResponse


# Legacy attributes some add-ons may be using

from .httpclient import HttpClient

AnkiRequestsClient = HttpClient


class Syncer:
    def sync(self) -> str:
        pass
