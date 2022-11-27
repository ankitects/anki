// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as tr from "@tslib/ftl";
import type { Scheduler } from "@tslib/proto";
import { naturalUnit, unitAmount, unitName } from "@tslib/time";

export function buildNextLearnMsg(info: Scheduler.CongratsInfoResponse): string {
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
