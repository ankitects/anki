from aqt import colors
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
    border: 1px solid {tm.color(colors.BORDER)};
    border-radius: 5px;
}}
QLineEdit {{
    padding: 2px;
}}
QLineEdit:focus {{
    border-color: {tm.color(colors.FOCUS_BORDER)};
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
    background: {tm.color(colors.TOOLTIP_BG)};
}}
    """
    return buf


def button_styles(tm: ThemeManager, buf: str) -> str:
    buf += f"""
QPushButton,
QComboBox:!editable {{
    background: qlineargradient(
        spread:pad, x1:0.5, y1:0, x2:0.5, y2:1,
        stop:0 {tm.color(colors.BUTTON_GRADIENT_START)},
        stop:1 {tm.color(colors.BUTTON_GRADIENT_END)}
    );

}}
QPushButton:hover,
QComboBox:!editable:hover {{
    background: qlineargradient(
        spread:pad, x1:0.5, y1:0, x2:0.5, y2:1.25,
        stop:0 {tm.color(colors.BUTTON_HOVER_GRADIENT_START)},
        stop:1 {tm.color(colors.BUTTON_HOVER_GRADIENT_END)}
    );
}}
QPushButton:pressed,
QComboBox:!editable:pressed {{
    border: 1px solid {tm.color(colors.BUTTON_PRESSED_BORDER)};
    background: qlineargradient(
        spread:pad, x1:0.5, y1:0, x2:0.5, y2:1,
        stop:0 {tm.color(colors.BUTTON_PRESSED_SHADOW)},
        stop:0.1 {tm.color(colors.BUTTON_GRADIENT_START)},
        stop:0.9 {tm.color(colors.BUTTON_GRADIENT_END)},
        stop:1 {tm.color(colors.BUTTON_PRESSED_SHADOW)},
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
    border-color: {tm.color(colors.FOCUS_BORDER)};
}}
QComboBox:on {{
    border-bottom: none;
    border-bottom-right-radius: 0;
    border-bottom-left-radius: 0;
}}
QComboBox::item {{
    color: {tm.color(colors.TEXT_FG)};
    background: {tm.color(colors.FRAME_BG)};
}}

QComboBox::item:selected {{
    background: {tm.color(colors.HIGHLIGHT_BG)};
    color: {tm.color(colors.HIGHLIGHT_FG)};
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
    border: 1px solid {tm.color(colors.BUTTON_BORDER)};
    border-top-right-radius: 5px;
    border-bottom-right-radius: 5px;
}}
QComboBox::down-arrow {{
    image: url(icons:chevron-down.svg);
}}
QComboBox::drop-down {{
    background: qlineargradient(
        spread:pad, x1:0.5, y1:0, x2:0.5, y2:1,
        stop:0 {tm.color(colors.BUTTON_PRIMARY_GRADIENT_START)},
        stop:1 {tm.color(colors.BUTTON_PRIMARY_GRADIENT_END)}
    );
}}
QComboBox::drop-down:hover {{
    background: qlineargradient(
        spread:pad, x1:0.5, y1:0, x2:0.5, y2:1.25,
        stop:0 {tm.color(colors.BUTTON_PRIMARY_HOVER_GRADIENT_START)},
        stop:1 {tm.color(colors.BUTTON_PRIMARY_HOVER_GRADIENT_END)}
    );
}}
    """
    return buf


def tabwidget_styles(tm: ThemeManager, buf: str) -> str:
    buf += f"""
QTabWidget {{
  border-radius: 5px;
  border: none;
  background: none;
}}
QTabWidget::pane {{
  border: 1px solid {tm.color(colors.FRAME_BG)};
  border-radius: 5px;
  background: {tm.color(colors.FRAME_BG)};
}}
QTabWidget::tab-bar {{
    alignment: center;
}}
QTabBar::tab {{
  background: none;
  border-top-left-radius: 5px;
  border-top-right-radius: 5px;
  padding: 5px 10px;
  margin-bottom: 0px;
}}
QTabBar::tab:!selected:hover,
QTabBar::tab:selected {{
    background: {tm.color(colors.FRAME_BG)};
}}
QTabBar::tab:selected {{
  margin-bottom: -1px;
}}
QTabBar::tab:!selected {{
    margin-top: 5px;
    background: {tm.color(colors.WINDOW_BG)};
}}
QTabBar::tab {{
    min-width: 8ex;
    padding: 5px 10px 5px 10px;
}}
QTabBar::tab:selected {{
    border-bottom-color: none;
}}
QTabBar::tab:bottom:selected {{
    border-top-color: none;
}}
QTabBar::tab:previous-selected {{
    border-top-left-radius: 0;
}}
QTabBar::tab:next-selected {{
    border-top-right-radius: 0;
}}
    """
    return buf


def table_styles(tm: ThemeManager, buf: str) -> str:
    buf += f"""
QTableView {{
    margin: -1px -1px 1px -1px;
    background: none;
    border: 2px solid {tm.color(colors.WINDOW_BG)};
    border-radius: 5px;
}}
QHeaderView::section {{
    border: 2px solid {tm.color(colors.WINDOW_BG)};
    margin: -1px;
    background: qlineargradient(
        spread:pad, x1:0.5, y1:0, x2:0.5, y2:1,
        stop:0 {tm.color(colors.BUTTON_GRADIENT_START)},
        stop:1 {tm.color(colors.BUTTON_GRADIENT_END)}
    );
}}
QHeaderView::section:pressed {{
    border: 1px solid {tm.color(colors.BUTTON_PRESSED_BORDER)};
    background: qlineargradient(
        spread:pad, x1:0.5, y1:0, x2:0.5, y2:1,
        stop:0 {tm.color(colors.BUTTON_PRESSED_SHADOW)},
        stop:0.1 {tm.color(colors.BUTTON_GRADIENT_START)},
        stop:0.9 {tm.color(colors.BUTTON_GRADIENT_END)},
        stop:1 {tm.color(colors.BUTTON_PRESSED_SHADOW)},
    );
}}
QHeaderView::section:hover {{
    background: qlineargradient(
        spread:pad, x1:0.5, y1:0, x2:0.5, y2:1.25,
        stop:0 {tm.color(colors.BUTTON_HOVER_GRADIENT_START)},
        stop:1 {tm.color(colors.BUTTON_HOVER_GRADIENT_END)}
    );
}}
QHeaderView::section:first {{
    border-top: 2px solid {tm.color(colors.WINDOW_BG)};
    border-left: 2px solid {tm.color(colors.WINDOW_BG)};
    border-top-left-radius: 5px;
}}
QHeaderView::section:!first {{
    border-left: none;
}}
QHeaderView::section:last {{
    border-top: 2px solid {tm.color(colors.WINDOW_BG)};
    border-right: 2px solid {tm.color(colors.WINDOW_BG)};
    border-top-right-radius: 5px;
}}
QHeaderView::section:next-selected {{
    border-right: none;
}}
QHeaderView::section:previous-selected {{
    border-left: none;
}}
QHeaderView::section:only-one {{
    border-left: 2px solid {tm.color(colors.WINDOW_BG)};
    border-top: 2px solid {tm.color(colors.WINDOW_BG)};
    border-right: 2px solid {tm.color(colors.WINDOW_BG)};
    border-top-left-radius: 5px;
    border-top-right-radius: 5px;
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
    border: 1px solid {tm.color(colors.BUTTON_BORDER)};
    background: qlineargradient(
        spread:pad, x1:0.5, y1:0, x2:0.5, y2:1,
        stop:0 {tm.color(colors.BUTTON_PRIMARY_GRADIENT_START)},
        stop:1 {tm.color(colors.BUTTON_PRIMARY_GRADIENT_END)}
    );
}}
QSpinBox::up-button:pressed,
QSpinBox::down-button:pressed {{
    border: 1px solid {tm.color(colors.BUTTON_PRESSED_BORDER)};
    background: qlineargradient(
        spread:pad, x1:0.5, y1:0, x2:0.5, y2:1,
        stop:0 {tm.color(colors.BUTTON_PRESSED_SHADOW)},
        stop:0.1 {tm.color(colors.BUTTON_PRIMARY_GRADIENT_START)},
        stop:0.9 {tm.color(colors.BUTTON_PRIMARY_GRADIENT_END)},
        stop:1 {tm.color(colors.BUTTON_PRESSED_SHADOW)},
    );
}}
QSpinBox::up-button:hover,
QSpinBox::down-button:hover {{
    background: qlineargradient(
        spread:pad, x1:0.5, y1:0, x2:0.5, y2:1.25,
        stop:0 {tm.color(colors.BUTTON_PRIMARY_HOVER_GRADIENT_START)},
        stop:1 {tm.color(colors.BUTTON_PRIMARY_HOVER_GRADIENT_END)}
    );
}}
QSpinBox::up-button {{
    margin-bottom: -1px;
    subcontrol-position: top right;
    border-top-right-radius: 5px;
}}
QSpinBox::down-button {{
    margin-top: -1px;
    subcontrol-position: bottom right;
    border-bottom-right-radius: 5px;
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
   background: {tm.color(colors.BUTTON_PRIMARY_DISABLED)};
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
    background-color: {tm.color(colors.WINDOW_BG)};
}}
QScrollBar::handle {{
    border-radius: 5px;
    background-color: {tm.color(colors.SCROLLBAR_BG)};
}}
QScrollBar::handle:hover {{
    background-color: {tm.color(colors.SCROLLBAR_HOVER_BG)};
}}
QScrollBar::handle:pressed {{
    background-color: {tm.color(colors.SCROLLBAR_ACTIVE_BG)};
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
  border-bottom: 1px solid {tm.color(colors.BORDER)};
  background: {tm.color(colors.WINDOW_BG) if tm.night_mode else "white"};
}}
    """

    # qt bug? setting the above changes the browser sidebar
    # to white as well, so set it back
    buf += f"""
QTreeWidget {{
  background: {tm.color(colors.WINDOW_BG)};
}}
    """

    if tm.night_mode:
        buf += """
QToolTip {
  border: 0;
}
        """
    return buf
