/* eslint-disable i18next/no-literal-string -- spec §6.7: M1 sandbox UI uses
   hardcoded Chinese to match existing pattern; future M-x will migrate to
   react-intl when sandbox UI stabilizes. */
import { Box, Stack, Typography } from '@mui/material';

import SandboxList from './sandbox/SandboxList';

const VeriguardConsole = () => (
  <Box sx={{
    display: 'flex',
    flexDirection: 'column',
    gap: 2,
  }}
  >
    <Stack direction="row" justifyContent="space-between" alignItems="center">
      <Box>
        <Typography variant="h4">沙箱管理</Typography>
        <Typography variant="body2" color="text.secondary">
          沙箱预设与网络访问控制策略
        </Typography>
      </Box>
    </Stack>
    <SandboxList />
  </Box>
);

export default VeriguardConsole;
