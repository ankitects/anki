// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";

import type { ConstructorParams, Size } from "../types";
import type { ShapeDataForCloze } from "./base";
import { Shape } from "./base";

export class Text extends Shape {
    text: string;

    constructor({ text = "", ...rest }: ConstructorParams<Text> = {}) {
        super(rest);
        this.text = text;
    }

    toDataForCloze(): TextDataForCloze {
        return {
            ...super.toDataForCloze(),
            text: this.text,
        };
    }

    toFabric(size: Size): fabric.IText {
        this.makeAbsolute(size);
        console.log("this.text", this.text);
        return new fabric.IText(this.text, this);
    }
}

interface TextDataForCloze extends ShapeDataForCloze {
    text: string;
}
