// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";

import { SHAPE_MASK_COLOR, TEXT_FONT_FAMILY, TEXT_PADDING } from "../tools/lib";
import type { ConstructorParams, Size } from "../types";
import type { ShapeDataForCloze } from "./base";
import { Shape } from "./base";
import { floatToDisplay } from "./floats";
import { xFromNormalized, xToNormalized, yFromNormalized, yToNormalized } from "./position";

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
            // scaleX and scaleY are guaranteed to be equal since we lock the aspect ratio
            scale: floatToDisplay(this.scaleX),
        };
    }

    toFabric(size: Size): fabric.IText {
        this.makeAbsolute(size);
        return new fabric.IText(this.text, {
            ...this,
            fontFamily: TEXT_FONT_FAMILY,
            backgroundColor: SHAPE_MASK_COLOR,
            padding: TEXT_PADDING,
        });
    }

    makeNormal(size: Size): void {
        super.makeNormal(size);
        this.scaleX = xToNormalized(size, this.scaleX);
        this.scaleY = yToNormalized(size, this.scaleY);
    }

    makeAbsolute(size: Size): void {
        super.makeAbsolute(size);
        this.scaleX = xFromNormalized(size, this.scaleX);
        this.scaleY = yFromNormalized(size, this.scaleY);
    }
}

interface TextDataForCloze extends ShapeDataForCloze {
    text: string;
    scale: string;
}
