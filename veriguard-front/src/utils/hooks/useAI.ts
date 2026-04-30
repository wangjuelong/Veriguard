/*
Copyright (c) 2025 Veriguard.
*/

import useAuth from './useAuth';

const useAI = (): {
  configured?: boolean | null;
  enabled?: boolean | null;
} => {
  const { settings } = useAuth();
  return {
    enabled: settings.platform_ai_enabled,
    configured: settings.platform_ai_has_token,
  };
};

export default useAI;
