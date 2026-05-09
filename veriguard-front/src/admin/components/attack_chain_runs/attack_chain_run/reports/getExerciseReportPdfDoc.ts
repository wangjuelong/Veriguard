import { toPng } from 'html-to-image';
import type { Content, ContentTable, TDocumentDefinitions } from 'pdfmake/interfaces';

import { type Translate } from '../../../../../components/i18n';
import LogoText from '../../../../../static/images/logo_text_light.svg';
import { type AttackChainNodeResultOutput, type LessonsAnswer, type Report } from '../../../../../utils/api-types';
import { resolveUserName } from '../../../../../utils/String';
import convertMarkdownToPdfMake from './convertMarkdownToPdfMake';
import ReportInformationType from './ReportInformationType';
import { type AttackChainRunReportData } from './useAttackChainRunReportData';

const getBase64ImageFromURL = (url: string) => {
  return new Promise((resolve, reject) => {
    const img = new Image();
    const canvas = document.createElement('canvas');

    img.onload = () => {
      canvas.width = img.width;
      canvas.height = img.height;
      const ctx = canvas.getContext('2d');
      if (ctx) {
        ctx.drawImage(img, 0, 0);
        const dataURL = canvas.toDataURL('image/png');
        resolve(dataURL);
      } else {
        reject(new Error('Failed to get canvas context'));
      }
    };

    img.onerror = (error) => {
      reject(error);
    };

    img.src = url;
  });
};

const tableCustomLayout = (displayColumnLine: boolean, paddingTop: number) => ({
  hLineWidth: () => 0.5,
  vLineWidth: (i: number, node: ContentTable) => {
    if (displayColumnLine || (i === 0 || i === node.table.body[0].length)) {
      return 0.5;
    }
    return 0;
  },
  hLineColor: () => '#aaa',
  vLineColor: () => '#aaa',
  paddingLeft: () => 4,
  paddingRight: () => 4,
  paddingTop: () => paddingTop,
  paddingBottom: () => paddingTop,
});

const imageBorder = {
  hLineWidth: () => 0.5,
  vLineWidth: () => 0.5,
  hLineColor: () => '#aaa',
  vLineColor: () => '#aaa',
};

interface Props {
  report: Report;
  reportData: AttackChainRunReportData;
  displayModule: (moduleType: ReportInformationType) => boolean;
  tPick: (input?: Record<string, string>) => string;
  fldt: (input?: string) => string;
  t: Translate;
}

