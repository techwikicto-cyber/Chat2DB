/**
 * An axis may be left empty.
 *
 * The configuration form now offers a way to clear an axis, which it did not
 * before - the list held only the query's own columns, so a query returning a
 * single column left both axes pointed at it with no way out. Clearing one
 * writes null into the chart schema, and these are the functions that read it.
 * They are expected to draw nothing rather than throw, for every chart type,
 * whichever axis is empty.
 */
import assert from 'node:assert';
// Every one of these modules exports the name it was copied from, so each
// import has to be renamed to say which chart it actually belongs to.
import { barDataTreating } from './BarChart/dataTreating';
import { barDataTreating as lineDataTreating } from './LineChart/dataTreating';
import { barDataTreating as comboDataTreating } from './ComboChart/dataTreating';
import { pieDataTreating as funnelDataTreating } from './FunnelChart/dataTreating';
import { pieDataTreating } from './PieChart/dataTreating';
import { pieDataTreating as valueDataTreating } from './ValueChart/dataTreating';
import { pieDataTreating as wordCloudDataTreating } from './WordCloudChart/dataTreating';
import { ChartType, LineType, OrderByRule, OrderByType } from '../constants';
import { ChartSchema } from '../typings';

const data = [
  { region: 'north', total: 12 },
  { region: 'south', total: 30 },
];

const schema = (overrides: Partial<ChartSchema>): ChartSchema =>
  ({
    chartType: ChartType.Line,
    lineType: LineType.Line,
    orderByType: OrderByType.DEFAULT,
    orderByRule: OrderByRule.ASC,
    ...overrides,
  }) as ChartSchema;

const cases: Array<[string, () => unknown]> = [
  ['bar, no x', () => barDataTreating({ data, chartSchema: schema({ xField: null, yField: 'total' }) })],
  ['bar, no y', () => barDataTreating({ data, chartSchema: schema({ xField: 'region', yField: null }) })],
  ['bar, neither', () => barDataTreating({ data, chartSchema: schema({ xField: null, yField: null }) })],
  ['line, no x', () => lineDataTreating({ data, chartSchema: schema({ xField: null, yField: 'total' }) })],
  ['line, no y', () => lineDataTreating({ data, chartSchema: schema({ xField: 'region', yField: null }) })],
  ['combo, no x', () => comboDataTreating({ data, chartSchema: schema({ xField: null }) })],
  ['pie, neither', () => pieDataTreating({ data, chartSchema: schema({ xField: null, yField: null }) })],
  ['funnel, neither', () => funnelDataTreating({ data, chartSchema: schema({ xField: null, yField: null }) })],
  ['wordCloud, neither', () => wordCloudDataTreating({ data, chartSchema: schema({ xField: null, yField: null }) })],
  ['value, none', () => valueDataTreating({ data, chartSchema: schema({ xField: null, valueField: null }) })],
];

for (const [name, run] of cases) {
  assert.doesNotThrow(run, `${name} threw on an empty axis`);
}

// An empty axis draws nothing; a complete one still draws. Without this the
// test above would pass just as happily against a function that returned early
// for every input.
const empty = barDataTreating({ data, chartSchema: schema({ xField: null, yField: 'total' }) });
assert.deepStrictEqual(empty.seriesData, [], 'an empty x axis should produce no series data');

const full = barDataTreating({ data, chartSchema: schema({ xField: 'region', yField: 'total' }) });
assert.deepStrictEqual(full.seriesData, [12, 30], 'both axes set should still produce series data');
assert.deepStrictEqual(full.xAxis.data, ['north', 'south'], 'both axes set should still produce categories');

console.log('Chart empty-axis tests passed.');
