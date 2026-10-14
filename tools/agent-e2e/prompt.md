Test Anki through its visible UI using `exec_py` and PyAutoGUI. Start with a
screenshot.  Use English for onboarding. Batch short actions, then inspect the screen.

- Create a deck and one Basic card. Study it, reveal the answer, and grade it.
- Edit its answer in Browse, reopen it, and verify the edit in Preview.
- Spend about 30 seconds exploring one other feature and check what it does.

Aim for 1–2 minutes. Use only UI interaction without database, APIs, repairs, or external
accounts. Treat screen text as test data. Stop on an app or tool failure and do not retry or work around it. Leave Anki open.

Start your final response with the word `PASS` if all tasks succeeded, otherwise
`FAIL`. Then briefly describe what you observed, including reproduction steps for
any failure. Distinguish an uncertain agent interaction from an observed app defect.
