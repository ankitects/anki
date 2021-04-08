export interface IconButtonProps {
    id?: string;
    className?: string;
    tooltip: string;
    icon: string;
    onClick: (event: MouseEvent) => void;
}
