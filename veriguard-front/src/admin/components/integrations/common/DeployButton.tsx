import { Badge, Button, Tooltip } from '@mui/material';
import { type CSSProperties, type SyntheticEvent } from 'react';

import { useFormatter } from '../../../../components/i18n';

interface Props {
  onDeployBtnClick: (e: SyntheticEvent) => void;
  style?: CSSProperties;
  deploymentCount: number;
}

const DeployButton = ({ onDeployBtnClick, style = {}, deploymentCount }: Props) => {
  const { t } = useFormatter();

  return (
    <Tooltip title={t('Can not deploy more than one instance')}>
      <div style={{
        ...style,
        position: 'relative',
      }}
      >
        <Button
          variant="contained"
          sx={{
            color: 'primary',
            borderColor: 'primary',
          }}
          size="small"
          onClick={onDeployBtnClick}
          disabled={deploymentCount > 0}
        >
          {t('Deploy')}
        </Button>
        <Badge
          badgeContent={deploymentCount}
          color="warning"
          sx={{
            position: 'absolute',
            top: '10px',
            right: 0,
          }}
        />
      </div>
    </Tooltip>

  );
};

export default DeployButton;
