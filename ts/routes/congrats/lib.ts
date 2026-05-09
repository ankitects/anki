// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { CongratsInfoResponse } from "@generated/anki/scheduler_pb";
import * as tr from "@generated/ftl";
import { naturalUnit, unitAmount, unitName } from "@tslib/time";

export function buildNextLearnMsg(info: CongratsInfoResponse): string {
    const secsUntil = info.secsUntilNextLearn;
    // next learning card not due today?
    if (secsUntil >= 86_400) {
        return "";
    }

    const unit = naturalUnit(secsUntil);
    const amount = Math.round(unitAmount(unit, secsUntil));
    const unitStr = unitName(unit);
    const nextLearnDue = tr.schedulingNextLearnDue({
        amount,
        unit: unitStr,
    });
    const remaining = tr.schedulingLearnRemaining({
        remaining: info.learnRemaining,
    });
    return `${nextLearnDue} ${remaining}`;
}
