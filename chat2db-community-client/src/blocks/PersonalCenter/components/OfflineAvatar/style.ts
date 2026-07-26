import { createStyles } from 'antd-style';

export const useStyles = createStyles(({ css, token }) => {
  return {
    // Sized to match the nav items above it so the rail stays visually even.
    settingsButton: css`
      width: 36px;
      height: 36px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 8px;
      color: ${token.colorTextSecondary};
      cursor: pointer;
      transition: background-color 0.2s ease, color 0.2s ease;

      &:hover {
        color: ${token.colorText};
        background: ${token.colorFillTertiary};
      }
    `,
  };
});
