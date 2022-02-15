// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { nodeIsElement, nodeIsText } from "../../lib/dom";
import { getRangeCoordinates } from "../location";
import type { RangeCoordinatesContent } from "../location/range";
import { ChildNodeRange } from "./child-node-range";
import type { ElementMatcher, FoundMatch } from "./match-type";
import { applyMatcher } from "./match-type";
import { elementIsNegligible, textIsNegligible } from "./node-negligible";
import type { TreeNode } from "./tree-node";
import { BlockNode, FormattingNode, MatchNode } from "./tree-node";
import { nodeWithinRange } from "./within-range";

/**
 * @returns Split text node to end direction
 */
function splitText(node: Text, offset: number): Text {
    return node.splitText(offset);
}

// TODO maybe both start and end should be of type Node
// Could also probably be the new "Range anchors"
interface SplitRange {
    /**
     * Used to recreate a range: `range.setStartBefore(start)`
     */
    start: Text | null;
    /**
     * Used to recreate a range: `range.setEndAfter(end)`
     */
    end: Text | null;
}

export function splitPartiallySelectedTextNodes(range: Range): SplitRange {
    const startContainer = range.startContainer;
    const startOffset = range.startOffset;

    // TODO Maybe we should avoid splitting, if they
    // create zero-length text nodes

    const start = nodeIsText(startContainer)
        ? splitText(startContainer, startOffset)
        : null;

    const endContainer = range.endContainer;
    const endOffset = range.endOffset;

    let end: Text | null = null;
    if (nodeIsText(endContainer)) {
        const splitOff = splitText(endContainer, endOffset);

        if (splitOff.data.length === 0) {
            // Range should include the split-off text if it is zero-length.
            // For the start container, this is done automatically.
            end = splitOff;
            range.setEndAfter(end);
        } else {
            end = endContainer;
        }
    }

    return { start, end };
}

function findWithinNodeInner(
    node: Node,
    matcher: ElementMatcher,
    matches: FoundMatch[],
): void {
    if (nodeIsElement(node)) {
        for (const child of node.children) {
            findWithinNodeInner(child, matcher, matches);
        }

        const match = applyMatcher(matcher, node);
        if (match.type) {
            matches.push({ match, element: node as HTMLElement | SVGElement });
        }
    }
}

/**
 * Will not include parent node
 */
export function findWithinNode(node: Node, matcher: ElementMatcher): FoundMatch[] {
    const matches: FoundMatch[] = [];

    if (nodeIsElement(node)) {
        for (const child of node.children) {
            findWithinNodeInner(child, matcher, matches);
        }
    }

    return matches;
}

////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * @param last: The node which is intended to be the new end node.
 */
function mergeInNode(initial: TreeNode[], last: FormattingNode, format: ParseFormat): TreeNode[] {
    const minimized: TreeNode[] = [last];

    for (let i = initial.length - 1; i >= 0; i--) {
        const next = initial[i];

        let merged: FormattingNode | null;
        if (next instanceof FormattingNode && (merged = format.tryMerge(next, last))) {
            minimized[0] = merged;
        } else {
            minimized.unshift(...initial.slice(0, i + 1));
            break;
        }
    }

    return minimized;
}

