import { Button, Tooltip } from '@mui/material';
import { type CSSProperties, type SyntheticEvent } from 'react';

import { useFormatter } from '../../../../components/i18n';

interface Props {
  onMigrateBtnClick: (e: SyntheticEvent) => void;
  style?: CSSProperties;
}

const MigrateButton = ({ onMigrateBtnClick, style = {} }: Props) => {
  const { t } = useFormatter();

  return (
    <Tooltip title={t('Migrate a manually-deployed connector to the XTM composer to manage its settings from the interface')}>
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
          onClick={onMigrateBtnClick}
        >
          {t('Migrate')}
        </Button>
      </div>
    </Tooltip>

  );
};

export default MigrateButton;
