/* eslint-disable i18next/no-literal-string -- Phase 12b-B 二开 UI 硬编码中文，未来统一 i18n 清洗。 */
import { Box, Typography } from '@mui/material';
import { useCallback, useEffect, useState } from 'react';

import {
  fetchSocConnectors,
  refreshSocConnector,
  type SocConnectorOutputDto,
} from '../../../../actions/soc_connectors/soc-connector-actions';
import SocConnectorStatusList from './SocConnectorStatusList';
import { type SocConnectorStatusItem } from './socConnectorTypes';

const toStatusItem = (dto: SocConnectorOutputDto): SocConnectorStatusItem => ({
  connectorId: dto.connector_id,
  displayName: dto.display_name,
  status: dto.status,
  message: dto.message ?? undefined,
  availableRuleCount: dto.available_rule_count ?? undefined,
  lastCheckedAt: dto.last_checked_at ?? undefined,
});

const SocConnectorsIndex = () => {
  const [items, setItems] = useState<SocConnectorStatusItem[]>([]);

  const refreshAll = useCallback(async () => {
    const response: { data: SocConnectorOutputDto[] } = await fetchSocConnectors();
    setItems((response.data ?? []).map(toStatusItem));
  }, []);

  useEffect(() => {
    refreshAll();
  }, [refreshAll]);

  const handleRefreshOne = async (connectorId: string) => {
    const response: { data: SocConnectorOutputDto } = await refreshSocConnector(connectorId);
    const updated = toStatusItem(response.data);
    setItems(prev => prev.map(item => (item.connectorId === connectorId ? updated : item)));
  };

  return (
    <Box>
      <Typography variant="h4" sx={{ mb: 2 }}>SOC 连接器</Typography>
      <SocConnectorStatusList
        items={items}
        onRefreshOne={handleRefreshOne}
        onRefreshAll={refreshAll}
      />
    </Box>
  );
};

export default SocConnectorsIndex;
