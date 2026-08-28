import { Input } from 'antd';
import { i18n } from '@/i18n';
import styles from './index.less';

/**
 * What this database is, in the words of whoever owns it.
 *
 * The schema already tells the assistant that a column is called Rank1 and
 * holds a float. What it cannot tell it is whether 1 is the best rank or the
 * worst, what Cluster has to do with it, or that rows without a processing
 * date are drafts nobody counts. Somebody at the company knows all of that,
 * and this is the first place they have been able to write it down.
 *
 * A plain textarea on purpose. A form with fields for "grain" and "metrics"
 * would be a better data model and a worse thing to sit in front of: people
 * describe their own database in their own order, and the parts that matter
 * most are the ones no schema designer anticipated.
 */

/** Matches MAX_PROFILE_CHARS on the server, which cuts anything past it. */
export const MAX_AI_PROFILE_CHARS = 20000;

interface IProps {
  value?: string | null;
  onChange: (value: string) => void;
  disabled?: boolean;
}

const AiProfile = (props: IProps) => {
  const { value, onChange, disabled } = props;
  const text = value || '';
  const overBudget = text.length > MAX_AI_PROFILE_CHARS;

  return (
    <div className={styles.box}>
      <p className={styles.intro}>{i18n('connection.aiProfile.intro')}</p>
      <Input.TextArea
        value={text}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value)}
        placeholder={i18n('connection.aiProfile.placeholder')}
        autoSize={{ minRows: 8, maxRows: 20 }}
        className={styles.editor}
      />
      <div className={styles.footer}>
        {/* Only once it is near the limit: a counter on an empty box is a
            length requirement nobody set. */}
        {text.length > MAX_AI_PROFILE_CHARS * 0.8 && (
          <span className={overBudget ? styles.countOver : styles.count}>
            {overBudget
              ? i18n('connection.aiProfile.overBudget', String(MAX_AI_PROFILE_CHARS))
              : i18n('connection.aiProfile.count', String(text.length), String(MAX_AI_PROFILE_CHARS))}
          </span>
        )}
      </div>
    </div>
  );
};

export default AiProfile;
