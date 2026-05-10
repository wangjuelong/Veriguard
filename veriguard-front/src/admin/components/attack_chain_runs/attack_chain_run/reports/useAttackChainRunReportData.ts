import { type Dispatch, type SetStateAction, useEffect, useState } from 'react';

import { type AttackChainNodeHelper } from '../../../../../actions/attack_chain_nodes/node-helper';
import {
  exerciseAttackChainNodesResultOutput,
  fetchAttackChainRunExpectationResult,
  fetchLessonsAnswers,
  fetchLessonsCategories,
  fetchLessonsQuestions,
  fetchPlayersByAttackChainRun,
} from '../../../../../actions/attack_chain_runs/attack_chain_run-action';
import { type AttackChainRunsHelper } from '../../../../../actions/attack_chain_runs/attack_chain_run-helper';
import { fetchAttackChainRun, fetchAttackChainRunTeams } from '../../../../../actions/AttackChainRun';
import { type UserHelper } from '../../../../../actions/helper';
import { fetchReportFromSimulation } from '../../../../../actions/reports/report-actions';
import { type ReportsHelper } from '../../../../../actions/reports/report-helper';
import { type TeamsHelper } from '../../../../../actions/teams/team-helper';
import { useHelper } from '../../../../../store';
import {
  type AttackChainNodeResultOutput,
  type AttackChainRun,
  type ExpectationResultsByType,
  type LessonsAnswer,
  type LessonsCategory,
  type LessonsQuestion,
  type Report,
  type ReportInformation,
  type Team,
  type User,
} from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import ReportInformationType from './ReportInformationType';

export interface AttackChainRunReportData {
  nodes: AttackChainNodeResultOutput[];
  exerciseExpectationResults: ExpectationResultsByType[];
  attack_chain_run: AttackChainRun;
  lessonsCategories: LessonsCategory[];
  lessonsQuestions: LessonsQuestion[];
  lessonsAnswers: LessonsAnswer[];
  teams: Team[];
  teamsMap: Record<string, Team>;
  usersMap: Record<string, User>;
}

interface ReturnType {
  loading: boolean;
  report: Report;
  displayModule: (moduleType: ReportInformationType) => boolean;
  setReloadReportDataCount: Dispatch<SetStateAction<number>>;
  reportData: AttackChainRunReportData;
}

const useAttackChainRunReportData = (reportId: Report['report_id'], exerciseId: AttackChainRun['attack_chain_run_id']): ReturnType => {
  const dispatch = useAppDispatch();
  const [loading, setLoading] = useState(true);
  const [reloadReportDataCount, setReloadReportDataCount] = useState(0);
  const [exerciseExpectationResults, setResults] = useState<ExpectationResultsByType[]>([]);
  const [nodes, setAttackChainNodes] = useState<AttackChainNodeResultOutput[]>([]);

  const {
    report,
    attack_chain_run,
    lessonsCategories,
    lessonsQuestions,
    lessonsAnswers,
    teams,
    teamsMap,
    usersMap,
  } = useHelper((helper: AttackChainNodeHelper & ReportsHelper & AttackChainRunsHelper & TeamsHelper & UserHelper) => {
    return {
      report: helper.getReport(reportId),
      attack_chain_run: helper.getAttackChainRun(exerciseId),
      lessonsCategories: helper.getAttackChainRunLessonsCategories(exerciseId),
      lessonsQuestions: helper.getAttackChainRunLessonsQuestions(exerciseId),
      lessonsAnswers: helper.getAttackChainRunLessonsAnswers(exerciseId),
      teamsMap: helper.getTeamsMap(),
      teams: helper.getAttackChainRunTeams(exerciseId),
      usersMap: helper.getUsersMap(),
    };
  });

  const displayModule = (moduleType: ReportInformationType): boolean => {
    return report?.report_informations.find((info: ReportInformation) => info.report_informations_type === moduleType)?.report_informations_display;
  };

  useDataLoader(() => {
    dispatch(fetchReportFromSimulation(exerciseId, reportId)).then(() => {
      setReloadReportDataCount(prev => prev + 1);
    });
  });

  useEffect(() => {
    if (reloadReportDataCount > 0) {
      setLoading(true);
      const fetchPromises = [];
      fetchPromises.push(dispatch(fetchAttackChainRun(exerciseId)));
      fetchPromises.push(dispatch(fetchAttackChainRunTeams(exerciseId)));
      if (displayModule(ReportInformationType.PLAYER_SURVEYS)) {
        fetchPromises.push(
          dispatch(fetchLessonsQuestions(exerciseId)),
          dispatch(fetchLessonsAnswers(exerciseId)),
          dispatch(fetchLessonsCategories(exerciseId)),
          dispatch(fetchPlayersByAttackChainRun(exerciseId)),
        );
      }
      if (displayModule(ReportInformationType.SCORE_DETAILS)) {
        fetchPromises.push(fetchAttackChainRunExpectationResult(exerciseId).then(result => setResults(result.data)));
      }

      if (displayModule(ReportInformationType.INJECT_RESULT)) {
        fetchPromises.push(exerciseAttackChainNodesResultOutput(exerciseId).then((result) => {
          // Sort the nodes by tracking_sent_date
          const sortedAttackChainNodes = result.data.sort((a: AttackChainNodeResultOutput, b: AttackChainNodeResultOutput) => {
            const dateA = a.node_status?.tracking_sent_date;
            const dateB = b.node_status?.tracking_sent_date;
            if ((dateA === undefined || dateA === null) && (dateB !== undefined && dateB !== null)) return 1;
            if ((dateA !== undefined && dateA !== null) && (dateB === undefined || dateB === null)) return -1;
            if ((dateA === undefined || dateA === null) && (dateB === undefined || dateB === null)) return 0;
            return dateA!.localeCompare(dateB!); // non-null assertion since we've checked above
          });
          setAttackChainNodes(sortedAttackChainNodes);
        }));
      }
      Promise.all(fetchPromises).then(() => {
        setLoading(false);
      });
    }
  }, [reloadReportDataCount]);

  return {
    loading,
    report,
    displayModule,
    setReloadReportDataCount,
    reportData: {
      nodes,
      exerciseExpectationResults,
      attack_chain_run,
      lessonsCategories,
      lessonsQuestions,
      lessonsAnswers,
      teams,
      teamsMap,
      usersMap,
    },
  };
};

export default useAttackChainRunReportData;
