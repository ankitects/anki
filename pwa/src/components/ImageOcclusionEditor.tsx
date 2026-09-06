"use client";

import { useEffect, useRef, useState } from "react";

export type OcclusionMask = {
  id: string;
  left: number;
  top: number;
  width: number;
  height: number;
};

export type ImageOcclusionDraft = {
  image: File | null;
  masks: OcclusionMask[];
  header: string;
  backExtra: string;
  comments: string;
  hideAllGuessOne: boolean;
};

export const emptyImageOcclusionDraft: ImageOcclusionDraft = {
  image: null,
  masks: [],
  header: "",
  backExtra: "",
  comments: "",
  hideAllGuessOne: false
};

function clamp(value: number) { return Math.max(0, Math.min(1, value)); }

export function ImageOcclusionEditor({ value, disabled, onChange }: {
  value: ImageOcclusionDraft;
  disabled: boolean;
  onChange: (value: ImageOcclusionDraft) => void;
}) {
  const [imageUrl, setImageUrl] = useState("");
  const [drawing, setDrawing] = useState<OcclusionMask | null>(null);
  const start = useRef<{ x: number; y: number; pointer: number } | null>(null);

  useEffect(() => {
    if (!value.image) { setImageUrl(""); return; }
    const url = URL.createObjectURL(value.image);
    setImageUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [value.image]);

  const point = (event: React.PointerEvent<HTMLDivElement>) => {
    const bounds = event.currentTarget.getBoundingClientRect();
    return { x: clamp((event.clientX - bounds.left) / bounds.width), y: clamp((event.clientY - bounds.top) / bounds.height) };
  };

  const begin = (event: React.PointerEvent<HTMLDivElement>) => {
    if (disabled) return;
    const { x, y } = point(event);
    start.current = { x, y, pointer: event.pointerId };
    event.currentTarget.setPointerCapture(event.pointerId);
    setDrawing({ id: "drawing", left: x, top: y, width: 0, height: 0 });
  };

  const move = (event: React.PointerEvent<HTMLDivElement>) => {
    if (!start.current || start.current.pointer !== event.pointerId) return;
    const end = point(event);
    setDrawing({ id: "drawing", left: Math.min(start.current.x, end.x), top: Math.min(start.current.y, end.y),
      width: Math.abs(end.x - start.current.x), height: Math.abs(end.y - start.current.y) });
  };

  const finish = (event: React.PointerEvent<HTMLDivElement>) => {
    if (!start.current || start.current.pointer !== event.pointerId) return;
    event.currentTarget.releasePointerCapture(event.pointerId);
    if (drawing && drawing.width >= 0.015 && drawing.height >= 0.015) {
      onChange({ ...value, masks: [...value.masks, { ...drawing, id: crypto.randomUUID() }] });
    }
    start.current = null;
    setDrawing(null);
  };

  const masks = drawing ? [...value.masks, drawing] : value.masks;
  return (
    <div className="io-editor">
      <label className="media-picker" htmlFor="occlusion-image">
        <span>{value.image ? "Change image" : "Choose an image"}</span>
        <input id="occlusion-image" type="file" accept="image/jpeg,image/png,image/gif,image/webp,image/avif,image/svg+xml"
          disabled={disabled} onChange={(event) => {
            const image = event.target.files?.[0] ?? null;
            onChange({ ...value, image, masks: [] });
          }} />
      </label>
      {value.image && <p className="attachment-list">{value.image.name}</p>}
      {imageUrl && (
        <>
          <p className="io-instruction">Drag over each part you want to hide. Every rectangle creates one card.</p>
          <div className="io-editor-frame">
            {/* The user's local image is only previewed as an img; SVG scripts cannot execute here. */}
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={imageUrl} alt="Image to mask" draggable={false} />
            <div className="io-drawing-layer" onPointerDown={begin} onPointerMove={move} onPointerUp={finish} onPointerCancel={finish}>
              {masks.map((mask, index) => <span className="io-editor-mask" key={mask.id} style={{
                left: `${mask.left * 100}%`, top: `${mask.top * 100}%`, width: `${mask.width * 100}%`, height: `${mask.height * 100}%`
              }}><span>{index + 1}</span></span>)}
            </div>
          </div>
          <div className="io-mask-actions">
            <span>{value.masks.length} {value.masks.length === 1 ? "mask" : "masks"}</span>
            <button type="button" className="secondary-button" disabled={disabled || !value.masks.length}
              onClick={() => onChange({ ...value, masks: value.masks.slice(0, -1) })}>Undo mask</button>
            <button type="button" className="secondary-button" disabled={disabled || !value.masks.length}
              onClick={() => onChange({ ...value, masks: [] })}>Clear</button>
          </div>
        </>
      )}
      <label className="checkbox-label" htmlFor="hide-all-masks">
        <input id="hide-all-masks" type="checkbox" checked={value.hideAllGuessOne} disabled={disabled}
          onChange={(event) => onChange({ ...value, hideAllGuessOne: event.target.checked })} />
        Hide all, guess one
      </label>
      <label htmlFor="occlusion-header">Header</label>
      <textarea id="occlusion-header" rows={2} value={value.header} disabled={disabled}
        onChange={(event) => onChange({ ...value, header: event.target.value })} />
      <label htmlFor="occlusion-extra">Back extra</label>
      <textarea id="occlusion-extra" rows={3} value={value.backExtra} disabled={disabled}
        onChange={(event) => onChange({ ...value, backExtra: event.target.value })} />
      <label htmlFor="occlusion-comments">Comments</label>
      <textarea id="occlusion-comments" rows={2} value={value.comments} disabled={disabled}
        onChange={(event) => onChange({ ...value, comments: event.target.value })} />
    </div>
  );
}
