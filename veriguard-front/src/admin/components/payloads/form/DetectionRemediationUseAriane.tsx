import { AutoFixHigh as AutoFixHighIcon } from '@mui/icons-material';
import { Button, Tooltip, Typography } from '@mui/material';
import { useState } from 'react';

import { useFormatter } from '../../../../components/i18n';
import useAI from '../../../../utils/hooks/useAI';
import { isNotEmptyField } from '../../../../utils/utils';
import useIsEligibleArianeCollector from '../hook/useIsEligibleArianeCollector';
import useIsEligibleArianePayloadType from '../hook/useIsEligibleArianePayloadType';
import Loader from '../Loader';
import { useSnapshotRemediation } from '../utils/useSnapshotRemediation';

export interface Props {
  collectorType: string;
  payloadType?: string | undefined;
  detectionRemediationContent?: string;
  onSubmit: () => Promise<void>;
  isValidForm?: boolean;
}

const DetectionRemediationUseAriane = ({
  collectorType,
  payloadType,
  detectionRemediationContent,
  onSubmit,
  isValidForm = true,
}: Props) => {
  const { snapshot } = useSnapshotRemediation();
  const { t } = useFormatter();
  const { enabled, configured } = useAI();
  const isAvailable = enabled && configured;

  const [loading, setLoading] = useState(false);
  const isEligibleArianeCollector = useIsEligibleArianeCollector(collectorType);
  const isEligibleArianePayload = useIsEligibleArianePayloadType(payloadType);
  const hasContent = isNotEmptyField(detectionRemediationContent);

  const handleClick = async () => {
    setLoading(true);
    onSubmit().finally(() => setLoading(false));
  };

  let btnLabel = t('Use Ariane');
  if (!isEligibleArianeCollector) {
    btnLabel = btnLabel + t(' is not available for current collector');
  } else if (!isEligibleArianePayload) {
    btnLabel = btnLabel + t(' is not available for current payload type');
  } else if (!isValidForm) {
    btnLabel = btnLabel + t(' is locked until required fields are filled.');
  } else if (hasContent) {
    btnLabel = btnLabel + t(' is only available for empty content');
  }

  const disabled = !isEligibleArianeCollector || !isAvailable || hasContent || !isValidForm || !isEligibleArianePayload;

  return (
    <Tooltip title={btnLabel}>
      <span>
        {(loading || snapshot?.get(collectorType)?.isLoading) ? (
          <div style={{
            display: 'flex',
            alignItems: 'center',
            marginRight: '10px',
          }}
          >
            <Typography
              variant="body2"
              color="textSecondary"
              sx={{ padding: 2 }}
              gutterBottom
            >
              {t('AI in progress')}
            </Typography>
            <Loader />
          </div>
        ) : (
          <Button
            type="button"
            variant="outlined"
            sx={{
              marginLeft: 'auto',
              color: 'ai.main',
              borderColor: 'ai.main',
            }}
            size="small"
            onClick={handleClick}
            startIcon={<AutoFixHighIcon fontSize="small" />}
            disabled={disabled || loading}
          >
            {t('Use Ariane ')}
          </Button>
        )}
      </span>
    </Tooltip>
  );
};
export default DetectionRemediationUseAriane;
