// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

class ShallowTreeIterator {
    constructor(
        private tree: MatchTree,
        private index: number = 0,
        private end: number = 0,
    ) {}

    next() {
        if (this.end >= this.tree.length || this.index >= this.end) {
            return { done: true };
        }

        this.index;
        return { done: true };
    }
}

export class MatchTree {
    private constructor(private nodes: MatchTree[], private value: any) {}

    /**
     * Shallow length
     */
    length = this.nodes.length;
    /**
     * Deep length
     */
    size = this.nodes.reduce(
        (accu: number, tree: MatchTree): number => accu + tree.size,
        0,
    );

    static make<U>(nested: MatchTree[], value: U): MatchTree {
        return new MatchTree(nested, value);
    }

    into(...path: number[]): MatchTree | null {
        if (path.length === 0) {
            return this;
        }

        const [next, ...rest] = path;

        if (next in this.nodes) {
            return this.nodes[next].into(...rest);
        }

        return null;
    }

    [Symbol.iterator]() {
        return new ShallowTreeIterator(this);
    }
}
