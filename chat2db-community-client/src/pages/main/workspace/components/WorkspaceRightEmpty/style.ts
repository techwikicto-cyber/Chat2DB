import { createStyles, keyframes } from 'antd-style';

export const useStyles = createStyles(({ css, token }) => {
  const bounceArrow = keyframes`
    0%   { transform: translateX(0); }
    8%   { transform: translateX(5px); }
    16%  { transform: translateX(0); }
    22%  { transform: translateX(3px); }
    28%  { transform: translateX(0); }
    32%  { transform: translateX(1.5px); }
    36%  { transform: translateX(0); }
    100% { transform: translateX(0); }
  `;
  return {
    box: css`
      display: flex;
      flex-direction: column;
      justify-content: center;
      align-items: center;
      width: 100%;
    `,
    /* The workspace canvas with nothing open in it. Quiet on purpose: this is a
       working surface, and whatever sits here is read once and then acted on. */
    stage: css`
      display: flex;
      flex-direction: column;
      align-items: center;
      max-width: 520px;
      padding: 0 24px;
      text-align: center;
    `,

    stageTitle: css`
      margin: 0;
      font-size: 20px;
      font-weight: 600;
      line-height: 1.4;
      letter-spacing: -0.01em;
      color: ${token.colorText};
    `,

    stageDesc: css`
      margin: 8px 0 0;
      font-size: 14px;
      line-height: 1.7;
      color: ${token.colorTextSecondary};
    `,

    stageAction: css`
      margin-top: 24px;
    `,

    databaseRow: css`
      margin-top: 28px;
      display: flex;
      flex-wrap: wrap;
      justify-content: center;
      gap: 8px;
    `,

    databaseChip: css`
      display: inline-flex;
      align-items: center;
      gap: 8px;
      height: 36px;
      padding: 0 14px;
      border: 1px solid ${token.colorBorderSecondary};
      border-radius: 999px;
      background: ${token.colorBgContainer};
      font-size: 13px;
      font-weight: 500;
      color: ${token.colorText};
      cursor: pointer;
      transition: border-color 0.15s ease, background-color 0.15s ease, transform 0.15s ease;

      &:hover {
        border-color: ${token.colorPrimaryBorderHover};
        background: ${token.colorPrimaryBg};
      }

      &:active {
        transform: translateY(1px);
      }

      &:focus-visible {
        outline: 2px solid ${token.colorPrimaryBorder};
        outline-offset: 2px;
      }
    `,

    stageFootnote: css`
      margin: 20px 0 0;
      display: flex;
      align-items: center;
      /* The parent centres text, which does nothing for flex children. */
      justify-content: center;
      flex-wrap: wrap;
      gap: 6px;
      font-size: 12px;
      /* Secondary rather than tertiary: this is 12px, and tertiary only just
         clears 4.5:1 on the container background. */
      color: ${token.colorTextSecondary};
    `,

    // AI introduction page styles.
    aiIntro: css`
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 20px;
      text-align: center;
    `,

    aiIconWrap: css`
      width: 72px;
      height: 72px;
      border-radius: 18px;
      background: ${token.colorPrimaryBg};
      display: flex;
      align-items: center;
      justify-content: center;
    `,

    aiTitle: css`
      font-size: 32px;
      font-weight: 700;
      color: ${token.colorText};
    `,

    aiDesc: css`
      font-size: 16px;
      line-height: 1.7;
      color: ${token.colorTextSecondary};
      white-space: pre-line;
    `,

    featureRow: css`
      display: flex;
      gap: 12px;
    `,

    featureCard: css`
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 16px;
      border-radius: 8px;
      background: ${token.colorFillQuaternary};
      font-size: 14px;
      color: ${token.colorTextSecondary};
      cursor: default;
      user-select: none;
    `,

    featureIcon: css`
      color: ${token.colorTextTertiary};
      flex-shrink: 0;
    `,

    aiCta: css`
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 0 28px;
      height: 36px;
      font-size: 14px;
    `,

    aiCtaArrow: css`
      display: inline-flex;
      animation: ${bounceArrow} 3s ease-out infinite;
    `,

    dismissBtn: css`
      background: none;
      border: none;
      cursor: pointer;
      font-size: 12px;
      color: ${token.colorTextQuaternary};
      padding: 4px 8px;
      border-radius: 4px;
      transition: color 0.15s;

      &:hover {
        color: ${token.colorTextSecondary};
      }
    `,
  };
});
