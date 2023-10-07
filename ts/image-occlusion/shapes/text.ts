// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";

import type { ConstructorParams, Size } from "../types";
import type { ShapeDataForCloze } from "./base";
import { Shape } from "./base";
import { floatToDisplay } from "./floats";

export class Text extends Shape {
    text: string;
    scale: number;

    constructor({
        text = "",
        scale = 1,
        ...rest
    }: ConstructorParams<Text> = {}) {
        super(rest);
        this.text = text;
        this.scale = scale;
    }

    toDataForCloze(): TextDataForCloze {
        return {
            ...super.toDataForCloze(),
            text: this.text,
            scale: floatToDisplay(this.scale),
        };
    }

    toFabric(size: Size): fabric.IText {
        this.makeAbsolute(size);
        return new fabric.IText(this.text, { ...this, scaleX: this.scale, scaleY: this.scale });
    }
}

interface TextDataForCloze extends ShapeDataForCloze {
    text: string;
    scale: string;
}
