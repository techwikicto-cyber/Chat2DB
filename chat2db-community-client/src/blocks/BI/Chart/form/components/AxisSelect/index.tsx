/*
 * AxisSelect
 * Picks which column of the query result feeds one axis of the chart.
 */
import { memo, useMemo } from 'react';
import { Select } from 'antd';
import i18n from '@/i18n';
import { IChartItem } from '@/typings';
import { newFormattedSqlExecuteData } from '@/utils/dashboard';

interface IProps {
  value: any;
  onChange?: (value: any) => void;
  chartDetail: IChartItem;
}

const AxisSelect = (props: IProps) => {
  const { value, onChange, chartDetail } = props;

  const dataKeys = useMemo(() => {
    if (!chartDetail.metaData) {
      return [];
    }
    const metaData = newFormattedSqlExecuteData(chartDetail.metaData);
    const keys = Object.keys(metaData[0] || {});
    const data =
      keys?.map((key) => {
        return { value: key, label: key };
      }) || [];
    return data;
  }, [chartDetail.metaData]);

  return (
    <Select
      value={value ?? undefined}
      // The list only ever offered the query's own columns, so an axis could be
      // pointed at something but never at nothing - and a query returning a
      // single column left both axes stuck on it. The schema has always allowed
      // an empty axis and the renderers already draw no series when a field is
      // missing; all that was wanting was a way to say so.
      allowClear
      placeholder={i18n('dashboard.chart.noField')}
      // Clearing hands back undefined, which vanishes when the schema is
      // serialised and would then read as "never set". null survives the round
      // trip and is what the typings and the AI-written schemas already use.
      onChange={(next) => onChange?.(next ?? null)}
      options={dataKeys}
    />
  );
};

export default memo(AxisSelect);
