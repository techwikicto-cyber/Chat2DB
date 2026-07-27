import { memo, useEffect } from 'react';
import { createGlobalStyle, css, createStyles } from 'antd-style';
import iconEdit from '../../node_modules/@chat2db/ui/es/ThemeProvider/fonts/icon-editor.woff2';
// Vazirmatn is bundled from node_modules rather than loaded from a CDN, so the
// Persian UI renders correctly on air-gapped servers and inside Docker.
import vazirmatnRegular from 'vazirmatn/fonts/webfonts/Vazirmatn-Regular.woff2';
import vazirmatnMedium from 'vazirmatn/fonts/webfonts/Vazirmatn-Medium.woff2';
import vazirmatnSemiBold from 'vazirmatn/fonts/webfonts/Vazirmatn-SemiBold.woff2';
import vazirmatnBold from 'vazirmatn/fonts/webfonts/Vazirmatn-Bold.woff2';
// IRANYekan is the face the rest of the product line uses, so it leads the
// stack; Vazirmatn stays behind it as the fallback. Bundled, not fetched: the
// same air-gapped requirement as above.
import iranYekanRegular from '@/assets/fonts/iranyekan/IRANYekanWeb-Regular.woff';
import iranYekanMedium from '@/assets/fonts/iranyekan/IRANYekanWeb-Medium.woff';
import iranYekanBold from '@/assets/fonts/iranyekan/IRANYekanWeb-Bold.woff';
import { useStylesStore } from '@/store/styles';

export const useStyles = createStyles(() => {
  return {};
});

