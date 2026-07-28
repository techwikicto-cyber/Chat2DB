import { createStyles } from 'antd-style';

export const useStyles = createStyles(({ css }) => {
  return {
    canvas: css`
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      /* Decoration only: every click has to reach whatever is above it. */
      pointer-events: none;
    `,
  };
});
