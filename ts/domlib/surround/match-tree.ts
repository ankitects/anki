// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export class TreeVertex {
    private constructor(private nested: TreeVertex[], private value: any) {}

    static make<U>(nested: TreeVertex[], value: U): TreeVertex {
        return new TreeVertex(nested, value);
    }

    into(...path: number[]): TreeVertex | null {
        if (path.length === 0) {
            return this;
        }

        const [next, ...rest] = path;

        if (next in this.nested) {
            return this.nested[next].into(...rest);
        }

        return null;
    }
}