const GlobalStyle = createGlobalStyle(({ theme: token }) => {
  const { theme } = useStyles();

  const setTheme = useStylesStore((s) => s.setTheme);

  useEffect(() => {
    setTheme(theme);
  }, [theme]);

  const scrollbarStyle = css`
    * {
      scrollbar-color: ${token.colorFill} transparent;
      ::-webkit-scrollbar {
        width: 6px;
        height: 6px;
      }

      ::-webkit-scrollbar-thumb {
        background-color: transparent;
        border-radius: 999px;
      }

      ::-webkit-scrollbar-corner {
        display: none;
        width: 0;
        height: 0;
      }

      &:hover {
        ::-webkit-scrollbar-thumb {
          background-color: ${token.colorFill};
        }
      }
    }
    .bashful-scroller {
      &::-webkit-scrollbar {
        width: 6px;
        height: 6px;
      }

      &::-webkit-scrollbar-thumb {
        border-radius: 10px;
        background-color: transparent;
        background-clip: padding-box;
      }

      &:hover::-webkit-scrollbar-thumb {
        background-color: ${token.colorFillSecondary};
      }

      &::-webkit-scrollbar-thumb:hover {
        background-color: ${token.colorFill};
      }

      &::-webkit-scrollbar-corner {
        display: none;
      }
    }
  `;

  const resizerStyle = css`
    /* The style of the dragged handle */
    .Resizer {
      position: relative;
      z-index: 100;
      flex-shrink: 0;
      background: ${token.colorBorderLayout};
      -moz-box-sizing: border-box;
      -webkit-box-sizing: border-box;
      box-sizing: border-box;
      -moz-background-clip: padding;
      -webkit-background-clip: padding;
      background-clip: padding-box;
    }

    .ResizerHidden .Resizer {
      display: none;
    }

    /* When the dragged line is on the right, the style when it is collapsed */
    .ResizerSizeIsZeroRight .Resizer.vertical {
      margin: 0 -5px 0px 0px;
      border-left: 0px solid transparent;
      border-right: 5px solid transparent;
    }

    /* When the dragged line is on the upper side, the style when it is collapsed  */
    .ResizerSizeIsZeroTop .Resizer.horizontal {
      margin: -5px 0px 0px 0px;
      border-top: 5px solid transparent;
      border-bottom: 0px solid transparent;
    }

    /* Horizontal drag bar */
    .Resizer.horizontal {
      height: 5px;
      margin: -2px 0;
      border-top: 2px solid transparent;
      border-bottom: 2px solid transparent;
      cursor: row-resize;
      width: 100%;
    }

    /* When the horizontal drag bar is dragged */
    .Resizer.horizontal:hover,
    .Resizer.horizontal:active {
      border-top: 2px solid ${token.colorBorder};
      border-bottom: 2px solid ${token.colorBorder};
      background: ${token.colorBorder};
      position: relative;
      z-index: 30;
    }

    /* Vertical drag bar */
    .Resizer.vertical {
      width: 5px;
      margin: 0 -2px;
      border-left: 2px solid transparent;
      border-right: 2px solid transparent;
      cursor: col-resize;
    }

    /* When dragging the vertical drag bar */
    .Resizer.vertical:hover,
    .Resizer.vertical:active {
      border-left: 2px solid ${token.colorBorder};
      border-right: 2px solid ${token.colorBorder};
      background: ${token.colorBorder};
      position: relative;
      z-index: 30;
    }

    .Resizer.disabled {
      cursor: default;
    }

    .Resizer.disabled:hover {
      border-color: transparent;
    }
  `;

  const canvasTable = css`
    .vtable__menu-element {
      padding: 4px !important;
      background-color: ${token.colorBgContainer} !important;
      border: 1px solid ${token.colorBorderSecondary} !important;
      box-shadow: none !important;
      color: ${token.colorTextBase} !important;
      border-radius: 6px;
      min-width: 150px !important;
    }
    .vtable__menu-element__item {
      height: 22px !important;
      padding: 5px 12px !important;
      color: ${token.colorText} !important;
      border-radius: 4px !important;
      &:hover {
        background-color: ${token.colorFillTertiary} !important;
      }
    }
    .vtable__menu-element__arrow {
      display: flex !important;
      svg {
        path {
          fill: ${token.colorTextBase} !important;
        }
      }
    }
  `;

  return css`
    @font-face {
      font-family: 'icon-editor';
      src: url(${iconEdit}) format('woff2');
    }
    /* swap: Persian text stays readable in a fallback face while the webfont
       loads, instead of flashing invisible. */
    @font-face {
      font-family: 'iranyekan';
      src: url(${iranYekanRegular}) format('woff');
      font-weight: 400;
      font-style: normal;
      font-display: swap;
    }
    @font-face {
      font-family: 'iranyekan';
      src: url(${iranYekanMedium}) format('woff');
      font-weight: 500;
      font-style: normal;
      font-display: swap;
    }
    @font-face {
      font-family: 'iranyekan';
      /* Also claims 600: the family has no semibold, and without this the
         browser would fall through to Vazirmatn for every semibold run. */
      src: url(${iranYekanBold}) format('woff');
      font-weight: 600 700;
      font-style: normal;
      font-display: swap;
    }

    /* The same face, minus the digits.
       This is the "fanum" cut of IRANYekan, which maps ASCII digits to Persian
       glyphs in its cmap - so 2026-07-27 renders as ۲۰۲۶-۰۷-۲۷ no matter what
       the interface language is, and no CSS feature setting can turn that off.
       Excluding U+0030-0039 from the range lets digits fall through to the next
       family in the stack, which draws them as written. Selected by language,
       so Persian keeps its numerals and English gets Latin ones. */
    @font-face {
      font-family: 'iranyekan-latn';
      src: url(${iranYekanRegular}) format('woff');
      font-weight: 400;
      font-style: normal;
      font-display: swap;
      unicode-range: U+0-2F, U+3A-10FFFF;
    }
    @font-face {
      font-family: 'iranyekan-latn';
      src: url(${iranYekanMedium}) format('woff');
      font-weight: 500;
      font-style: normal;
      font-display: swap;
      unicode-range: U+0-2F, U+3A-10FFFF;
    }
    @font-face {
      font-family: 'iranyekan-latn';
      src: url(${iranYekanBold}) format('woff');
      font-weight: 600 700;
      font-style: normal;
      font-display: swap;
      unicode-range: U+0-2F, U+3A-10FFFF;
    }
    @font-face {
      font-family: 'Vazirmatn';
      src: url(${vazirmatnRegular}) format('woff2');
      font-weight: 400;
      font-style: normal;
      font-display: swap;
    }
    @font-face {
      font-family: 'Vazirmatn';
      src: url(${vazirmatnMedium}) format('woff2');
      font-weight: 500;
      font-style: normal;
      font-display: swap;
    }
    @font-face {
      font-family: 'Vazirmatn';
      src: url(${vazirmatnSemiBold}) format('woff2');
      font-weight: 600;
      font-style: normal;
      font-display: swap;
    }
    @font-face {
      font-family: 'Vazirmatn';
      src: url(${vazirmatnBold}) format('woff2');
      font-weight: 700;
      font-style: normal;
      font-display: swap;
    }
    ${scrollbarStyle},
    ${resizerStyle},
    ${canvasTable},
    a {
      color: ${token.colorPrimary};
      &:hover {
        color: ${token.colorPrimaryHover};
      }
    }
    .ant-select-dropdown {
      border: 1px solid ${token.colorBorderSecondary};
    }
    .ant-modal-confirm-paragraph {
      width: 100%;
    }
    .ant-modal-root .ant-modal-wrap {
      top: ${window._appTitleBarHeight || 0}px;
    }
    .ant-dropdown {
      z-index: 11000 !important;
    }
    .ant-dropdown-menu-submenu {
      z-index: 11001 !important;
    }
    .ant-dropdown .ant-dropdown-menu .ant-dropdown-menu-item-icon {
      font-size: 20px !important;
    }
    .ant-dropdown-menu-submenu .ant-dropdown-menu {
      height: auto;
      max-height: 50vh;
      overflow: auto;
    }
    .ant-dropdown-menu-submenu .ant-dropdown-menu .ant-dropdown-menu-item-icon {
      font-size: 20px !important;
    }
    .ant-tooltip {
      z-index: 11100 !important;
      max-width: 500px;
    }
    .ant-input-outlined:focus-within {
      box-shadow: none;
    }
    .ant-input-outlined:focus {
      box-shadow: none;
    }

    .ant-form-item {
      margin-bottom: 16px;
    }

    .ant-upload-wrapper .ant-upload-drag {
      background-color: transparent;
    }

    .ant-dropdown-menu-submenu-title {
      display: flex;
      align-items: center;
    }

    /* The popup layer border color needs to be unified to UI */
    .ant-modal-content {
      border: 1px solid ${token.colorBorderSecondary} !important;
    }

    /* Offset Ant Design overlays below the desktop title bar. */
    .ant-drawer {
      top: ${window._appTitleBarHeight || 0}px;
    }
    .ant-message {
      top: ${window._appTitleBarHeight ? window._appTitleBarHeight + 4 : 0}px !important;
    }
  `;
});

export default memo(GlobalStyle);
