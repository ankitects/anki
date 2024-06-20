import unittest
from anki.browser import BrowserConfig  

class TestBrowserConfig(unittest.TestCase):
    def test_sort_column_key_notes_mode(self):
        result = BrowserConfig.sort_column_key(True)
        self.assertEqual(result, BrowserConfig.NOTES_SORT_COLUMN_KEY)

    def test_sort_column_key_cards_mode(self):
        result = BrowserConfig.sort_column_key(False)
        self.assertEqual(result, BrowserConfig.CARDS_SORT_COLUMN_KEY)

if __name__ == '__main__':
    unittest.main()