const getAttackChainRunReportPdfDocDefinition = async ({
  report,
  reportData,
  displayModule,
  tPick,
  fldt,
  t,
}: Props): Promise<TDocumentDefinitions> => {
  // Fetch reports images
  const modulesImages = [
    'main_information',
    'score_details',
    'lessons_categories',
    'attack_chain_run_distribution_score_by_team',
    'attack_chain_run_distribution_score_over_time',
    'attack_chain_run_distribution_total_score_by_team',
    'attack_chain_run_distribution_score_over_time_by_team',
    'attack_chain_run_distribution_total_score_by_node_type',
    'attack_chain_run_distribution_score_over_time_by_node',
    'attack_chain_run_distribution_total_score_by_organization',
    'attack_chain_run_distribution_total_score_by_player',
    'attack_chain_run_distribution_total_score_by_node',
  ];
  const fetchPromises = [getBase64ImageFromURL(LogoText).then(img => ({
    key: 'veriguard_logo',
    img,
  }))];
  modulesImages.forEach((id) => {
    const element = document.getElementById(id);
    if (element) {
      fetchPromises.push(toPng(element).then((img: string) => ({
        key: id,
        img,
      })));
    }
  });
  (reportData.nodes || []).forEach((node) => {
    const element = document.getElementById(`node_expectations_${node.node_id}`);
    if (element) {
      fetchPromises.push(
        toPng(element).then((img: string) => ({
          key: `node_${node.node_id}`,
          img,
        })),
      );
    }
  });

  // AttackChainNode Result page
  const findCommentsByAttackChainNodeId = (injectId: AttackChainNodeResultOutput['node_id']) => (report?.report_nodes_comments ?? []).find(c => c.node_id === injectId)?.report_node_comment ?? null;
  const injectResultPage = (imagesMap: Map<string, string>) => ([
    {
      text: t('AttackChainNodes results'),
      tocItem: ['mainToc'],
      pageBreak: 'before',
      style: 'header',
    },
    {
      style: 'tableStyle',
      table: {
        widths: ['auto', 'auto', 'auto', 'auto', 'auto', 190],
        body: [
          [t('Type'), t('Title'), t('Execution date'), t('Scores'), t('Targets'), t('Comments')].map(title => ({
            text: title,
            style: 'tableTitle',
          })),
          ...reportData.nodes.map((node) => {
            return [
              { text: tPick(node.node_injector_contract?.injector_contract_labels) },
              { text: node.node_title },
              { text: fldt(node.node_status?.tracking_sent_date) || 'N/A' },
              {
                image: imagesMap.get(`node_${node.node_id}`),
                width: 60,
              },
              { text: node.node_targets?.map(target => target.target_name).join(', ') },
              { stack: convertMarkdownToPdfMake(findCommentsByAttackChainNodeId(node.node_id) || '') },
            ];
          }),
        ],
      },
      layout: tableCustomLayout(false, 10),
    },
  ]);

  // AttackChainRun Details page
  const exerciseDetailsPage = (imagesMap: Map<string, string>) => {
    const doubleColumns = [
      [
        {
          title: 'Distribution of score by team (in % of expectations)',
          img: imagesMap.get('attack_chain_run_distribution_score_by_team'),
        },
        {
          title: 'Teams scores over time (in % of expectations)',
          img: imagesMap.get('attack_chain_run_distribution_score_over_time'),
        },
      ],
      [
        {
          title: 'Distribution of total score by team',
          img: imagesMap.get('attack_chain_run_distribution_total_score_by_team'),
        },
        {
          title: 'Teams scores over time)',
          img: imagesMap.get('attack_chain_run_distribution_score_over_time_by_team'),
        },
      ],
      [
        {
          title: 'Distribution of total score by node type',
          img: imagesMap.get('attack_chain_run_distribution_total_score_by_node_type'),
        },
        {
          title: 'AttackChainNode types scores over time',
          img: imagesMap.get('attack_chain_run_distribution_score_over_time_by_node'),
        },
      ],
    ];

    return [
      {
        text: t('AttackChainRun details'),
        tocItem: ['mainToc'],
        pageBreak: 'before',
        style: 'header',
      },
      ...doubleColumns.flatMap(columns => (
        {
          columns: columns.flatMap(col => (
            {
              width: '*',
              stack: [
                {
                  text: t(col.title),
                  style: 'chartTitle',
                },
                {
                  image: col.img as string,
                  width: 260,
                  margin: [-5, 0, 0, 0],
                },
              ],
            }
          )),
          columnGap: 10,
        }
      )),
      {
        columns: [
          {
            width: '50%',
            stack: [
              {
                text: t('Distribution of total score by organization'),
                style: 'chartTitle',
              },
              {
                image: imagesMap.get('attack_chain_run_distribution_total_score_by_organization'),
                width: 250,
                margin: [-5, 0, 0, 0],
              },
            ],
          }, {
            width: '25%',
            stack: [
              {
                text: t('Distribution of total score by player'),
                style: 'chartTitle',
              },
              {
                image: imagesMap.get('attack_chain_run_distribution_total_score_by_player'),
                width: 130,
              },
            ],
          }, {
            width: '25%',
            stack: [
              {
                text: t('Distribution of total score by node'),
                style: 'chartTitle',
              },
              {
                image: imagesMap.get('attack_chain_run_distribution_total_score_by_node'),
                width: 130,
              },
            ],
          }],
        margin: [0, 20, 0, 0],
      },
    ];
  };

  // Players Surveys page
  const playersSurveysPage = () => [
    {
      text: t('Player surveys'),
      tocItem: ['mainToc'],
      pageBreak: 'before',
      style: 'header',
    },
    ...reportData.lessonsCategories.sort(lesson => lesson.lessons_category_order || 0).map((category) => {
      const lessonQuestions = reportData.lessonsQuestions
        .filter(q => (category.lessons_category_questions || []).includes(q.lessonsquestion_id))
        .sort((a, b) => (a.lessons_question_order || 0) - (b.lessons_question_order || 0));

      return ([
        {
          text: [t('Category'), ` : ${category.lessons_category_name}`],
          style: 'markdownHeaderH1',
        },
        { text: [t('Targeted teams'), ` : ${(category.lessons_category_teams || []).map(teamId => reportData.teams.find(team => team.team_id === teamId)?.team_name).join(', ') || '-'}`] },
        ...lessonQuestions.flatMap((question) => {
          const lessonsAnswers = (question.lessons_question_answers || [])
            .map(answerId => reportData.lessonsAnswers.find(answer => answer.lessonsanswer_id === answerId));
          const totalScore = (lessonsAnswers || []).reduce((sum, answer) => sum + (answer?.lessons_answer_score || 0), 0);
          const getUserName = (answer: LessonsAnswer): string => {
            if (reportData.attack_chain_run.attack_chain_run_lessons_anonymized) return t('Anonymized');
            if (answer.lessons_answer_user) return resolveUserName(reportData.usersMap[answer.lessons_answer_user as string]);
            return '-';
          };
          return [
            {
              text: [t('Question'), ` : ${question.lessons_question_content}`],
              margin: [0, 6, 0, 0],
            },
            { text: [t('Total score'), ` : ${totalScore}`] },
            (lessonsAnswers.length > 0
              ? {
                  table: {
                    widths: ['auto', 'auto', '*', '*'],
                    margin: [0, 2, 0, 0],
                    body: [
                      [t('User'), t('Score'), t('What worked well'), t('What didn\'t work well')].map(title => ({
                        text: title,
                        style: 'tableTitle',
                      })),
                      ...(lessonsAnswers || []).map(answer => ([
                        answer && getUserName(answer),
                        answer?.lessons_answer_score,
                        answer?.lessons_answer_positive,
                        answer?.lessons_answer_negative,
                      ])),
                    ],
                  },
                  layout: tableCustomLayout(true, 6),
                }
              : { text: `${t('Answers:')}-` }),
          ];
        }),
        '\n',
      ]);
    }),
  ];

  const results = await Promise.all(fetchPromises);
  const imagesMap: Map<string, string> = new Map();
  results.forEach(({ key: key_1, img: img_3 }) => {
    imagesMap.set(key_1, img_3 as string);
  });

  const displayAttackChainNodeResultPage = displayModule(ReportInformationType.INJECT_RESULT);
  const displayGlobalObservation = displayModule(ReportInformationType.GLOBAL_OBSERVATION);
  const displayPlayerSurveys = displayModule(ReportInformationType.PLAYER_SURVEYS);
  const displayAttackChainRunDetails = imagesMap.has('attack_chain_run_distribution_score_by_team');

  return {
    compress: false,
    pageSize: 'A4',
    footer: (currentPage_1: number) => {
      return {
        columns: [
          {
            text: report.report_name,
            alignment: 'left',
            margin: [30, 10],
          },
          {
            text: currentPage_1,
            alignment: 'right',
            margin: [30, 10],
          }, // Right side
        ],
        margin: [0, 0, 0, 10],
        style: 'footerStyle',
      };
    },
    content: [
      // First Page
      {
        image: imagesMap.get('veriguard_logo'),
        width: 150,
        margin: [0, 0, 0, 40],
      },
      {
        text: report.report_name,
        style: 'reportTitle',
      },
      reportData.attack_chain_run.attack_chain_run_start_date
        ? {
            text: fldt(reportData.attack_chain_run.attack_chain_run_start_date),
            margin: [0, 10, 0, 0],
          }
        : {},
      reportData.attack_chain_run.attack_chain_run_teams?.length && reportData.attack_chain_run.attack_chain_run_teams?.length > 0
        ? {
            text: [{ text: t('Teams') }, { text: ` : ${reportData.attack_chain_run.attack_chain_run_teams?.map(teamId_1 => reportData.teams.find(team_1 => team_1.team_id === teamId_1)?.team_name).join(', ')}` }],
            margin: [0, 0, 0, 150],
          }
        : {},
      '\n',
      imagesMap.has('score_details')
        ? {
            table: {
              body: [
                [
                  {
                    image: imagesMap.get('score_details'),
                    width: 530,
                  },
                ],
              ],
            },
            layout: imageBorder,
          }
        : {},
      '\n',
      imagesMap.has('main_information')
        ? {
            table: {
              body: [
                [
                  {
                    image: imagesMap.get('main_information'),
                    width: 530,
                  },
                ],
              ],
            },
            layout: imageBorder,
          }
        : {},
      // Table of Contents Page
      ...((displayAttackChainNodeResultPage || displayGlobalObservation || displayPlayerSurveys || displayAttackChainRunDetails)
        ? [{
            toc: {
              id: 'mainToc',
              title: {
                text: t('Table of contents'),
                style: 'header',
                margin: [0, 0, 0, 20],
              },
            },
            pageBreak: 'before',
          }]
        : []),

      // AttackChainNode Results Page
      displayAttackChainNodeResultPage ? injectResultPage(imagesMap) : {},

      // Global Information Page
      ...(displayGlobalObservation
        ? [
            {
              text: t('Global observation'),
              tocItem: ['mainToc'],
              pageBreak: 'before',
              style: 'header',
            },
            { stack: convertMarkdownToPdfMake(report.report_global_observation || ' -') },
          ]
        : []),

      // Player surveys page
      displayPlayerSurveys && reportData.lessonsCategories.length > 0 ? playersSurveysPage() : [],

      // AttackChainRun details page
      displayAttackChainRunDetails ? exerciseDetailsPage(imagesMap) : [],
    ] as Content,
    styles: {
      reportTitle: {
        fontSize: 40,
        bold: true,
      },
      header: {
        bold: true,
        fontSize: 15,
        margin: [0, 0, 0, 15],
      },
      markdownHeaderH1: {
        bold: true,
        fontSize: 12,
        margin: [0, 10, 0, 10],
      },
      markdownHeaderH2: {
        bold: true,
        margin: [20, 10, 0, 10],
      },
      markdownHeaderH3: {
        bold: true,
        margin: [40, 10, 0, 10],
      },
      tableStyle: { fontSize: 8 },
      chartTitle: {
        fontSize: 8,
        bold: true,
      },
      tableTitle: {
        fontSize: 10,
        bold: true,
      },
      boldText: { bold: true },
      italicText: { italics: true },
    },
    defaultStyle: { fontSize: 10 },
    pageMargins: [30, 40, 30, 40],
  };
};

export default getAttackChainRunReportPdfDocDefinition;
