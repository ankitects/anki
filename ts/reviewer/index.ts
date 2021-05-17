// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

// This is a temporary extra file we load separately from reviewer.ts. Once
// reviewer.ts has been migrated into ts/, the code here can be merged into
// it.

import { mutateNextCardStates } from "./answering";
globalThis.anki = { ...globalThis.anki, mutateNextCardStates };
