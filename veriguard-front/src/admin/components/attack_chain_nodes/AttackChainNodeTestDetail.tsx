import { Card, CardContent, CardHeader, Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import Drawer from '../../../components/common/Drawer';
import { useFormatter } from '../../../components/i18n';
import { type AttackChainNodeTestStatusOutput } from '../../../utils/api-types';
import { truncate } from '../../../utils/String';
import AttackChainNodeIcon from '../common/attack_chain_nodes/AttackChainNodeIcon';
import GlobalExecutionTraces from '../common/attack_chain_nodes/status/traces/GlobalExecutionTraces';

interface Props {
  open: boolean;
  handleClose: () => void;
  injectTestStatus: AttackChainNodeTestStatusOutput | undefined;
}

const AttackChainNodeTestDetail = ({
  open,
  handleClose,
  injectTestStatus,
}: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();

  return (
    <Drawer
      open={open}
      handleClose={handleClose}
      title={t('Test Details')}
    >
      <div>
        <Card elevation={0} style={{ marginBottom: theme.spacing(3) }}>
          {injectTestStatus
            ? (
                <CardHeader
                  sx={{ backgroundColor: theme.palette.background.default }}
                  avatar={(
                    <AttackChainNodeIcon
                      isPayload={false}
                      type={injectTestStatus.node_type}
                      variant="list"
                    />
                  )}

                />
              ) : (
                <Paper variant="outlined" style={{ padding: theme.spacing(3) }}>
                  <Typography variant="body1">{t('No data available')}</Typography>
                </Paper>
              )}
          <CardContent style={{
            fontSize: 18,
            textAlign: 'center',
          }}
          >
            {truncate(injectTestStatus?.node_title, 80)}
          </CardContent>
        </Card>
        {injectTestStatus && <GlobalExecutionTraces injectStatus={injectTestStatus} />}
      </div>
    </Drawer>
  );
};

export default AttackChainNodeTestDetail;
