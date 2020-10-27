// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { CountableTimeInterval } from 'd3-time';

import { timeSunday, timeMonday, timeFriday, timeSaturday } from "d3-time";

export function checkFirstWeekday(): CountableTimeInterval {
    const params = new URLSearchParams(window.location.search);
    const firstWeekday = Number(params.get('firstWeekday'));

    switch (firstWeekday) {
        case 0:
            return timeSunday;
        case 1:
            return timeMonday;
        case 5:
            return timeFriday;
        case 6:
            return timeSaturday;
        default:
            return timeSunday;
    }
}
