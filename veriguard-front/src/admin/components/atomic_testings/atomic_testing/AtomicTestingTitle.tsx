import { InfoOutlined } from '@mui/icons-material';
import { Box, IconButton, Tooltip, Typography } from '@mui/material';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import ItemStatus from '../../../../components/ItemStatus';
import Loader from '../../../../components/Loader';
import type { AttackChainNodeResultOverviewOutput } from '../../../../utils/api-types';
import { truncate } from '../../../../utils/String';
import AtomicTestingInformation from './AtomicTestingInformation';

const useStyles = makeStyles()(theme => ({
  title: {
    float: 'left',
    marginRight: theme.spacing(1),
  },
}));

interface Props { injectResultOverview: AttackChainNodeResultOverviewOutput }

const AtomicTestingTitle = ({ injectResultOverview }: Props) => {
  const { classes } = useStyles();
  const { t } = useFormatter();

  if (!injectResultOverview) {
    return <Loader variant="inElement" />;
  }

  return (
    <Box>
      <Tooltip title={injectResultOverview.node_title}>
        <Typography
          variant="h1"
          gutterBottom
          classes={{ root: classes.title }}
        >
          {truncate(injectResultOverview.node_title, 80)}
        </Typography>
      </Tooltip>
      <ItemStatus
        status={injectResultOverview.node_status?.status_name}
        label={t(injectResultOverview.node_status?.status_name ?? 'Unknown')}
      />
      <Tooltip
        slotProps={{
          tooltip: {
            sx: {
              maxWidth: 500,
              backgroundColor: 'background.paper',
              color: 'text.primary',
              padding: 0,
            },
          },
        }}
        title={
          <AtomicTestingInformation injectResultOverviewOutput={injectResultOverview} />
        }
      >
        <IconButton size="small">
          <InfoOutlined
            fontSize="small"
            color="primary"
          />
        </IconButton>
      </Tooltip>
    </Box>
  );
};

export default AtomicTestingTitle;
