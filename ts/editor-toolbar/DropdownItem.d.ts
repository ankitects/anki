export interface DropdownItemProps {
    id?: string;
    className?: string;
    tooltip: string;

    onClick: (event: MouseEvent) => void;
    label: string;
    endLabel: string;
}
