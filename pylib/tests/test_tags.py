import os
import tempfile
from typing import Any

from anki.collection import Collection as aopen
from anki.dbproxy import emulate_named_args
from anki.lang import TR, without_unicode_isolation
from anki.stdmodels import _legacy_add_basic_model, get_stock_notetypes
from anki.utils import is_win
from tests.shared import assertException, getEmptyCol
def test_legacy_bulk_add():
    col = getEmptyCol()
    tag_manager = col.tags

    ids = [1, 2]
    tags = "tag1 tag2"

    tag_manager._legacy_bulk_add(ids, tags, True)
    assert tag_manager.branch_coverage2["legacy_bulk_add"] == True
    assert tag_manager.branch_coverage2["legacy_bulk_remove"] == False
    tag_manager.print_coverage2()

    tag_manager.branch_coverage2["legacy_bulk_add"] = False

    tag_manager._legacy_bulk_add(ids, tags, False)
    assert tag_manager.branch_coverage2["legacy_bulk_remove"] == True
    assert tag_manager.branch_coverage2["legacy_bulk_add"] == False
    tag_manager.print_coverage2()