export interface DropdownItemProps {
    id?: string;
    className?: string;
    title: string;

    onClick: (event: MouseEvent) => void;
    label: string;
    endLabel: string;
}
