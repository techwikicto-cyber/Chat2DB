import { Radio } from 'antd';
import { i18n } from '@/i18n';
import styles from './index.less';

/**
 * How much of this connection's data may reach the model provider.
 *
 * Its own panel rather than a row in the connection form, because it is not a
 * connection parameter: nothing here changes how the database is reached. It
 * is a decision about the data behind it, and it belongs to whoever knows what
 * that data is.
 *
 * Three levels, described by what each one costs the person choosing. A
 * setting whose options are named NONE, SAMPLE and FULL asks the reader to
 * work out the consequence themselves, and most will take the default.
 */

export type AiDisclosurePolicy = 'NONE' | 'SAMPLE' | 'FULL';

export const DEFAULT_AI_DISCLOSURE_POLICY: AiDisclosurePolicy = 'SAMPLE';

interface IProps {
  value?: string | null;
  onChange: (value: AiDisclosurePolicy) => void;
  disabled?: boolean;
}

const AiDisclosure = (props: IProps) => {
  const { value, onChange, disabled } = props;
  // A connection saved before this setting existed has nothing stored, and the
  // default is what it has been doing all along.
  const selected = (value as AiDisclosurePolicy) || DEFAULT_AI_DISCLOSURE_POLICY;

  const options: Array<{ value: AiDisclosurePolicy; label: string; description: string }> = [
    {
      value: 'NONE',
      label: i18n('connection.aiDisclosure.none'),
      description: i18n('connection.aiDisclosure.noneDescription'),
    },
    {
      value: 'SAMPLE',
      label: i18n('connection.aiDisclosure.sample'),
      description: i18n('connection.aiDisclosure.sampleDescription'),
    },
    {
      value: 'FULL',
      label: i18n('connection.aiDisclosure.full'),
      description: i18n('connection.aiDisclosure.fullDescription'),
    },
  ];

  return (
    <div className={styles.box}>
      <p className={styles.intro}>{i18n('connection.aiDisclosure.intro')}</p>
      <Radio.Group
        value={selected}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value as AiDisclosurePolicy)}
        className={styles.options}
      >
        {options.map((option) => (
          <Radio key={option.value} value={option.value} className={styles.option}>
            <span className={styles.optionLabel}>{option.label}</span>
            <span className={styles.optionDescription}>{option.description}</span>
          </Radio>
        ))}
      </Radio.Group>
    </div>
  );
};

export default AiDisclosure;
