# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from aqt import colors, props
from aqt.theme import ThemeManager


def general_styles(tm: ThemeManager, buf: str) -> str:
    buf += f"""
QFrame {{
    background: none;
}}
QPushButton,
QComboBox,
QSpinBox,
QLineEdit,
QListWidget,
QTreeWidget,
QListView {{
    border: 1px solid {tm.var(colors.BORDER)};
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
QToolTip {{
    background: {tm.var(colors.CANVAS_OVERLAY)};
}}
    """
    return buf


def button_styles(tm: ThemeManager, buf: str) -> str:
    buf += f"""
QPushButton,
QTabBar::tab:!selected,
QComboBox:!editable {{
    background: qlineargradient(
        spread:pad, x1:0.5, y1:0, x2:0.5, y2:1,
        stop:0 {tm.var(colors.BUTTON_GRADIENT_START)},
        stop:1 {tm.var(colors.BUTTON_GRADIENT_END)}
    );

}}
QPushButton:hover,
QTabBar::tab:hover,
QComboBox:!editable:hover {{
    background: qlineargradient(
        spread:pad, x1:0.5, y1:0, x2:0.5, y2:1.25,
        stop:0 {tm.var(colors.BUTTON_HOVER_GRADIENT_START)},
        stop:1 {tm.var(colors.BUTTON_HOVER_GRADIENT_END)}
    );
}}
QPushButton:pressed,
QComboBox:!editable:pressed {{
    border: 1px solid {tm.var(colors.BUTTON_PRESSED_BORDER)};
    background: qlineargradient(
        spread:pad, x1:0.5, y1:0, x2:0.5, y2:1,
        stop:0 {tm.var(colors.BUTTON_PRESSED_SHADOW)},
        stop:0.1 {tm.var(colors.BUTTON_GRADIENT_START)},
        stop:0.9 {tm.var(colors.BUTTON_GRADIENT_END)},
        stop:1 {tm.var(colors.BUTTON_PRESSED_SHADOW)},
    );
}}
    """
    return buf


def combobox_styles(tm: ThemeManager, buf: str) -> str:
    buf += f"""
QComboBox {{
    padding: 1px 4px 2px 6px;
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
    margin: -1px;
    subcontrol-origin: padding;
    padding: 2px;
    width: 16px;
    subcontrol-position: top right;
    border: 1px solid {tm.var(colors.BUTTON_BORDER)};
    border-top-right-radius: {tm.var(props.BORDER_RADIUS)};
    border-bottom-right-radius: {tm.var(props.BORDER_RADIUS)};
}}
QComboBox::down-arrow {{
    image: url(icons:chevron-down.svg);
}}
QComboBox::drop-down {{
    background: qlineargradient(
        spread:pad, x1:0.5, y1:0, x2:0.5, y2:1,
        stop:0 {tm.var(colors.BUTTON_PRIMARY_GRADIENT_START)},
        stop:1 {tm.var(colors.BUTTON_PRIMARY_GRADIENT_END)}
    );
}}
QComboBox::drop-down:hover {{
    background: qlineargradient(
        spread:pad, x1:0.5, y1:0, x2:0.5, y2:1.25,
        stop:0 {tm.var(colors.BUTTON_PRIMARY_HOVER_GRADIENT_START)},
        stop:1 {tm.var(colors.BUTTON_PRIMARY_HOVER_GRADIENT_END)}
    );
}}
    """
    return buf


def tabwidget_styles(tm: ThemeManager, buf: str) -> str:
    buf += f"""
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
}}
QTabBar::tab:first {{
  border-top-left-radius: {tm.var(props.BORDER_RADIUS)};
  border-bottom-left-radius: {tm.var(props.BORDER_RADIUS)};
}}
QTabBar::tab:!first {{
  margin-left: -1px;
}}
QTabBar::tab:last {{
  border-top-right-radius: {tm.var(props.BORDER_RADIUS)};
  border-bottom-right-radius: {tm.var(props.BORDER_RADIUS)};
}}
QTabBar::tab:selected {{
    color: white;
    background: qlineargradient(
        spread:pad, x1:0.5, y1:0, x2:0.5, y2:1,
        stop:0 {tm.var(colors.BUTTON_PRIMARY_GRADIENT_START)},
        stop:1 {tm.var(colors.BUTTON_PRIMARY_GRADIENT_END)}
    );
}}
    """
    return buf


