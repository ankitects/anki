// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export class TreeVertex {
    private constructor(private nested: TreeVertex[]) {}

    static make(nested: TreeVertex[]) {
        return new TreeVertex(nested);
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
