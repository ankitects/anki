// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";

import type { ConstructorParams, Size } from "../types";
import type { ShapeDataForCloze } from "./base";
import { Shape } from "./base";
import { floatToDisplay } from "./floats";

export class Text extends Shape {
    text: string;
    scaleX: number;
    scaleY: number;

    constructor({
        text = "",
        scaleX = 1,
        scaleY = 1,
        ...rest
    }: ConstructorParams<Text> = {}) {
        super(rest);
        this.text = text;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
    }

    toDataForCloze(): TextDataForCloze {
        return {
            ...super.toDataForCloze(),
            text: this.text,
            sx: floatToDisplay(this.scaleX),
            sy: floatToDisplay(this.scaleY),
        };
    }

    toFabric(size: Size): fabric.IText {
        this.makeAbsolute(size);
        return new fabric.IText(this.text, this);
    }
}

interface TextDataForCloze extends ShapeDataForCloze {
    text: string;
    sx: string;
    sy: string;
}
