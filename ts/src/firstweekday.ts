// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export function checkFirstWeekday(): number {
    const params = new URLSearchParams(window.location.search)
    const firstWeekday = Number(params.get('firstWeekday'))

    return firstWeekday;
}
