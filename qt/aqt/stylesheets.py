# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from anki.utils import is_win
from aqt import colors, props
from aqt.theme import ThemeManager


def button_gradient(start: str, end: str) -> str:
    return f"""
qlineargradient(
    spread:pad, x1:0.5, y1:0, x2:0.5, y2:1,
    stop:0 {start},
    stop:1 {end}
);
    """


def button_pressed_gradient(start: str, end: str, shadow: str) -> str:
    return f"""
qlineargradient(
    spread:pad, x1:0.5, y1:0, x2:0.5, y2:1,
    stop:0 {shadow},
    stop:0.1 {start},
    stop:0.9 {end},
    stop:1 {shadow}
);
    """


class CustomStyles:
    def general(self, tm: ThemeManager) -> str:
        return f"""
    QFrame,
    QWidget {{
        background: none;
    }}
    QPushButton,
    QComboBox,
    QSpinBox,
    QDateTimeEdit,
    QLineEdit,
    QListWidget,
    QTreeWidget,
    QListView,
    QTextEdit,
    QPlainTextEdit {{
        border: 1px solid {tm.var(colors.BORDER_SUBTLE)};
        border-radius: {tm.var(props.BORDER_RADIUS)};
    }}
    QLineEdit,
    QTextEdit,
    QPlainTextEdit,
    QDateTimeEdit,
    QListWidget,
    QTreeWidget,
    QListView {{
        background: {tm.var(colors.CANVAS_CODE)};
    }}
    QLineEdit,
    QTextEdit,
    QPlainTextEdit,
    QDateTimeEdit {{
        padding: 2px;
    }}
    QSpinBox:focus,
    QDateTimeEdit:focus,
    QLineEdit:focus,
    QTextEdit:editable:focus,
    QPlainTextEdit:editable:focus,
    QWidget:editable:focus {{
        border-color: {tm.var(colors.BORDER_FOCUS)};
    }}
    QPushButton {{
        margin-top: 1px;
    }}
    QPushButton,
    QComboBox,
    QSpinBox {{
        padding: 2px 6px;
    }}
    QGroupBox {{
        text-align: center;
        font-weight: bold;
        border: 1px solid {tm.var(colors.BORDER_SUBTLE)};
        padding: 0.75em 0 0.75em 0;
        background: {tm.var(colors.CANVAS_ELEVATED)};
        border-radius: {tm.var(props.BORDER_RADIUS)};
        margin-top: 10px;
    }}
    QGroupBox#preview_box,
    QGroupBox#template_box {{
        background: none;
        border: none;
    }}
    QGroupBox::title {{
        subcontrol-origin: margin;
        subcontrol-position: top left;
        margin: 0 2px;
        left: 15px;
    }}
    QGroupBox#preview_box::title,
    QGroupBox#template_box::title {{
        margin-top: 5px;
        left: 5px;
    }}
    QLabel:disabled {{
        color: {tm.var(colors.FG_DISABLED)};
    }}
    QToolTip {{ color: {tm.var(colors.FG)}; background-color: {tm.var(colors.CANVAS)}; }} 
        """

    def menu(self, tm: ThemeManager) -> str:
        return f"""
    QMenuBar {{
        border-bottom: 1px solid {tm.var(colors.BORDER_SUBTLE)};
    }}
    QMenuBar::item {{
        background-color: transparent;
        padding: 2px 4px;
        border-radius: {tm.var(props.BORDER_RADIUS)};
    }}
    QMenuBar::item:selected {{
        background-color: {tm.var(colors.CANVAS_ELEVATED)};
    }}
    QMenu {{
        background-color: {tm.var(colors.CANVAS_OVERLAY)};
        border: 1px solid {tm.var(colors.BORDER_SUBTLE)};
        padding: 4px;
    }}
    QMenu::item {{
        background-color: transparent;
        padding: 3px 14px;
        margin-bottom: 4px;
    }}
    QMenu::item:selected {{
        background-color: {tm.var(colors.HIGHLIGHT_BG)};
        border-radius: {tm.var(props.BORDER_RADIUS)};
    }}
    QMenu::separator {{
        height: 1px;
        background: {tm.var(colors.BORDER_SUBTLE)};
        margin: 0 8px 4px 8px;
    }}
    QMenu::indicator {{
        border: 1px solid {tm.var(colors.BORDER)};
        margin-{tm.left()}: 6px;
        margin-{tm.right()}: -6px;
    }}
        """

    def button(self, tm: ThemeManager) -> str:
        # For some reason, Windows needs a larger padding to look the same
        button_pad = 25 if is_win else 15
        return f"""
    QPushButton {{ padding-left: {button_pad}px; padding-right: {button_pad}px; }}
    QPushButton,
    QTabBar::tab:!selected,
    QComboBox:!editable,
    QComboBox::drop-down:editable {{
        background: {tm.var(colors.BUTTON_BG)};
        border-bottom: 1px solid {tm.var(colors.SHADOW)};
    }}
    QPushButton:default {{
        border: 1px solid {tm.var(colors.BORDER_FOCUS)};
    }}
    QPushButton:focus {{
        border: 2px solid {tm.var(colors.BORDER_FOCUS)};
        outline: none;
    }}
    QPushButton:hover,
    QTabBar::tab:hover,
    QComboBox:!editable:hover,
    QSpinBox::up-button:hover,
    QSpinBox::down-button:hover,
    QDateTimeEdit::up-button:hover,
    QDateTimeEdit::down-button:hover {{
        background: {
            button_gradient(
                tm.var(colors.BUTTON_GRADIENT_START),
                tm.var(colors.BUTTON_GRADIENT_END),
            )
        };
    }}
    QPushButton:default:hover {{
        border-width: 2px;
    }}
    QPushButton:pressed,
    QPushButton:checked,
    QSpinBox::up-button:pressed,
    QSpinBox::down-button:pressed,
    QDateTimeEdit::up-button:pressed,
    QDateTimeEdit::down-button:pressed {{
        background: {
            button_pressed_gradient(
                tm.var(colors.BUTTON_GRADIENT_START),
                tm.var(colors.BUTTON_GRADIENT_END),
                tm.var(colors.SHADOW)
            )
        };
    }}
    QPushButton:flat {{
        border: none;
    }}
        """

    def splitter(self, tm: ThemeManager) -> str:
        return f"""
    QSplitter::handle,
    QMainWindow::separator {{
        height: 16px;
    }}
    QSplitter::handle:vertical,
    QMainWindow::separator:horizontal {{
        image: url({tm.themed_icon("mdi:drag-horizontal-FG_SUBTLE")});
    }}
    QSplitter::handle:horizontal,
    QMainWindow::separator:vertical {{
        image: url({tm.themed_icon("mdi:drag-vertical-FG_SUBTLE")});
    }}
    """

    def combobox(self, tm: ThemeManager) -> str:
        return f"""
    QComboBox {{
        padding: {"1px 6px 2px 4px" if tm.rtl() else "1px 4px 2px 6px"};
    }}
    QComboBox:focus {{
        border-color: {tm.var(colors.BORDER_FOCUS)};
    }}
    QComboBox:editable:on,
    QComboBox:editable:focus,
    QComboBox::drop-down:focus:editable,
    QComboBox::drop-down:pressed {{
        border-color: {tm.var(colors.BORDER_FOCUS)};
    }}
    QComboBox:on {{
        border-bottom: none;
        border-bottom-right-radius: 0;
        border-bottom-left-radius: 0;
    }}
    QComboBox::item {{
        color: {tm.var(colors.FG)};
        background: {tm.var(colors.CANVAS_ELEVATED)};
    }}

    QComboBox::item:selected {{
        background: {tm.var(colors.HIGHLIGHT_BG)};
        color: {tm.var(colors.HIGHLIGHT_FG)};
    }}
    QComboBox::item::icon:selected {{
        position: absolute;
    }}
    QComboBox::drop-down {{
        subcontrol-origin: border;
        padding: 2px;
        padding-left: 4px;
        padding-right: 4px;
        width: 16px;
        subcontrol-position: top right;
        border: 1px solid {tm.var(colors.BORDER_SUBTLE)};
        border-top-{tm.right()}-radius: {tm.var(props.BORDER_RADIUS)};
        border-bottom-{tm.right()}-radius: {tm.var(props.BORDER_RADIUS)};
    }}
    QComboBox::drop-down:!editable {{
        background: none;
        border-color: transparent;
    }}
    QComboBox::down-arrow {{
        image: url({tm.themed_icon("mdi:chevron-down")});
    }}
    QComboBox::down-arrow:disabled {{
        image: url({tm.themed_icon("mdi:chevron-down-FG_DISABLED")});
    }}
    QComboBox::drop-down:hover:editable {{
        background: {
            button_gradient(
                tm.var(colors.BUTTON_GRADIENT_START),
                tm.var(colors.BUTTON_GRADIENT_END),
            )
        };
    }}
        """

    def tabwidget(self, tm: ThemeManager) -> str:
        return f"""
    QTabWidget {{
        border-radius: {tm.var(props.BORDER_RADIUS)};
        background: none;
    }}
    QTabWidget::pane {{
        top: -15px;
        padding-top: 1em;
        background: {tm.var(colors.CANVAS_ELEVATED)};
        border: 1px solid {tm.var(colors.BORDER_SUBTLE)};
        border-radius: {tm.var(props.BORDER_RADIUS)};
    }}
    QTabWidget::tab-bar {{
        alignment: center;
    }}
    QTabBar::tab {{
        background: none;
        padding: 4px 8px;
        min-width: 8ex;
    }}
    QTabBar::tab {{
        border: 1px solid {tm.var(colors.BORDER_SUBTLE)};
        border-bottom-color: {tm.var(colors.SHADOW)};
    }}
    QTabBar::tab:first {{
        border-top-{tm.left()}-radius: {tm.var(props.BORDER_RADIUS)};
        border-bottom-{tm.left()}-radius: {tm.var(props.BORDER_RADIUS)};
    }}
    QTabBar::tab:!first {{
        margin-{tm.left()}: -1px;
    }}
    QTabBar::tab:last {{
        border-top-{tm.right()}-radius: {tm.var(props.BORDER_RADIUS)};
        border-bottom-{tm.right()}-radius: {tm.var(props.BORDER_RADIUS)};
    }}
    QTabBar::tab:selected {{
        color: white;
        background: {tm.var(colors.BUTTON_PRIMARY_BG)};
    }}
    QTabBar::tab:selected:hover {{
        background: {
                button_gradient(
                tm.var(colors.BUTTON_PRIMARY_GRADIENT_START),
                tm.var(colors.BUTTON_PRIMARY_GRADIENT_END),
            )
        };
    }}
    QTabBar::tab:focus {{
        outline: none;
    }}
    QTabBar::tab:disabled,
    QTabBar::tab:disabled:hover {{
        background: {tm.var(colors.BUTTON_DISABLED)};
        color: {tm.var(colors.FG_DISABLED)};
    }}
    QTabBar::tab:selected:disabled,
    QTabBar::tab:selected:hover:disabled {{
        background: {tm.var(colors.BUTTON_PRIMARY_DISABLED)};
    }}
        """

    def table(self, tm: ThemeManager) -> str:
        return f"""
    QTableView {{
        border-radius: {tm.var(props.BORDER_RADIUS)};
        border-{tm.left()}: 1px solid {tm.var(colors.BORDER_SUBTLE)};
        border-bottom: 1px solid {tm.var(colors.BORDER_SUBTLE)};
        border-bottom-left-radius: 0;
        border-bottom-right-radius: 0;
        gridline-color: {tm.var(colors.BORDER_SUBTLE)};
        selection-background-color: {tm.var(colors.SELECTED_BG)};
        selection-color: {tm.var(colors.SELECTED_FG)};
        background: {tm.var(colors.CANVAS_CODE)};
    }}
    QHeaderView {{
        background: {tm.var(colors.CANVAS)};
    }}
    QHeaderView::section {{
        padding-{tm.left()}: 0px;
        padding-{tm.right()}: 15px;
        border: 1px solid {tm.var(colors.BORDER_SUBTLE)};
        background: {tm.var(colors.BUTTON_BG)};
    }}
    QHeaderView::section:first {{
        margin-left: -1px;
    }}
    QHeaderView::section:pressed,
    QHeaderView::section:pressed:!first {{
        background: {
            button_pressed_gradient(
                tm.var(colors.BUTTON_GRADIENT_START),
                tm.var(colors.BUTTON_GRADIENT_END),
                tm.var(colors.SHADOW)
            )
        }
    }}
    QHeaderView::section:hover {{
        background: {
            button_gradient(
                tm.var(colors.BUTTON_GRADIENT_START),
                tm.var(colors.BUTTON_GRADIENT_END),
            )
        };
    }}
    QHeaderView::section:first {{
        border-left: 1px solid {tm.var(colors.BORDER_SUBTLE)}; 
        border-top-left-radius: {tm.var(props.BORDER_RADIUS)};
    }}
    QHeaderView::section:!first {{
        border-left: none;
    }}
    QHeaderView::section:last {{
        border-right: 1px solid {tm.var(colors.BORDER_SUBTLE)}; 
        border-top-right-radius: {tm.var(props.BORDER_RADIUS)};
    }}
    QHeaderView::section:only-one {{
        border-left: 1px solid {tm.var(colors.BORDER_SUBTLE)}; 
        border-right: 1px solid {tm.var(colors.BORDER_SUBTLE)};
        border-top-left-radius: {tm.var(props.BORDER_RADIUS)};
        border-top-right-radius: {tm.var(props.BORDER_RADIUS)};
    }}
    QHeaderView::up-arrow,
    QHeaderView::down-arrow {{
        width: 20px;
        height: 20px;
        margin-{tm.left()}: -20px;
    }}
    QHeaderView::up-arrow {{
        image: url({tm.themed_icon("mdi:menu-up")});
    }}
    QHeaderView::down-arrow {{
        image: url({tm.themed_icon("mdi:menu-down")});
    }}
        """

    def spinbox(self, tm: ThemeManager) -> str:
        return f"""
    QSpinBox::up-button,
    QSpinBox::down-button,
    QDateTimeEdit::up-button,
    QDateTimeEdit::down-button {{
        subcontrol-origin: border;
        width: 16px;
        margin: 1px;
    }}
    QSpinBox::up-button,
    QDateTimeEdit::up-button {{
        margin-bottom: -1px;
        subcontrol-position: top right;
        border-top-{tm.right()}-radius: {tm.var(props.BORDER_RADIUS)};
    }}
    QSpinBox::down-button,
    QDateTimeEdit::down-button {{
        margin-top: -1px;
        subcontrol-position: bottom right;
        border-bottom-{tm.right()}-radius: {tm.var(props.BORDER_RADIUS)};
    }}
    QSpinBox::up-arrow,
    QDateTimeEdit::up-arrow {{
        image: url({tm.themed_icon("mdi:chevron-up")});
    }}
    QSpinBox::down-arrow,
    QDateTimeEdit::down-arrow {{
        image: url({tm.themed_icon("mdi:chevron-down")});
    }}
    QSpinBox::up-arrow,
    QSpinBox::down-arrow,
    QSpinBox::up-arrow:pressed,
    QSpinBox::down-arrow:pressed,
    QSpinBox::up-arrow:disabled:hover, QSpinBox::up-arrow:off:hover,
    QSpinBox::down-arrow:disabled:hover, QSpinBox::down-arrow:off:hover,
    QDateTimeEdit::up-arrow,
    QDateTimeEdit::down-arrow,
    QDateTimeEdit::up-arrow:pressed,
    QDateTimeEdit::down-arrow:pressed,
    QDateTimeEdit::up-arrow:disabled:hover, QDateTimeEdit::up-arrow:off:hover,
    QDateTimeEdit::down-arrow:disabled:hover, QDateTimeEdit::down-arrow:off:hover {{
        width: 16px;
        height: 16px;
    }}
    QSpinBox::up-arrow:hover,
    QSpinBox::down-arrow:hover,
    QDateTimeEdit::up-arrow:hover,
    QDateTimeEdit::down-arrow:hover {{
        width: 20px;
        height: 20px;
    }}
    QSpinBox::up-arrow:disabled, QSpinBox::up-arrow:off,
    QDateTimeEdit::up-arrow:disabled, QDateTimeEdit::up-arrow:off {{
        image: url({tm.themed_icon("mdi:chevron-up-FG_DISABLED")});
    }}
    QSpinBox::down-arrow:disabled, QSpinBox::down-arrow:off,
    QDateTimeEdit::down-arrow:disabled, QDateTimeEdit::down-arrow:off {{
        image: url({tm.themed_icon("mdi:chevron-down-FG_DISABLED")});
    }}
        """

    def checkbox(self, tm: ThemeManager) -> str:
        return f"""
    QCheckBox,
    QRadioButton {{
        spacing: 8px;
        margin: 2px 0;
    }}
    QCheckBox::indicator,
    QRadioButton::indicator,
    QMenu::indicator {{
        border: 1px solid {tm.var(colors.BORDER)};
        border-radius: {tm.var(props.BORDER_RADIUS)};
        background: {tm.var(colors.CANVAS_ELEVATED)};
        width: 16px;
        height: 16px;
    }}
    QRadioButton::indicator,
    QMenu::indicator:exclusive {{
        border-radius: 8px;
    }}
    QCheckBox::indicator:focus:!disabled,
    QCheckBox::indicator:hover:!disabled,
    QCheckBox::indicator:checked:hover:!disabled,
    QRadioButton::indicator:focus:!disabled,
    QRadioButton::indicator:hover:!disabled,
    QRadioButton::indicator:checked::!disabled {{
        border: 2px solid {tm.var(colors.BORDER_STRONG)};
        width: 14px;
        height: 14px;
    }}
    QCheckBox::indicator:checked,
    QRadioButton::indicator:checked,
    QMenu::indicator:checked {{
        image: url({tm.themed_icon("mdi:check")});
    }}
    QRadioButton::indicator:checked {{
        image: url({tm.themed_icon("mdi:circle-medium")});
    }}
    QCheckBox::indicator:indeterminate {{
        image: url({tm.themed_icon("mdi:minus-thick")});
    }}
    QCheckBox:disabled,
    QRadioButton:disabled {{
        color: {tm.var(colors.FG_DISABLED)};
    }}
    QCheckBox::indicator:disabled,
    QRadioButton::indicator:disabled,
    QMenu:indicator:disabled {{
        color: {tm.var(colors.FG_DISABLED)};
        border-color: {tm.var(colors.FG_DISABLED)};
    }}
    QCheckBox::indicator:checked:disabled,
    QRadioButton::indicator:checked:disabled,
    QMenu::indicator:checked:disabled {{
        image: url({tm.themed_icon("mdi:check-FG_DISABLED")});
    }}
    QRadioButton::indicator:checked:disabled {{
        image: url({tm.themed_icon("mdi:circle-medium-FG_DISABLED")});
    }}
    QCheckBox::indicator:indeterminate:disabled {{
        image: url({tm.themed_icon("mdi:minus-thick-FG_DISABLED")});
    }}
        """

    def scrollbar(self, tm: ThemeManager) -> str:
        return f"""
    QAbstractScrollArea::corner {{
        background: none;
        border: none;
    }}
    QScrollBar {{
        subcontrol-origin: content;
        background-color: transparent;
    }}
    QScrollBar::handle {{
        border-radius: {tm.var(props.BORDER_RADIUS)};
        background-color: {tm.var(colors.SCROLLBAR_BG)};
    }}
    QScrollBar::handle:hover {{
        background-color: {tm.var(colors.SCROLLBAR_BG_HOVER)};
    }}
    QScrollBar::handle:pressed {{
        background-color: {tm.var(colors.SCROLLBAR_BG_ACTIVE)};
    }} 
    QScrollBar:horizontal {{
        height: 12px;
    }}
    QScrollBar::handle:horizontal {{
        min-width: 60px;
    }} 
    QScrollBar:vertical {{
        width: 12px;
    }}
    QScrollBar::handle:vertical {{
        min-height: 60px;
    }} 
    QScrollBar::add-line {{
        border: none;
        background: none;
    }}
    QScrollBar::sub-line {{
        border: none;
        background: none;
    }}
        """

    def slider(self, tm: ThemeManager) -> str:
        return f"""
    QSlider::horizontal {{
        height: 20px;
    }}
    QSlider::vertical {{
        width: 20px;
    }}
    QSlider::groove {{
        border: 1px solid {tm.var(colors.BORDER_SUBTLE)};
        border-radius: 3px;
        background: {tm.var(colors.CANVAS_ELEVATED)};
    }}
    QSlider::sub-page {{
        background: {tm.var(colors.BUTTON_PRIMARY_GRADIENT_START)};
        border-radius: 3px;
        margin: 1px;
    }}
    QSlider::sub-page:disabled {{
        background: {tm.var(colors.BUTTON_DISABLED)};
    }}
    QSlider::add-page {{
        margin-{tm.right()}: 2px;
    }}
    QSlider::groove:vertical {{
        width: 6px;
    }}
    QSlider::groove:horizontal {{
        height: 6px;
    }}
    QSlider::handle {{
        background: {tm.var(colors.BUTTON_BG)};
        border: 1px solid {tm.var(colors.BORDER)};
        border-radius: 9px;
        width: 18px;
        height: 18px;
        border-bottom-color: {tm.var(colors.SHADOW)};
    }}
    QSlider::handle:vertical {{
        margin: 0 -7px;
    }}
    QSlider::handle:horizontal {{
        margin: -7px 0;
    }}
    QSlider::handle:hover {{
        background: {button_gradient(
            tm.var(colors.BUTTON_GRADIENT_START),
            tm.var(colors.BUTTON_GRADIENT_END),
        )}
    }}
    """


custom_styles = CustomStyles()
