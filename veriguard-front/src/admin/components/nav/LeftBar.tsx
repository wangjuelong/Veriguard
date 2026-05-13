import { DashboardOutlined, DescriptionOutlined, DevicesOtherOutlined, DnsOutlined, GpsFixedOutlined, Groups3Outlined, GroupsOutlined, HubOutlined, InsertChartOutlined, MovieFilterOutlined, PersonOutlined, SchoolOutlined, SettingsOutlined, ShieldOutlined, SubscriptionsOutlined, TimelineOutlined, TuneOutlined, VerifiedUserOutlined, ViewModuleOutlined } from '@mui/icons-material';
import { Binoculars, NewspaperVariantMultipleOutline, SecurityNetwork, SelectGroup, Target } from 'mdi-material-ui';
import { useContext } from 'react';

import LeftMenu from '../../../components/common/menu/leftmenu/LeftMenu';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';

const LeftBar = () => {
  const ability = useContext(AbilityContext);
  const entries = [
    {
      userRight: true,
      items: [
        {
          path: `/admin`,
          icon: () => (<DashboardOutlined />),
          label: 'Home',
          userRight: true,
        },
        {
          path: `/admin/workspaces/custom_dashboards`,
          icon: () => (<InsertChartOutlined />),
          label: 'Dashboards',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.DASHBOARDS),
        },
        {
          path: '/admin/findings',
          icon: () => (<Binoculars />),
          label: 'Findings',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.FINDINGS),
        },
      ],
    },
    {
      userRight: true,
      items: [
        // 攻击编排（PRD §2.4 / spec §6.1）—— B 系列五个并列入口收纳为单顶层 + 4 子项.
        {
          path: `/admin/attack_chains`,
          icon: () => (<MovieFilterOutlined />),
          label: 'Attack chain orchestration',
          href: 'attack_chains',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.ASSESSMENT),
          subItems: [
            {
              link: '/admin/attack_chains',
              label: 'AttackChains',
              icon: () => (<MovieFilterOutlined fontSize="small" />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.ASSESSMENT),
            },
            {
              link: '/admin/attack_chain_runs',
              label: 'Simulations',
              icon: () => (<HubOutlined fontSize="small" />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.ASSESSMENT),
            },
            {
              link: '/admin/validation_parameter_sets',
              label: 'Validation parameter sets',
              icon: () => (<TuneOutlined fontSize="small" />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.ASSESSMENT),
            },
            {
              link: '/admin/integrations/soc_connectors',
              label: 'SOC connectors',
              icon: () => (<ShieldOutlined fontSize="small" />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS),
            },
          ],
        },
      ],
    },
    {
      userRight: true,
      items: [
        {
          path: `/admin/assets`,
          icon: () => (<DnsOutlined />),
          label: 'Assets',
          href: 'assets',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS) || ability.can(ACTIONS.ACCESS, SUBJECTS.SECURITY_PLATFORMS),
          subItems: [
            {
              link: '/admin/assets/endpoints',
              label: 'Endpoints',
              icon: () => (<DevicesOtherOutlined fontSize="small" />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS),
            },
            {
              link: '/admin/assets/asset_groups',
              label: 'Asset groups',
              icon: () => (<SelectGroup fontSize="small" />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS),
            },
            {
              link: '/admin/assets/security_platforms',
              label: 'Security platforms',
              icon: () => (<SecurityNetwork fontSize="small" />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.SECURITY_PLATFORMS),
            },
          ],
        },
        {
          path: `/admin/teams`,
          icon: () => (<Groups3Outlined />),
          label: 'People',
          href: 'teams',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.TEAMS_AND_PLAYERS),
          subItems: [
            {
              link: '/admin/teams/players',
              label: 'Players',
              icon: () => (<PersonOutlined fontSize="small" />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.TEAMS_AND_PLAYERS),
            },
            {
              link: '/admin/teams/teams',
              label: 'Teams',
              icon: () => (<GroupsOutlined fontSize="small" />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.TEAMS_AND_PLAYERS),
            },
          ],
        },
        {
          path: `/admin/components`,
          icon: () => (<NewspaperVariantMultipleOutline />),
          label: 'Components',
          href: 'components',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.DOCUMENTS)
            || ability.can(ACTIONS.ACCESS, SUBJECTS.LESSONS_LEARNED),
          subItems: [
            {
              link: '/admin/components/documents',
              label: 'Documents',
              icon: () => (<DescriptionOutlined fontSize="small" />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.DOCUMENTS),
            },
            {
              link: '/admin/components/lessons',
              label: 'Lessons learned',
              icon: () => (<SchoolOutlined fontSize="small" />),
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.LESSONS_LEARNED),
            },
          ],
        },
      ],
    },
    {
      userRight: true,
      items: [
        // Atomic testings 不在 spec §6.1 攻击编排子项里（OpenBAS 原生功能），保留为独立顶层避免回归.
        {
          path: `/admin/atomic_testings`,
          icon: () => (<Target />),
          label: 'Atomic testings',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.ASSESSMENT),
        },
        {
          path: `/admin/payloads`,
          icon: () => (<SubscriptionsOutlined />),
          label: 'Payloads',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PAYLOADS),
        },
        {
          path: `/admin/veriguard`,
          icon: () => (<VerifiedUserOutlined />),
          label: '沙箱',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS),
        },
        {
          path: `/admin/combinations`,
          icon: () => (<ViewModuleOutlined />),
          label: '攻击组合',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS),
        },
        {
          path: `/admin/coverage`,
          icon: () => (<GpsFixedOutlined />),
          label: '边界覆盖度',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS),
        },
        {
          path: `/admin/stability`,
          icon: () => (<TimelineOutlined />),
          label: '稳定性趋势',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.ASSESSMENT),
        },
      ],
    },
    {
      userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS),
      items: [
        {
          path: `/admin/settings`,
          icon: () => (<SettingsOutlined />),
          label: 'Settings',
          href: 'settings',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS),
          subItems: [
            {
              link: '/admin/settings/parameters',
              label: 'Parameters',
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS),
            },
            {
              link: '/admin/settings/security',
              label: 'Security',
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS),
            },
            {
              link: '/admin/settings/asset_rules',
              label: 'Customization',
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS),
            },
            {
              link: '/admin/settings/taxonomies',
              label: 'Taxonomies',
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS),
            },
            {
              link: '/admin/settings/data_ingestion',
              label: 'Data ingestion',
              userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS),
            },
          ],
        },
      ],
    },
  ];
  return (
    <LeftMenu entries={entries} />
  );
};

export default LeftBar;
