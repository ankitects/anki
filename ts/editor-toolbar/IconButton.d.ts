export interface IconButtonProps {
    id?: string;
    className?: string;
    title: string;
    icon: string;
    onClick: (event: MouseEvent) => void;
}
