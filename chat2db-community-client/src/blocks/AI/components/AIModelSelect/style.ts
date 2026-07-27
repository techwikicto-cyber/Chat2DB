import { createStyles } from 'antd-style';

export const useStyles = createStyles(({ css, token }) => {
  return {
    modelSelect: css`
      min-width: 108px;
      max-width: 190px;
      font-size: 11px;
      border-radius: 999px;
      background: transparent;
      transition: background-color 0.2s ease, color 0.2s ease;

      &:hover {
        background: ${token.colorFillTertiary};
      }

      & .ant-select-selector {
        height: 24px !important;
        padding: 0 10px !important;
        font-size: 12px !important;
      }

      & .ant-select-selection-item {
        max-width: 150px;
        color: ${token.colorText};
        font-weight: 500;
        /* The closed control is a single 24px line, so a long model name is
           clipped rather than allowed to grow the toolbar. */
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
      }

      & .ant-select-selection-placeholder {
        color: ${token.colorTextSecondary};
        font-weight: 500;
      }

      &.ant-select-focused {
        background: ${token.colorFillTertiary};
      }
    `,
    popupSelect: css`
      min-width: 260px !important;
      max-width: 320px !important;
      padding: 6px !important;

      & .ant-select-item {
        font-size: 12px !important;
        min-height: 0px !important;
        padding: 4px 8px !important;
      }
    `,
    emptyHint: css`
      padding: 6px 8px;
      color: ${token.colorTextTertiary};
      font-size: 12px;
      text-align: right;
    `,
    customModelEntry: css`
      width: 100%;
      padding: 6px 8px;
      display: flex;
      align-items: center;
      gap: 8px;
      border-radius: ${token.borderRadiusSM}px;
      color: ${token.colorText};
      text-align: left;
      cursor: pointer;
      outline: none;
      transition: background-color 0.2s ease;

      &:hover,
      &:focus-visible {
        background: ${token.colorFillTertiary};
      }
    `,
    customModelEntryDivided: css`
      margin-top: 4px;
      border-top: 1px solid ${token.colorSplit};
      border-top-left-radius: 0;
      border-top-right-radius: 0;
    `,
    customModelIcon: css`
      width: 24px;
      height: 24px;
      flex: 0 0 24px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border-radius: 6px;
      color: ${token.colorPrimary};
      background: ${token.colorPrimaryBg};
    `,
    customModelContent: css`
      min-width: 0;
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 2px;
    `,
    customModelTitle: css`
      color: ${token.colorText};
      font-size: 12px;
      font-weight: 600;
      line-height: 18px;
    `,
    customModelHint: css`
      color: ${token.colorTextSecondary};
      font-size: 11px;
      line-height: 16px;
      white-space: normal;
      /* Hard ceiling of two lines. Without it the hint keeps wrapping and drags
         the row taller than whatever container it lands in. */
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    `,
    customModelArrow: css`
      flex: 0 0 auto;
      color: ${token.colorPrimary};
      font-size: 11px;
    `,
  };
});