function appendNode(nodes: TreeNode[], node: TreeNode, format: ParseFormat): TreeNode[] {
    if (node instanceof FormattingNode) {
        return mergeInNode(nodes, node, format);
    } else {
        return [...nodes, node];
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////

function buildTreeNode(
    element: Element,
    format: ParseFormat,
    covered: boolean,
): TreeNode | null {
    const match = format.matches(element);
    const covers = covered || Boolean(match.type);

    let children: TreeNode[] = [];
    for (const child of element.childNodes) {
        const node = buildFormattingTree(child, format, covers);

        if (node) {
            children = appendNode(children, node, format);
        }
    }

    const matchNode = MatchNode.make(
        element,
        match,
        covers || children.every((node: TreeNode): boolean => node.covered),
        children.every((node: TreeNode): boolean => node.insideRange),
    );

    if (children.length === 0 && !match.type) {
        return null;
    }

    if (children.length === 1) {
        const [only] = children;

        if (
            // blocking
            only instanceof BlockNode ||
            // ascension
            (only instanceof FormattingNode && matchNode.isAscendable() && format.tryAscend(only, matchNode))
        ) {
            return only;
        }
    }

    matchNode.replaceChildren(children);
    return matchNode;
}

function buildFormattingNode(
    node: Node,
    format: ParseFormat,
    covered: boolean,
): FormattingNode | BlockNode {
    const insideRange = format.isInsideRange(node);

    if (!insideRange && !covered) {
        return BlockNode.make(false, false);
    }

    return FormattingNode.make(ChildNodeRange.fromNode(node), insideRange, covered);
}

import type { ParseFormat } from "./match-type";
/**
 * Builds a formatting tree starting at node.
 *
 * @returns root of the formatting tree
 */
export function buildFormattingTree(
    node: Node,
    format: ParseFormat,
    covered: boolean,
): TreeNode | null {
    // debugger;
    if (nodeIsText(node) && !textIsNegligible(node)) {
        return buildFormattingNode(node, format, covered);
    } else if (nodeIsElement(node) && !elementIsNegligible(node)) {
        return buildTreeNode(node, format, covered);
    } else {
        return null;
    }
}

function previousSibling(node: Node): ChildNode | null {
    return node.previousSibling;
}

function nextSibling(node: Node): ChildNode | null {
    return node.nextSibling;
}

interface MergeResult {
    node: FormattingNode;
    hitBorder: boolean;
}

/**
 * @param main: Node into which is merged. Is modified.
 *
 * @returns Whether siblings were exhausted during merging
 */
function mergePreviousTrees(
    start: FormattingNode,
    format: ParseFormat,
): MergeResult {
    let result = start;

    let sibling = previousSibling(start.range.parent);
    while (sibling) {
        const siblingNode = buildFormattingTree(sibling, format, false);

        if (siblingNode) {
            let merged: FormattingNode | null;
            if (
                siblingNode.covered &&
                siblingNode instanceof FormattingNode &&
                (merged = format.tryMerge(siblingNode, result))
            ) {
                result = merged;
            } else {
                return {
                    node: result,
                    hitBorder: false,
                };
            }
        }

        sibling = previousSibling(sibling);
    }

    return {
        node: result,
        hitBorder: true,
    };
}

/**
 * @param main: Node into which is merged. Is modified.
 *
 * @returns Whether siblings were exhausted during merging
 */
function mergeNextTrees(
    start: FormattingNode,
    format: ParseFormat,
): MergeResult {
    let result = start;

    let sibling = nextSibling(start.range.parent);
    while (sibling) {
        const siblingNode = buildFormattingTree(sibling, format, false);

        if (siblingNode) {
            let merged: FormattingNode | null;
            if (
                siblingNode.covered &&
                siblingNode instanceof FormattingNode &&
                (merged = format.tryMerge(result, siblingNode))
            ) {
                result = merged;
            } else {
                return {
                    node: result,
                    hitBorder: false,
                };
            }
        }

        sibling = nextSibling(sibling);
    }

    return {
        node: result,
        hitBorder: true,
    };
}

function extendAndMerge(
    node: FormattingNode,
    format: ParseFormat,
): TreeNode {
    node.extendAndAscend(format);

    const previous = mergePreviousTrees(node, format);
    const next = mergeNextTrees(previous.node, format);
    const parent = node.range.parent.parentElement;

    if (!previous.hitBorder || !next.hitBorder || !parent) {
        return next.node;
    }

    const matchNode = MatchNode.make(
        parent,
        format.matches(parent),
        next.node.covered,
        next.node.insideRange,
    );

    format.tryAscend(next.node, matchNode);

    // Even if matchNode ends up matching and next.node refuses to ascend,
    // as this function assumes we're not placed in an ancestor matching
    // element, it does not matter.
    return next.node;
}

export function buildTreeFromNode(
    node: Node,
    format: ParseFormat,
    covered: boolean,
): TreeNode | null {
    // const coordinates = getRangeCoordinates(range, base) as RangeCoordinatesContent;

    // TODO range is only used to check whether text nodes are placed within the range
    // also, this must be false during extendAndMerging, maybe replace this with a callback
    const output = buildFormattingTree(node, format, covered);

    if (output instanceof FormattingNode) {
        return extendAndMerge(output, format);
    }

    return output;
}
// export function buildTreeFromNode(node: Node, matcher: ElementMatcher): TreeNode | null {
//     if (nodeIsText(node) && !textIsNegligible(node)) {
//         return FormattingNode.make(ChildNodeRange.fromNode(node));
//     } else if (nodeIsElement(node) && !elementIsNegligible(node)) {
//         return buildTreeNode(node, matcher);
//     } else {
//         return null;
//     }
// }

/**
 * @returns Text nodes contained in node in source order
 */
// TODO -> built match tree at that point
export function findTextsWithinNode(node: Node): Text[] {
    if (nodeIsText(node)) {
        return [node];
    } else if (nodeIsElement(node)) {
        const nodes: Text[] = [];

        for (const child of node.childNodes) {
            nodes.push(...findTextsWithinNode(child));
        }

        return nodes;
    } else {
        return [];
    }
}

/**
 * @returns Text nodes contained in range in source order
 */
export function findTextsWithinRange(range: Range): Text[] {
    return findTextsWithinNode(range.commonAncestorContainer).filter(
        (text: Text): boolean => nodeWithinRange(text, range),
    );
}

/**
 * We do not want to surround zero-length text nodes
 */
export function validText(text: Text): boolean {
    return text.length > 0;
}
