export interface LabelButtonProps {
    id?: string;
    className?: string;

    label: string;
    tooltip: string;
    onClick: (event: MouseEvent) => void;
    disables?: boolean;
}
