import { createStyles } from 'antd-style';

export const useStyles = createStyles(({ css }) => {
  return {
    message: css`
      max-width: 100%;

      /* Same reasoning as the AI panel: 'plaintext' is the CSS spelling of
         dir="auto", so each block takes its direction from the first letter
         that has one and a Persian sentence carrying an English table name
         stops coming out shuffled. Code keeps its own direction. */
      p,
      li,
      h1,
      h2,
      h3,
      h4,
      h5,
      h6,
      blockquote,
      th,
      td {
        unicode-bidi: plaintext;
        text-align: start;
      }

      pre,
      pre code,
      code {
        direction: ltr;
        unicode-bidi: embed;
        text-align: left;
      }
    `,
  };
});