def table_styles(tm: ThemeManager, buf: str) -> str:
    buf += f"""
QTableView {{
    background: none;
    top: 2px;
    border-left: 1px solid {tm.var(colors.BORDER_SUBTLE)};
    border-radius: {tm.var(props.BORDER_RADIUS)};
    gridline-color: {tm.var(colors.BORDER_SUBTLE)}; 
}}
QHeaderView {{
    background: {tm.var(colors.CANVAS)};
}}
QHeaderView::section {{
    border: 1px solid {tm.var(colors.BORDER_SUBTLE)};
    background: qlineargradient(
        spread:pad, x1:0.5, y1:0, x2:0.5, y2:1,
        stop:0 {tm.var(colors.BUTTON_GRADIENT_START)},
        stop:1 {tm.var(colors.BUTTON_GRADIENT_END)}
    );
}}
QHeaderView::section:pressed {{
    border: 1px solid {tm.var(colors.BUTTON_PRESSED_BORDER)};
    background: qlineargradient(
        spread:pad, x1:0.5, y1:0, x2:0.5, y2:1,
        stop:0 {tm.var(colors.BUTTON_PRESSED_SHADOW)},
        stop:0.1 {tm.var(colors.BUTTON_GRADIENT_START)},
        stop:0.9 {tm.var(colors.BUTTON_GRADIENT_END)},
        stop:1 {tm.var(colors.BUTTON_PRESSED_SHADOW)},
    );
}}
QHeaderView::section:hover {{
    background: qlineargradient(
        spread:pad, x1:0.5, y1:0, x2:0.5, y2:1.25,
        stop:0 {tm.var(colors.BUTTON_HOVER_GRADIENT_START)},
        stop:1 {tm.var(colors.BUTTON_HOVER_GRADIENT_END)}
    );
}}
QHeaderView::section:first {{
    margin-left: -1px;
    border-top: 1px solid {tm.var(colors.BORDER_SUBTLE)}; 
    border-left: 1px solid {tm.var(colors.BORDER_SUBTLE)}; 
    border-top-left-radius: {tm.var(props.BORDER_RADIUS)};
}}
QHeaderView::section:!first {{
    border-left: none;
}}
QHeaderView::section:last {{
    border-top: 1px solid {tm.var(colors.BORDER_SUBTLE)}; 
    border-right: 1px solid {tm.var(colors.BORDER_SUBTLE)}; 
    border-top-right-radius: {tm.var(props.BORDER_RADIUS)};
}}
QHeaderView::section:next-selected {{
    border-right: none;
}}
QHeaderView::section:previous-selected {{
    border-left: none;
}}
QHeaderView::section:only-one {{
    border-left: 1px solid {tm.var(colors.BORDER_SUBTLE)}; 
    border-top: 1px solid {tm.var(colors.BORDER_SUBTLE)}; 
    border-right: 1px solid {tm.var(colors.CANVAS)};
    border-top-left-radius: {tm.var(props.BORDER_RADIUS)};
    border-top-right-radius: {tm.var(props.BORDER_RADIUS)};
}}
QHeaderView::up-arrow,
QHeaderView::down-arrow {{
    width: 20px;
    height: 20px;
}}
QHeaderView::up-arrow {{
    image: url(icons:menu-up.svg);
}}
QHeaderView::down-arrow {{
    image: url(icons:menu-down.svg);
}}
    """
    return buf


def spinbox_styles(tm: ThemeManager, buf: str) -> str:
    buf += f"""
QSpinBox::up-button,
QSpinBox::down-button {{
    subcontrol-origin: border;
    width: 16px;
    border: 1px solid {tm.var(colors.BUTTON_BORDER)};
    background: qlineargradient(
        spread:pad, x1:0.5, y1:0, x2:0.5, y2:1,
        stop:0 {tm.var(colors.BUTTON_PRIMARY_GRADIENT_START)},
        stop:1 {tm.var(colors.BUTTON_PRIMARY_GRADIENT_END)}
    );
}}
QSpinBox::up-button:pressed,
QSpinBox::down-button:pressed {{
    border: 1px solid {tm.var(colors.BUTTON_PRESSED_BORDER)};
    background: qlineargradient(
        spread:pad, x1:0.5, y1:0, x2:0.5, y2:1,
        stop:0 {tm.var(colors.BUTTON_PRESSED_SHADOW)},
        stop:0.1 {tm.var(colors.BUTTON_PRIMARY_GRADIENT_START)},
        stop:0.9 {tm.var(colors.BUTTON_PRIMARY_GRADIENT_END)},
        stop:1 {tm.var(colors.BUTTON_PRESSED_SHADOW)},
    );
}}
QSpinBox::up-button:hover,
QSpinBox::down-button:hover {{
    background: qlineargradient(
        spread:pad, x1:0.5, y1:0, x2:0.5, y2:1.25,
        stop:0 {tm.var(colors.BUTTON_PRIMARY_HOVER_GRADIENT_START)},
        stop:1 {tm.var(colors.BUTTON_PRIMARY_HOVER_GRADIENT_END)}
    );
}}
QSpinBox::up-button {{
    margin-bottom: -1px;
    subcontrol-position: top right;
    border-top-right-radius: {tm.var(props.BORDER_RADIUS)};
}}
QSpinBox::down-button {{
    margin-top: -1px;
    subcontrol-position: bottom right;
    border-bottom-right-radius: {tm.var(props.BORDER_RADIUS)};
}}
QSpinBox::up-arrow {{
    image: url(icons:chevron-up.svg);
}}
QSpinBox::down-arrow {{
    image: url(icons:chevron-down.svg);
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
   background: {tm.var(colors.BUTTON_PRIMARY_DISABLED)};
}}
     """
    return buf


def scrollbar_styles(tm: ThemeManager, buf: str) -> str:
    buf += f"""
QAbstractScrollArea::corner {{
    background: none;
    border: none;
}}
QScrollBar {{
    background-color: {tm.var(colors.CANVAS)};
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
    min-width: 50px;
}} 
QScrollBar:vertical {{
    width: 12px;
}}
QScrollBar::handle:vertical {{
    min-height: 50px;
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
    return buf


def win10_styles(tm: ThemeManager, buf: str) -> str:

    # day mode is missing a bottom border; background must be
    # also set for border to apply
    buf += f"""
QMenuBar {{
  border-bottom: 1px solid {tm.var(colors.BORDER)};
  background: {tm.var(colors.CANVAS) if tm.night_mode else "white"};
}}
    """

    # qt bug? setting the above changes the browser sidebar
    # to white as well, so set it back
    buf += f"""
QTreeWidget {{
  background: {tm.var(colors.CANVAS)};
}}
    """

    if tm.night_mode:
        buf += """
QToolTip {
  border: 0;
}
        """
    return buf
