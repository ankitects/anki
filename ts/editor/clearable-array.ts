// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

class ClearableObject {
    owner: ClearableObject[];

    constructor(owner: ClearableObject[]) {
        this.owner = owner;
    }

    destroy() {
        const index = this.owner.indexOf(this);
        this.owner.splice(index, 1);
    }
}

export function clearableArray() {
    const list: ClearableObject[] = [];

    return new Proxy(list, {
        get: function (target: ClearableObject[], prop: string | symbol) {
            if (!isNaN(Number(prop)) && !target[prop]) {
                target[prop] = new ClearableObject(target);
            }

            return target[prop];
        },
    });
}
