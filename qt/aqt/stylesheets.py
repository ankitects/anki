# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

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


def general_styles(tm: ThemeManager) -> str:
    return f"""
QFrame,
QWidget {{
    background: none;
}}
QPushButton,
QComboBox,
QSpinBox,
QLineEdit,
QListWidget,
QTreeWidget,
QListView {{
    border: 1px solid {tm.var(colors.BORDER_SUBTLE)};
    border-radius: {tm.var(props.BORDER_RADIUS)};
}}
QLineEdit {{
    padding: 2px;
}}
QLineEdit:focus {{
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
    """


def menu_styles(tm: ThemeManager) -> str:
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


def button_styles(tm: ThemeManager) -> str:
    return f"""
QPushButton {{ padding-left: 15px; padding-right: 15px; }}
QPushButton,
QTabBar::tab:!selected,
QComboBox:!editable,
QComboBox::drop-down:editable,
QSpinBox::up-button,
QSpinBox::down-button {{
    background: {tm.var(colors.BUTTON_BG)};
    border-bottom: 1px solid {tm.var(colors.SHADOW)};
}}
QPushButton:hover,
QTabBar::tab:hover,
QComboBox:!editable:hover,
QSpinBox::up-button,
QSpinBox::down-button {{
    background: {
        button_gradient(
            tm.var(colors.BUTTON_GRADIENT_START),
            tm.var(colors.BUTTON_GRADIENT_END),
        )
    };
}}
QPushButton:pressed,
QSpinBox::up-button,
QSpinBox::down-button {{
    background: {
        button_pressed_gradient(
            tm.var(colors.BUTTON_GRADIENT_START),
            tm.var(colors.BUTTON_GRADIENT_END),
            tm.var(colors.SHADOW)
        )
    };
}}
    """


def splitter_styles(tm: ThemeManager) -> str:
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


def combobox_styles(tm: ThemeManager) -> str:
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
QComboBox::drop-down:hover:editable {{
    background: {
        button_gradient(
            tm.var(colors.BUTTON_GRADIENT_START),
            tm.var(colors.BUTTON_GRADIENT_END),
        )
    };
}}
    """


def tabwidget_styles(tm: ThemeManager) -> str:
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
    """


def table_styles(tm: ThemeManager) -> str:
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


def spinbox_styles(tm: ThemeManager) -> str:
    return f"""
QSpinBox::up-button,
QSpinBox::down-button {{
    subcontrol-origin: border;
    width: 16px;
}}
QSpinBox::up-button {{
    margin-bottom: -1px;
    subcontrol-position: top right;
    border-top-{tm.right()}-radius: {tm.var(props.BORDER_RADIUS)};
}}
QSpinBox::down-button {{
    margin-top: -1px;
    subcontrol-position: bottom right;
    border-bottom-{tm.right()}-radius: {tm.var(props.BORDER_RADIUS)};
}}
QSpinBox::up-arrow {{
    image: url({tm.themed_icon("mdi:chevron-up")});
}}
QSpinBox::down-arrow {{
    image: url({tm.themed_icon("mdi:chevron-down")});
}}
QSpinBox::up-arrow,
QSpinBox::down-arrow,
QSpinBox::up-arrow:pressed,
QSpinBox::down-arrow:pressed,
QSpinBox::up-arrow:disabled:hover, QSpinBox::up-arrow:off:hover,
QSpinBox::down-arrow:disabled:hover, QSpinBox::down-arrow:off:hover {{
    width: 16px;
    height: 16px;
}}
QSpinBox::up-arrow:hover,
QSpinBox::down-arrow:hover {{
    width: 20px;
    height: 20px;
}}
QSpinBox::up-button:disabled, QSpinBox::up-button:off,
QSpinBox::down-button:disabled, QSpinBox::down-button:off {{
   background: {tm.var(colors.BUTTON_DISABLED)};
}}
QSpinBox::up-arrow:off,
QSpinBox::down-arrow:off {{
    image: url({tm.themed_icon("mdi:chevron-down-FG_DISABLED")});
}}
     """


def checkbox_styles(tm: ThemeManager) -> str:
    return f"""
QCheckBox,
QRadioButton {{
    spacing: 8px;
    margin: 2px 0;
}}
QCheckBox::indicator,
QRadioButton::indicator,
QMenu::indicator {{
    border: 1px solid {tm.var(colors.BORDER_SUBTLE)};
    border-radius: {tm.var(props.BORDER_RADIUS)};
    background: {tm.var(colors.CANVAS_INSET)};
    width: 16px;
    height: 16px;
}}
QRadioButton::indicator,
QMenu::indicator:exclusive {{
    border-radius: 8px;
}}
QCheckBox::indicator:hover,
QCheckBox::indicator:checked:hover,
QRadioButton::indicator:hover,
QRadioButton::indicator:checked:hover {{
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
    """


def scrollbar_styles(tm: ThemeManager) -> str:
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
