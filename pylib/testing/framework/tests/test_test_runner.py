import unittest

from testing.framework.test_runner import parse_response
from testing.framework.types import TestResponse


class TestRunnerTests(unittest.TestCase):
    def test_line_parser_only_json(self):
        line = '{"duration": 1, "result": 1}'
        tst_resp, msg = parse_response(line)
        self.assertEqual(TestResponse(result=1, duration=1), tst_resp)
        self.assertEqual('', msg)

    def test_line_parser_only_text(self):
        line = '"duration": 1, "result": 1'
        tst_resp, msg = parse_response(line)
        self.assertEqual('"duration": 1, "result": 1', msg)
        self.assertIsNone(tst_resp)

    def test_line_parser_mixed_text_and_json(self):
        line = 'ok{"duration": 1, "result": 1}'
        tst_resp, msg = parse_response(line)
        self.assertEqual(TestResponse(result=1, duration=1), tst_resp)
        self.assertEqual('ok', msg)

    def test_line_parser_mixed_text_and_json_obscured(self):
        line = 'ok{{{"duration": 1, "result": 1}'
        tst_resp, msg = parse_response(line)
        self.assertEqual(TestResponse(result=1, duration=1), tst_resp)
        self.assertEqual('ok{{', msg)

    def test_line_parser_mixed_text_and_json_obscured_2(self):
        line = 'ok{{}}{"duration": 1, "result": 1}'
        tst_resp, msg = parse_response(line)
        self.assertEqual(TestResponse(result=1, duration=1), tst_resp)
        self.assertEqual('ok{{}}', msg)

    def test_line_parser_mixed_text_and_json_obscured_2(self):
        line = '{"duration": 0}'
        tst_resp, msg = parse_response(line)
        self.assertEqual(TestResponse(result=None, duration=0), tst_resp)
