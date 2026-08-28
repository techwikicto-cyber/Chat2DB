import { useRef, useState } from 'react';
import { Button, Input } from 'antd';
import { UploadOutlined } from '@ant-design/icons';
import { staticMessage } from '@chat2db/ui';
import aiAttachmentService from '@/service/aiAttachment';
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
 *
 * A document can be loaded into it as well, because this is usually already
 * written somewhere - a handover note, a data dictionary, a page in the wiki.
 * It is read into the box as text and stays editable there: what is stored is
 * the words, not the file. Nobody has to wonder later which version of which
 * document the assistant is actually reading.
 */

/** Matches MAX_PROFILE_CHARS on the server, which cuts anything past it. */
export const MAX_AI_PROFILE_CHARS = 20000;

/** What the document parser already understands. */
const ACCEPTED_FILES = '.txt,.md,.json,.pdf,.doc,.docx';

interface IProps {
  value?: string | null;
  onChange: (value: string) => void;
  disabled?: boolean;
}

const AiProfile = (props: IProps) => {
  const { value, onChange, disabled } = props;
  const [loading, setLoading] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);
  const text = value || '';
  const overBudget = text.length > MAX_AI_PROFILE_CHARS;

  const loadDocument = async (file: File) => {
    setLoading(true);
    try {
      const parsed = await aiAttachmentService.parseAttachment({ file, fileName: file.name });
      const loaded = parsed?.content || '';
      if (!loaded.trim()) {
        staticMessage.error(i18n('connection.aiProfile.uploadEmpty', file.name));
        return;
      }
      // Appended, never substituted. Somebody who has already written three
      // paragraphs and then loads the old handover note means to have both,
      // and silently discarding their work would be the worse guess.
      onChange(text.trim() ? `${text.trim()}\n\n${loaded.trim()}` : loaded.trim());
      staticMessage.success(i18n('connection.aiProfile.uploadLoaded', file.name));
    } catch (error: any) {
      staticMessage.error(error?.message || i18n('connection.aiProfile.uploadFailed'));
    } finally {
      setLoading(false);
    }
  };

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
        <Button
          size="small"
          icon={<UploadOutlined />}
          loading={loading}
          disabled={disabled}
          onClick={() => fileInput.current?.click()}
        >
          {i18n('connection.aiProfile.upload')}
        </Button>
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

      <input
        ref={fileInput}
        type="file"
        accept={ACCEPTED_FILES}
        className={styles.hiddenInput}
        onChange={(event) => {
          const file = event.target.files?.[0];
          // Cleared before the upload, so choosing the same file twice in a
          // row still fires a change event.
          event.target.value = '';
          if (file) {
            loadDocument(file);
          }
        }}
      />
    </div>
  );
};

export default AiProfile;
