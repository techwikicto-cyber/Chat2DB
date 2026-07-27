import { createStyles } from 'antd-style';

export const useStyles = createStyles(({ css, token }) => {
  return {
    container: css`
      display: flex;
      flex-direction: column;
      gap: 28px;
    `,
    sectionTitle: css`
      font-size: 15px;
      font-weight: 600;
      color: ${token.colorText};
      margin-bottom: 6px;
    `,
    sectionHint: css`
      font-size: 12px;
      line-height: 1.8;
      color: ${token.colorTextTertiary};
      margin-bottom: 12px;
    `,
    identity: css`
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 16px;
    `,
    username: css`
      font-size: 14px;
      font-weight: 600;
      color: ${token.colorText};
    `,
    passwordForm: css`
      display: flex;
      align-items: center;
      gap: 8px;
      max-width: 640px;
    `,
    createRow: css`
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 16px;
      max-width: 760px;
    `,
    disabledNotice: css`
      font-size: 13px;
      line-height: 1.9;
      color: ${token.colorTextTertiary};
    `,
  };
});
