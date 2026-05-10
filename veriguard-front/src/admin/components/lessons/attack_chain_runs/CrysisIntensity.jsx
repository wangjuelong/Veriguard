import { Paper } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import Chart from 'react-apexcharts';

import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import { areaChartOptions } from '../../../../utils/Charts';

const CrysisIntensity = ({ nodes }) => {
  const theme = useTheme();
  const { t, nsdt } = useFormatter();
  const injectsData = R.pipe(
    R.filter(n => n.node_sent_at !== null),
    R.map((n) => {
      const date = new Date(n.node_sent_at);
      date.setHours(0, 0, 0, 0);
      return R.assoc('node_sent_at_date', date.toISOString(), n);
    }),
    R.groupBy(R.prop('node_sent_at_date')),
    R.toPairs,
    R.map(n => ({
      x: n[0],
      y: n[1].length,
    })),
  )(nodes);
  const chartData = [
    {
      name: t('Number of nodes'),
      data: injectsData,
    },
  ];
  return (
    <Paper variant="outlined">
      {injectsData.length > 0 ? (
        <Chart
          options={areaChartOptions(theme, true, nsdt, null, undefined)}
          series={chartData}
          type="area"
          width="100%"
          height={350}
        />
      ) : (
        <Empty
          message={t(
            'No data to display or the attack_chain_run has not started yet',
          )}
        />
      )}
    </Paper>
  );
};

export default CrysisIntensity;
