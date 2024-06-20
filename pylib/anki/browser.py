# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

branch_coverage = {
    "notes_branch": False,
    "cards_branch": False   
}

def print_coverage():
    for branch, hit in branch_coverage.items():
        print(f"{branch} was {'hit' if hit else 'not hit'}")

class BrowserConfig:
    ACTIVE_CARD_COLUMNS_KEY = "activeCols"
    ACTIVE_NOTE_COLUMNS_KEY = "activeNoteCols"
    CARDS_SORT_COLUMN_KEY = "sortType"
    NOTES_SORT_COLUMN_KEY = "noteSortType"
    CARDS_SORT_BACKWARDS_KEY = "sortBackwards"
    NOTES_SORT_BACKWARDS_KEY = "browserNoteSortBackwards"

    @staticmethod
    def active_columns_key(is_notes_mode: bool) -> str:
        if is_notes_mode:
            return BrowserConfig.ACTIVE_NOTE_COLUMNS_KEY
        return BrowserConfig.ACTIVE_CARD_COLUMNS_KEY

    @staticmethod
    def sort_column_key(is_notes_mode: bool) -> str:
        if is_notes_mode:
            branch_coverage["notes_branch"] = True
            return BrowserConfig.NOTES_SORT_COLUMN_KEY
        branch_coverage["cards_branch"] = True
        return BrowserConfig.CARDS_SORT_COLUMN_KEY

    @staticmethod
    def sort_backwards_key(is_notes_mode: bool) -> str:
        if is_notes_mode:
            return BrowserConfig.NOTES_SORT_BACKWARDS_KEY
        return BrowserConfig.CARDS_SORT_BACKWARDS_KEY


class BrowserDefaults:
    CARD_COLUMNS = ["noteFld", "template", "cardDue", "deck"]
    NOTE_COLUMNS = ["noteFld", "note", "template", "noteTags"]

result = BrowserConfig.sort_column_key(True)
print_coverage()

branch_coverage["cards_branch"] = False
branch_coverage["notes_branch"] = False

result = BrowserConfig.sort_column_key(False)
print_coverage()
