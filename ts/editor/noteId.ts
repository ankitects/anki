let currentNoteId: number | null = null;

export function setNoteId(id: number): void {
    currentNoteId = id;
}

export function getNoteId(): number | null {
    return currentNoteId;
}
