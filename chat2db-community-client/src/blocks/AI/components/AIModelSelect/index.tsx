import React, { useEffect, useState } from 'react';
import { Select } from 'antd';
import { PlusOutlined, RightOutlined } from '@ant-design/icons';
import { useStyles } from './style';
import i18n from '@/i18n';
import { useAIStore } from '@/store/ai/store';
import { SelectedModelOption } from '@/store/ai/slices/model/initialState';
import { ModelSelectOption } from './modelSelectOptions';

interface AIModelSelectProps {
  onChange?: (value: SelectedModelOption | null) => void;
  options?: ModelSelectOption[];
  showCustomModelEntry?: boolean;
  onCustomModelClick?: () => void;
  customModelText?: string;
}

const AIModelSelect = ({
  onChange,
  options,
  showCustomModelEntry = false,
  onCustomModelClick,
  customModelText,
}: AIModelSelectProps) => {
  const { styles, cx } = useStyles();
  const { modelList, selectedModel, setSelectedModel, getModelList } = useAIStore((state) => ({
    modelList: state.modelList,
    selectedModel: state.selectedModel,
    setSelectedModel: state.setSelectedModel,
    getModelList: state.getModelList,
  }));
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (options !== undefined) {
      return;
    }
    if (!modelList || modelList.length === 0) {
      getModelList();
    }
  }, [options, modelList?.length]);

  // Handle select change
  const handleChange = (selectedValue: { value: string; label: React.ReactNode }) => {
    const nextValue = {
      value: selectedValue.value,
      label: String(selectedValue.label || ''),
    };
    setSelectedModel(nextValue);
    if (onChange) {
      onChange(nextValue);
    }
  };

  // handles the drop-down box opening event
  const handleDropdownVisibleChange = (nextOpen: boolean) => {
    setOpen(nextOpen);
    if (nextOpen && (!modelList || modelList.length === 0)) {
      if (options !== undefined) {
        return;
      }
      getModelList();
    }
  };

  const openCustomModelDialog = () => {
    // The dialog takes over from here; leaving the popup open would park it
    // behind the modal mask, where the next click outside is the only way out.
    setOpen(false);
    onCustomModelClick?.();
  };

  const selectOptions = options !== undefined ? options : modelList;
  const hasOptions = !!selectOptions?.length;

  // The entry opens a dialog - it is an action, not a model. It is rendered
  // under the list rather than as one more option because antd otherwise adopts
  // whatever is clicked as the picker's value: the first click drew this
  // two-line block inside the 24px control, and every later click was swallowed,
  // since rc-select skips onChange when the value has not actually changed.
  const customModelEntry =
    showCustomModelEntry && onCustomModelClick ? (
      <div
        role="button"
        tabIndex={0}
        className={cx(styles.customModelEntry, hasOptions && styles.customModelEntryDivided)}
        // The popup closes on blur, and mousedown inside it would move focus off
        // the picker before the click lands.
        onMouseDown={(event) => event.preventDefault()}
        onClick={openCustomModelDialog}
        onKeyDown={(event) => {
          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            openCustomModelDialog();
          }
        }}
      >
        <span className={styles.customModelIcon}>
          <PlusOutlined />
        </span>
        <span className={styles.customModelContent}>
          <span className={styles.customModelTitle}>{customModelText || i18n('setting.modelConfig.entry')}</span>
          <span className={styles.customModelHint}>{i18n('setting.modelConfig.entryHint')}</span>
        </span>
        <RightOutlined className={styles.customModelArrow} />
      </div>
    ) : null;

  return (
    <Select
      popupMatchSelectWidth={false}
      // The picker sits inside the chat input's toolbar, which clips its
      // overflow. Rendering the popup into the body takes it out of that
      // stacking/clipping context so it can never be cut off or drawn over the
      // controls beside it.
      getPopupContainer={() => document.body}
      className={styles.modelSelect}
      popupClassName={styles.popupSelect}
      variant="borderless"
      labelInValue
      value={selectedModel && selectedModel.label ? selectedModel : undefined}
      onChange={handleChange}
      options={selectOptions}
      size="small"
      placeholder={i18n('ai.select.model')}
      open={open}
      onDropdownVisibleChange={handleDropdownVisibleChange}
      dropdownRender={
        customModelEntry
          ? (menu) => (
              <>
                {menu}
                {customModelEntry}
              </>
            )
          : undefined
      }
      // With no model configured yet the entry is the only thing to do here, so
      // antd's "no data" placeholder above it is noise.
      notFoundContent={customModelEntry && !hasOptions ? null : undefined}
    />
  );
};

export default AIModelSelect;
