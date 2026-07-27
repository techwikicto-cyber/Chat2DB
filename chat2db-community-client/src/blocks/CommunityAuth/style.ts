import { createStyles } from 'antd-style';

export const useStyles = createStyles(({ css, token }) => {
  return {
    screen: css`
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px;
      background: ${token.colorBgLayout};
    `,
    card: css`
      width: 100%;
      max-width: 380px;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 16px;
      padding: 32px 28px;
      border: 1px solid ${token.colorBorderSecondary};
      border-radius: ${token.borderRadiusLG}px;
      background: ${token.colorBgContainer};
    `,
    /* Pulled out of the column flow so the card still centres on the mark. */
    language: css`
      align-self: flex-end;
      margin-bottom: -8px;
      color: ${token.colorTextTertiary};
    `,
    title: css`
      font-size: 20px;
      font-weight: 600;
      color: ${token.colorText};
      text-align: center;
    `,
    subtitle: css`
      font-size: 13px;
      line-height: 1.7;
      color: ${token.colorTextTertiary};
      text-align: center;
      margin-top: -8px;
    `,
    form: css`
      width: 100%;
      display: flex;
      flex-direction: column;
      gap: 12px;
    `,
    error: css`
      color: ${token.colorError};
      font-size: 12px;
      text-align: center;
      min-height: 18px;
    `,
    loading: css`
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
    `,
  };
});
