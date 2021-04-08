export interface LabelButtonProps {
    id?: string;
    className?: string;

    label: string;
    title: string;
    onClick: (event: MouseEvent) => void;
    disables?: boolean;
}
