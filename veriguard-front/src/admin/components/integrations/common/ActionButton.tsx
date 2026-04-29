import { Button } from '@mui/material';

import { useFormatter } from '../../../../components/i18n';

interface Props {
  onUpdate: () => void;
  disabled: boolean;
  status?: 'starting' | 'stopping';
}

const ActionButton = ({ onUpdate, disabled, status }: Props) => {
  const { t } = useFormatter();

  if (status === 'starting') {
    return (
      <Button
        variant="outlined"
        color="error"
        onClick={onUpdate}
        disabled={disabled}
      >
        {t('Stop')}
      </Button>
    );
  }

  return (
    <Button
      variant="contained"
      sx={{
        color: 'primary',
        borderColor: 'primary',
      }}
      onClick={onUpdate}
      disabled={disabled}
    >
      { t('Start')}
    </Button>
  );
};

export default ActionButton;
