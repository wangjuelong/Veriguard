import { lazy, Suspense, useContext } from 'react';
import { Navigate, Route, Routes } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { errorWrapper } from '../../../components/Error';
import Loader from '../../../components/Loader';
import NotFound from '../../../components/NotFound';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import ProtectedRoute from '../../../utils/permissions/ProtectedRoute';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';

const Documents = lazy(() => import('./documents/Documents'));
const Lessons = lazy(() => import('./lessons/LessonsTemplates'));
const LessonIndex = lazy(() => import('./lessons/Index'));

const useStyles = makeStyles()(() => ({ root: { flexGrow: 1 } }));

const Index = () => {
  const { classes } = useStyles();
  const ability = useContext(AbilityContext);

  const order = ['DOCUMENTS', 'LESSONS_LEARNED'] as const;

  const subjectToRoute: Record<typeof order[number], string> = {
    DOCUMENTS: 'documents',
    LESSONS_LEARNED: 'lessons',
  };

  const accessibleSubject = order.find(subject => ability.can(ACTIONS.ACCESS, subject));

  const navigation = accessibleSubject ? subjectToRoute[accessibleSubject] : '/';

  return (
    <div className={classes.root}>
      <Suspense fallback={<Loader />}>
        <Routes>
          <Route path="" element={<Navigate to={navigation} replace={true} />} />
          <Route
            path="documents"
            element={(
              <ProtectedRoute
                checks={[{
                  action: ACTIONS.ACCESS,
                  subject: SUBJECTS.DOCUMENTS,
                }]}
                Component={errorWrapper(Documents)()}
              />
            )}
          />
          <Route
            path="lessons"
            element={(
              <ProtectedRoute
                checks={[{
                  action: ACTIONS.ACCESS,
                  subject: SUBJECTS.LESSONS_LEARNED,
                }]}
                Component={errorWrapper(Lessons)()}
              />
            )}
          />
          <Route
            path="lessons/:lessonsTemplateId/*"
            element={(
              <ProtectedRoute
                checks={[{
                  action: ACTIONS.ACCESS,
                  subject: SUBJECTS.LESSONS_LEARNED,
                }]}
                Component={errorWrapper(LessonIndex)()}
              />
            )}
          />
          {/* Not found */}
          <Route path="*" element={<NotFound />} />
        </Routes>
      </Suspense>
    </div>
  );
};

export default Index;
