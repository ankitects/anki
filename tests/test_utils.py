# coding: utf-8

from anki.utils import fmtTimeSpan

def test_fmtTimeSpan():
    assert fmtTimeSpan(5) == "5 seconds"
    assert fmtTimeSpan(5, inTime=True) == "in 5 seconds"
