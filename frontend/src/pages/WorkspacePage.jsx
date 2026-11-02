import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { readStorage, writeStorage } from '../lib/storage.js';
import { request, matchErrorMessage } from '../api/client.js';
import { useSessionProfile } from '../hooks/useSessionProfile.js';
import CareerCockpitLayout from '../components/CareerCockpitLayout.jsx';
import CareerDashboard from './CareerDashboard.jsx';
import ProfileOnboardingPage from './ProfileOnboardingPage.jsx';
import CareerAssessmentPage from './CareerAssessmentPage.jsx';
import ResumeAnalyzerPage from './ResumeAnalyzerPage.jsx';
import SkillGapPage from './SkillGapPage.jsx';
import RoadmapPage from './RoadmapPage.jsx';
import ProjectRecommenderPage from './ProjectRecommenderPage.jsx';
import { ApplicationsPage, InterviewCoachPage, JobMatcherPage } from './CareerToolsPages.jsx';
import CareerRecommendations from '../components/CareerRecommendations.jsx';
import { useAuth } from '../auth/AuthContext.jsx';
import { authApi } from '../api/auth.js';

const QUICK_SKILL_NAMES = [
  'Complex Problem Solving',
  'Critical Thinking',
  'Active Listening',
  'Writing',
  'Programming',
  'Time Management',
];
const INTERESTS = [
  { code: 'R', title: 'Hands-on work', description: 'Building, fixing, and practical tasks' },
  { code: 'I', title: 'Investigating', description: 'Research, analysis, and solving problems' },
  { code: 'A', title: 'Creating', description: 'Design, ideas, and creative expression' },
  { code: 'S', title: 'Helping people', description: 'Teaching, supporting, and caring' },
  { code: 'E', title: 'Leading', description: 'Persuading, organizing, and taking initiative' },
  { code: 'C', title: 'Planning', description: 'Organizing details, records, and processes' },
];
const LEVELS = [
  { value: 1, label: 'New to it' },
  { value: 2, label: 'Learning' },
  { value: 3, label: 'Capable' },
  { value: 4, label: 'Strong' },
  { value: 5, label: 'Advanced' },
];

const validTheme = (value) => ['light', 'dark'].includes(value);
const validSavedCareers = (value) => Array.isArray(value) && value.every((career) =>
  career && typeof career === 'object' && typeof career.onet_soc_code === 'string'
  && typeof career.occupation_title === 'string' && Number.isFinite(Number(career.match_score)));
const validRoadmapState = (value) => value !== null && typeof value === 'object' && !Array.isArray(value)
  && Object.values(value).every((checked) => typeof checked === 'boolean');
const validInterviewDrafts = (value) => value !== null && typeof value === 'object' && !Array.isArray(value)
  && Object.entries(value).every(([index, draft]) => /^\d+$/.test(index) && typeof draft === 'string');

function BackendStartHelp() {
  return <details className="backend-help"><summary>Start matching API</summary><code>Run .\start_backend.ps1 from the project folder.</code></details>;
}

function WorkspacePage() {
  const location = useLocation();
  const navigate = useNavigate();
  const careerCode = location.pathname.startsWith('/careers/') ? decodeURIComponent(location.pathname.slice('/careers/'.length)) : null;
  const page = location.pathname === '/results' || location.pathname.startsWith('/results/') ? 'explore' : (careerCode ? 'career-detail' : ({ '/roadmap': 'roadmap', '/projects': 'projects', '/resume': 'resume', '/skill-gaps': 'skill-gaps', '/interview': 'interview', '/job-matcher': 'job-matcher', '/applications': 'applications', '/saved': 'saved', '/settings': 'settings', '/overview': 'overview', '/profile': 'profile', '/onboarding': 'profile', '/assessment': 'assessment', '/careers': 'careers', '/compare': 'compare' }[location.pathname] || 'explore'));
  const setPage = (nextPage) => navigate(({ overview: '/overview', explore: '/match', profile: '/profile', assessment: '/assessment', roadmap: '/roadmap', projects: '/projects', skillGaps: '/skill-gaps', 'skill-gaps': '/skill-gaps', resume: '/resume', interview: '/interview', jobMatcher: '/job-matcher', 'job-matcher': '/job-matcher', applications: '/applications', account: '/account', saved: '/saved', settings: '/settings', careers: '/careers', careerRoles: '/careers', compare: '/compare' })[nextPage] || '/match');
  const [theme, setTheme] = useState(() => readStorage('pathfinder-theme', 'dark', validTheme));
  const [savedCareers, setSavedCareers] = useState(() => readStorage('pathfinder-saved', [], validSavedCareers));
  const [checkedRoadmap, setCheckedRoadmap] = useState(() => readStorage('pathfinder-roadmap', {}, validRoadmapState));
  const [roadmapDates, setRoadmapDates] = useState({});
  const [interviewDrafts, setInterviewDrafts] = useState(() => readStorage('pathfinder-interview-drafts', {}, validInterviewDrafts, 'sessionStorage'));
  const [interviewIndex, setInterviewIndex] = useState(0);
  const [skills, setSkills] = useState([]);
  const { profile, ratings, interests, results, step, setProfile, setRatings, setInterests, setResults, setStep } = useSessionProfile();
  const { enabled: authEnabled, accessToken, loading: authLoading, user: authUser } = useAuth();
  const [loadingSkills, setLoadingSkills] = useState(true);
  const [loadingMatch, setLoadingMatch] = useState(false);
  const [connection, setConnection] = useState('checking');
  const [notice, setNotice] = useState('');
  const [noticeError, setNoticeError] = useState(false);
  const [careerQuery, setCareerQuery] = useState('');
  const [careerFilter, setCareerFilter] = useState('');
  const [careerCatalog, setCareerCatalog] = useState([]);
  const [careerDetail, setCareerDetail] = useState(null);
  const [careerLoading, setCareerLoading] = useState(false);
  const [careerError, setCareerError] = useState('');
  const [compareCodes, setCompareCodes] = useState([]);
  const [resourceCatalog, setResourceCatalog] = useState([]);
  const [whatIfRatings, setWhatIfRatings] = useState({});
  const [whatIfData, setWhatIfData] = useState(null);
  const [whatIfLoading, setWhatIfLoading] = useState(false);
  const [whatIfError, setWhatIfError] = useState('');
  const [fullProfile, setFullProfile] = useState(() => readStorage('pathfinder-full-profile', {}));
  const [targetRoleSaving, setTargetRoleSaving] = useState('');
  const [accountProfileLoaded, setAccountProfileLoaded] = useState(false);

  const coreSkills = useMemo(() => QUICK_SKILL_NAMES
    .map((name) => skills.find((skill) => skill.name === name))
    .filter(Boolean), [skills]);
  const ratedCount = Object.values(ratings).filter(Boolean).length;
  const currentSkillRatings = { ...(fullProfile.skills || {}), ...ratings };
  const whatIfBaseSkills = Object.fromEntries(coreSkills
    .filter((skill) => Number(currentSkillRatings[skill.name]) >= 1 && Number(currentSkillRatings[skill.name]) <= 5)
    .map((skill) => [skill.name, Number(currentSkillRatings[skill.name])]));
  const firstName = profile.name.trim().split(/\s+/)[0];

  useEffect(() => {
    const media = window.matchMedia?.('(prefers-color-scheme: dark)');
    const applyTheme = () => { document.documentElement.dataset.theme = theme === 'system' ? (media?.matches ? 'dark' : 'light') : theme; };
    applyTheme();
    media?.addEventListener?.('change', applyTheme);
    writeStorage('pathfinder-theme', theme, validTheme);
    return () => media?.removeEventListener?.('change', applyTheme);
  }, [theme]);
  useEffect(() => { writeStorage('pathfinder-saved', savedCareers, validSavedCareers); }, [savedCareers]);
  useEffect(() => { writeStorage('pathfinder-roadmap', checkedRoadmap, validRoadmapState); }, [checkedRoadmap]);
  useEffect(() => { writeStorage('pathfinder-interview-drafts', interviewDrafts, validInterviewDrafts, 'sessionStorage'); }, [interviewDrafts]);
  useEffect(() => {
    const updateProfile = (event) => {
      if (!event.detail || typeof event.detail !== 'object') return;
      setFullProfile((current) => {
        const next = { ...current, ...event.detail };
        writeStorage('pathfinder-full-profile', next);
        return next;
      });
      setProfile((current) => ({ ...current, name: event.detail.name || current.name, stage: event.detail.stage || event.detail.education || current.stage }));
    };
    window.addEventListener('pathfinder:profile-updated', updateProfile);
    return () => window.removeEventListener('pathfinder:profile-updated', updateProfile);
  }, [setProfile]);
  useEffect(() => {
    if (!authEnabled || !accessToken) return undefined;
    let active = true;
    authApi.account('/saved-careers', accessToken).then((items) => { if (active && items?.length) setSavedCareers(items); }).catch(() => {});
    authApi.account('/roadmap', accessToken).then((items) => {
      if (active && items?.length) {
        setCheckedRoadmap(Object.fromEntries(items.map((item) => [item.key, Boolean(item.complete)])));
        setRoadmapDates(Object.fromEntries(items.filter((item) => item.completedAt).map((item) => [item.key, item.completedAt])));
      }
    }).catch(() => {});
    return () => { active = false; };
  }, [authEnabled, accessToken]);
  useEffect(() => {
    if (!authEnabled || authLoading || !accountProfileLoaded || !accessToken || (!profile.name && !profile.stage && !profile.studyArea)) return;
    const timer = window.setTimeout(() => {
      const complete = readStorage('pathfinder-full-profile', {});
      authApi.account('/profile', accessToken, { method: 'PUT', body: { profile: { ...complete, ...profile,
        skills: complete.skills || ratings, interests: complete.interests || interests } } })
        .catch((error) => setNotice(error.message || 'Could not save your profile.'));
    }, 500);
    return () => window.clearTimeout(timer);
  }, [authEnabled, authLoading, accountProfileLoaded, accessToken, profile, ratings, interests]);
  useEffect(() => {
    if (authEnabled && authLoading) return undefined;
    if (!authEnabled || !accessToken) {
      setFullProfile(readStorage('pathfinder-full-profile', {}));
      const localRecommendations = readStorage('pathfinder-recommendations', []);
      if (localRecommendations.length) setResults(localRecommendations);
      setAccountProfileLoaded(true);
      return undefined;
    }
    setAccountProfileLoaded(false);
    let active = true;
    Promise.all([authApi.account('/profile', accessToken), authApi.account('/recommendations', accessToken)])
      .then(([savedProfile, recommendations]) => {
        if (!active) return;
        const accountProfile = savedProfile && Object.keys(savedProfile).some((key)=>key!=='targetRole'&&key!=='completeness') ? savedProfile : {name:'',stage:'',studyArea:'',email:authUser?.email||'',skills:{},interests:[],completeness:0};
        setFullProfile(accountProfile); writeStorage('pathfinder-full-profile', accountProfile);
        setProfile({name:accountProfile.name||'',stage:accountProfile.stage||accountProfile.education||'',studyArea:accountProfile.studyArea||''});
        setRatings(accountProfile.skills||{}); setInterests(accountProfile.interests||[]);
        if (Array.isArray(recommendations) && recommendations.length) { setResults(recommendations); writeStorage('pathfinder-recommendations', recommendations); }
        else { setResults(null); writeStorage('pathfinder-recommendations', []); }
      }).catch((error) => { if (active) setNotice(error.message || 'Could not load your saved career profile.'); })
      .finally(()=>{if(active)setAccountProfileLoaded(true);});
    return () => { active = false; };
  }, [authEnabled, authLoading, accessToken]);
  useEffect(() => {
    if (page !== 'careers' && page !== 'career-detail') return undefined;
    let active = true;
    setCareerLoading(true); setCareerError('');
    const path = page === 'career-detail' ? '/careers/' + encodeURIComponent(careerCode)
      : '/careers?query=' + encodeURIComponent(careerQuery.trim()) + '&riasec=' + encodeURIComponent(careerFilter) + '&limit=20&offset=0';
    const timer = window.setTimeout(() => request(path).then((data) => {
      if (!active) return;
      if (page === 'career-detail') setCareerDetail(data); else setCareerCatalog(data.results || []);
    }).catch((error) => { if (active) setCareerError(error.message || 'Career catalog could not be loaded.'); })
      .finally(() => { if (active) setCareerLoading(false); }), page === 'careers' ? 250 : 0);
    return () => { active = false; window.clearTimeout(timer); };
  }, [page, careerCode, careerQuery, careerFilter]);
  useEffect(() => {
    if (page !== 'roadmap' || resourceCatalog.length) return;
    let active = true;
    request('/resources').then((items) => { if (active) setResourceCatalog(items); }).catch(() => { if (active) setResourceCatalog([]); });
    return () => { active = false; };
  }, [page, resourceCatalog.length]);
  const navItems = [
    ['overview', '⌂', 'Overview'], ['explore', '✳', 'Career match'], ['roadmap', '↗', 'My roadmap'],
    ['interview', '▤', 'Interview studio'], ['saved', '☆', 'Saved careers'], ['careers', '⌕', 'Career explorer'], ['compare', '⇄', 'Compare careers'], ['settings', '⚙', 'Settings'],
  ];
  const interviewPrompts = [
    'Tell me about a project where you solved a difficult problem.',
    'How do you learn a new tool or skill when a task depends on it?',
    'Describe a time you worked with someone whose approach differed from yours.',
    'What kind of work gives you energy, and why?',
    'Tell me about a mistake that changed how you work.',
    'How would you explain a complex idea to a beginner?',
  ];
  const roadmapItems = results?.[0]?.skill_gaps?.slice().sort((a,b) => (b.gap_priority || b.gap_size) - (a.gap_priority || a.gap_size)).slice(0, 5).map((gap) => ({
    id: results[0].onet_soc_code + ':' + gap.element_name,
    title: 'Build ' + gap.element_name.toLowerCase(),
    detail: 'A development area for ' + results[0].occupation_title + '.',
    resource: resourceCatalog.find((item) => item.skill.toLowerCase() === gap.element_name.toLowerCase()) || null,
  })) || [
    { id: 'profile', title: 'Complete your career profile', detail: 'Choose work interests and rate six strengths to get a personalized plan.' },
    { id: 'research', title: 'Explore three career directions', detail: 'Compare day-to-day work, skills, and training before choosing a path.' },
    { id: 'conversation', title: 'Talk with someone in a role you like', detail: 'Ask about their first steps, typical week, and skills they use most.' },
  ];
  function toggleSaved(career) {
    const exists = savedCareers.some((item) => item.onet_soc_code === career.onet_soc_code);
    if (authEnabled && accessToken) {
      (exists ? authApi.account('/saved-careers/' + encodeURIComponent(career.onet_soc_code), accessToken, { method: 'DELETE' })
        : authApi.account('/saved-careers', accessToken, { method: 'POST', body: { onetSocCode: career.onet_soc_code, career } }))
        .then(() => setSavedCareers((current) => exists ? current.filter((item) => item.onet_soc_code !== career.onet_soc_code) : [...current, career]))
        .catch((error) => setNotice(error.message || 'Could not update your saved careers.'));
      return;
    }
    setSavedCareers((current) => exists ? current.filter((item) => item.onet_soc_code !== career.onet_soc_code) : [...current, career]);
  }

  function setProfileField(field, value) {
    setProfile((current) => ({ ...current, [field]: value }));
    setResults(null);
  }

  function toggleRoadmapItem(item, complete) {
    const next = { ...checkedRoadmap, [item.id]: complete };
    const completedAt = complete ? new Date().toISOString() : null;
    setCheckedRoadmap(next);
    setRoadmapDates((current) => ({ ...current, [item.id]: completedAt }));
    if (authEnabled && accessToken) {
      const items = roadmapItems.map((entry) => ({ key: entry.id, title: entry.title, detail: entry.detail,
        complete: Boolean(next[entry.id]), completedAt: entry.id === item.id ? completedAt : roadmapDates[entry.id] || null }));
      authApi.account('/roadmap', accessToken, { method: 'PUT', body: { items } })
        .catch((error) => setNotice(error.message || 'Could not save roadmap progress.'));
    }
  }

  function toggleCompare(code) {
    setCompareCodes((current) => {
      if (current.includes(code)) return current.filter((item) => item !== code);
      if (current.length >= 3) { setNotice('Compare up to three careers at a time.'); return current; }
      return [...current, code];
    });
  }

  async function selectTargetRole(role) {
    if (!role?.onet_soc_code) return;
    setTargetRoleSaving(role.onet_soc_code); setNotice(''); setNoticeError(false);
    try {
      let selected = { code: role.onet_soc_code, title: role.occupation_title };
      if (authEnabled && accessToken) selected = await authApi.account('/target-role', accessToken, { method: 'PUT', body: { onetSocCode: role.onet_soc_code } });
      const next = { ...readStorage('pathfinder-full-profile', fullProfile), targetRole: selected };
      writeStorage('pathfinder-full-profile', next); setFullProfile(next);
      setNotice(`${selected.title} is now your target role.`);
    } catch (error) { setNotice(error.message || 'Could not save the selected target role. Try again.'); setNoticeError(true); }
    finally { setTargetRoleSaving(''); }
  }

  function setWhatIfRating(name, value) {
    if (!whatIfBaseSkills[name]) {
      setWhatIfError('Rate this skill in your profile before including it in a what-if comparison.'); return;
    }
    if (value && Number(value) !== Number(whatIfBaseSkills[name]) && !Object.hasOwn(whatIfRatings, name) && Object.keys(whatIfRatings).length >= 3) {
      setWhatIfError('Change no more than three skill ratings per scenario.'); return;
    }
    setWhatIfRatings((current) => {
      const next = { ...current };
      if (!value || Number(value) === Number(whatIfBaseSkills[name])) delete next[name];
      else next[name] = Number(value);
      setWhatIfError(''); return next;
    });
    setWhatIfData(null);
  }

  async function runWhatIf() {
    const studentSkills = whatIfBaseSkills;
    if (Object.keys(studentSkills).length < 3) {
      setWhatIfError('Rate at least three skills in your profile first, then return here to compare a scenario.'); return;
    }
    const changes = Object.fromEntries(Object.entries(whatIfRatings).filter(([name, value]) => Object.hasOwn(studentSkills, name) && Number(value) !== Number(studentSkills[name])));
    if (!Object.keys(changes).length) { setWhatIfError('Change at least one skill rating to compare a scenario.'); return; }
    setWhatIfLoading(true); setWhatIfError(''); setWhatIfData(null);
    try {
      const data = await request('/what-if', { method: 'POST', body: JSON.stringify({
        profile: { studentSkills, studentInterests: interests, topN: 5, assessedOnly: true }, changes,
      }) });
      setWhatIfData(data);
    } catch (error) { setWhatIfError(error.message || 'The what-if scenario could not be calculated.'); }
    finally { setWhatIfLoading(false); }
  }

  function receiveSkills(rows, status) {
    setSkills(rows);
    setRatings((current) => Object.fromEntries(
      rows.filter((skill) => QUICK_SKILL_NAMES.includes(skill.name))
        .map((skill) => [skill.name, current[skill.name] || '']),
    ));
    setConnection(status);
  }

  async function loadSkills() {
    setLoadingSkills(true);
    setNotice('');
    try {
      const rows = await request('/skills');
      if (!Array.isArray(rows) || !rows.length) throw new Error('empty catalog');
      receiveSkills(rows, 'online');
    } catch {
      try {
        const response = await fetch('/skills.json');
        if (!response.ok) throw new Error('catalog unavailable');
        receiveSkills(await response.json(), 'offline');
      } catch {
        setConnection('offline');
        setNotice('The skill catalog could not load. Refresh the page or check the local project files.');
      }
    } finally {
      setLoadingSkills(false);
    }
  }

  useEffect(() => { loadSkills(); }, []);

  function toggleInterest(code) {
    setInterests((current) => {
      if (current.includes(code)) return current.filter((item) => item !== code);
      if (current.length >= 3) {
        setNotice('Choose up to three work interests.');
        return current;
      }
      setNotice('');
      return [...current, code];
    });
    setResults(null);
  }

  function chooseRating(name, value) {
    setRatings((current) => ({ ...current, [name]: value }));
    setResults(null);
  }

  async function submit(event) {
    event.preventDefault();
    setNotice('');
    setLoadingMatch(true);
    try {
      const studentSkills = Object.fromEntries(
        coreSkills.map((skill) => [skill.name, Number(ratings[skill.name])]),
      );
      const data = await request('/matches', {
        method: 'POST',
        body: JSON.stringify({
          studentSkills,
          studentInterests: interests,
          topN: 10,
          assessedOnly: true,
        }),
      });
      setResults(data);
      setConnection('online');
      navigate('/results/local-' + Date.now());
      requestAnimationFrame(() => {
        const section = document.getElementById('results');
        if (section && typeof section.scrollIntoView === 'function') section.scrollIntoView({ behavior: 'smooth', block: 'start' });
      });
    } catch (error) {
      if (error.message === 'NETWORK' || error.status === 503) setConnection('offline');
      setNotice(matchErrorMessage(error));
    } finally {
      setLoadingMatch(false);
    }
  }

  const canContinue = step === 1
    ? Boolean(profile.stage)
    : step === 2
      ? interests.length > 0
      : ratedCount === coreSkills.length && coreSkills.length > 0;

  function nextStep() {
    if (!canContinue) return;
    setNotice('');
    setStep((current) => Math.min(3, current + 1));
  }

  function previousStep() {
    setNotice('');
    setStep((current) => Math.max(1, current - 1));
  }

  return <CareerCockpitLayout navItems={navItems} page={page} setPage={setPage} savedCount={savedCareers.length} firstName={firstName} stage={profile.stage} connection={connection} loadingSkills={loadingSkills} onReconnect={loadSkills} theme={theme} onThemeChange={setTheme} openMentorOnLoad={location.pathname === '/mentor'}>

    {page === 'overview' && <CareerDashboard firstName={firstName} profile={{ ...fullProfile, ...profile, completeness: fullProfile.completeness ?? profile.completeness }} ratings={ratings} interests={interests} results={results} savedCount={savedCareers.length} completedSteps={Object.values(checkedRoadmap).filter(Boolean).length} totalSteps={roadmapItems.length} setPage={setPage} onAskMentor={() => window.dispatchEvent(new Event('pathfinder:open-mentor'))} targetRole={fullProfile.targetRole} />}
    {page === 'profile' && <ProfileOnboardingPage />}
    {page === 'assessment' && <CareerAssessmentPage />}
    {page === 'resume' && <ResumeAnalyzerPage />}
    {page === 'skill-gaps' && <SkillGapPage />}
    {page === 'roadmap' && <RoadmapPage guestItems={roadmapItems} guestChecks={checkedRoadmap} onGuestToggle={toggleRoadmapItem} />}
    {page === 'projects' && <ProjectRecommenderPage />}
    {false && page === 'overview' && <section className="dashboard-page"><div className="dashboard-welcome"><div><p className="eyebrow"><span className="eyebrow-line" /> YOUR CAREER WORKSPACE</p><h1>{firstName ? 'Welcome back, ' + firstName + '.' : 'Make room for what’s next.'}</h1><p>Explore career directions, build a plan, and prepare for the conversations that move you forward.</p><button className="primary-button" onClick={() => setPage('explore')}>Explore career matches <span>→</span></button></div><div className="welcome-art"><div className="art-orbit orbit-one"/><div className="art-orbit orbit-two"/><div className="art-center">YOU</div><span className="art-dot dot-one"/><span className="art-dot dot-two"/><span className="art-dot dot-three"/><small>CAREER PATHS, IN CONTEXT</small></div></div><div className="stat-grid"><article><span>PROFILE STATUS</span><strong>{results ? 'Matched' : interests.length ? 'In progress' : 'Not started'}</strong><small>{results ? 'Your recommendations are ready' : 'A few questions unlock your workspace'}</small></article><article><span>CAREER IDEAS</span><strong>{results?.length || '—'}</strong><small>{results ? 'Personalized possibilities' : 'Complete your profile to see matches'}</small></article><article><span>SAVED CAREERS</span><strong>{savedCareers.length}</strong><small>Paths you want to revisit</small></article><article><span>PLAN PROGRESS</span><strong>{Object.values(checkedRoadmap).filter(Boolean).length}/{roadmapItems.length}</strong><small>Small steps add up</small></article></div><div className="section-heading"><div><p className="eyebrow"><span className="eyebrow-line" /> YOUR TOOLKIT</p><h2>Different ways to move forward</h2></div><span>Choose what you need today</span></div><div className="tool-grid">{navItems.slice(1,5).map(([id, icon, label], index) => <button className="tool-card" key={id} onClick={() => setPage(id)}><span className="tool-icon">{icon}</span><small>0{index+1} / PATHFINDER</small><strong>{label}</strong><p>{['See career ideas based on your interests and strengths.','Turn your strongest match into practical next steps.','Practice thoughtful answers for real career conversations.','Keep the paths you want to compare in one place.'][index]}</p><span className="tool-link">Open section →</span></button>)}</div>{results?.length > 0 && <div className="recent-panel"><div><p className="eyebrow"><span className="eyebrow-line" /> RECENT MATCH</p><h2>{results[0].occupation_title}</h2><p>Your strongest current match · {Math.round(results[0].match_score)}% profile match</p></div><button className="secondary-button" onClick={() => setPage('explore')}>View recommendations →</button></div>}</section>}


    {page === 'interview' && <InterviewCoachPage />}
    {page === 'job-matcher' && <JobMatcherPage setPage={setPage} />}
    {page === 'applications' && <ApplicationsPage />}

    {page === 'saved' && <section className="workspace-page"><div className="page-title"><p className="eyebrow"><span className="eyebrow-line" /> YOUR SHORTLIST</p><h1>Careers worth coming back to.</h1><p>Save roles from your match results to compare and revisit whenever you’re ready.</p></div>{savedCareers.length ? <div className="career-list">{savedCareers.map((career)=><article className="career-card" key={career.onet_soc_code}><div className="career-top"><div className="rank">★</div><div className="career-name"><h3>{career.occupation_title}</h3><p>{career.onet_soc_code}{career.job_zone?' · Job zone '+career.job_zone:''}</p></div><div className="score"><strong>{Math.round(career.match_score)}<small>%</small></strong><span>profile match</span></div><button className="icon-button" aria-label={'Remove '+career.occupation_title+' from saved careers'} onClick={()=>toggleSaved(career)}>×</button></div><p className="description">{career.description}</p></article>)}</div>:<div className="empty-panel"><span>☆</span><h2>Your shortlist starts with a spark.</h2><p>Run a career match and save the roles that make you curious.</p><button className="primary-button" onClick={()=>setPage('explore')}>Explore career matches <span>→</span></button></div>}</section>}

    {page === 'careers' && <section className="workspace-page career-discovery"><div className="page-title"><p className="eyebrow"><span className="eyebrow-line" /> CAREER LIBRARY</p><h1>Explore the work behind the titles.</h1><p>Search the local O*NET career catalog and filter by interest family.</p></div><CareerRecommendations roles={results || []} targetRole={fullProfile.targetRole} onSelect={selectTargetRole} savingCode={targetRoleSaving} onStartAssessment={()=>setPage(Object.keys(ratings).length>=3 ? "assessment" : "profile")} />{notice && <div className={`form-alert ${noticeError?"error":"success"}`} role="status">{notice}</div>}<div className="career-search"><label>Search careers<input aria-label="Search careers" value={careerQuery} maxLength={80} onChange={(event)=>setCareerQuery(event.target.value)} placeholder="Try designer, analyst, healthcare…" /></label><label>Interest area<select aria-label="Filter careers by interest" value={careerFilter} onChange={(event)=>setCareerFilter(event.target.value)}><option value="">All interest types</option>{INTERESTS.map((item)=><option key={item.code} value={item.code}>{item.title}</option>)}</select></label></div>{careerLoading ? <p role="status">Loading careers…</p> : careerError ? <div className="empty-panel" role="alert">{careerError}<p>Check that the career data has been generated and the backend is running.</p></div> : careerCatalog.length ? <div className="career-list">{careerCatalog.map((career)=><article className="career-card discovery-card" key={career.onet_soc_code}><div className="career-top"><div className="career-name"><h2>{career.occupation_title}</h2><p>{career.onet_soc_code}{career.job_zone ? ' · Job zone '+career.job_zone : ''}</p></div><button className="secondary-button" onClick={()=>navigate('/careers/'+encodeURIComponent(career.onet_soc_code))}>Career details →</button></div><p className="description">{career.description}</p></article>)}</div> : !careerLoading && !careerError ? <div className="empty-panel">No careers matched those filters. Try a broader search.</div> : null}</section>}
    {page === 'career-detail' && <section className="workspace-page"><button className="text-button" onClick={()=>setPage('careers')}>← Back to career explorer</button>{careerLoading ? <p role="status">Loading career details…</p> : careerError ? <div className="empty-panel" role="alert">{careerError}</div> : careerDetail && <><div className="page-title"><p className="eyebrow"><span className="eyebrow-line" /> O*NET CAREER PROFILE</p><h1>{careerDetail.occupation_title}</h1><p>{careerDetail.onet_soc_code} · Job zone {careerDetail.job_zone || 'not listed'}</p></div><p className="description">{careerDetail.description}</p><div className="career-detail-codes">{['riasec_top1','riasec_top2','riasec_top3'].filter((key)=>careerDetail[key]).map((key)=><span key={key}>{INTERESTS.find((item)=>item.code===careerDetail[key])?.title || careerDetail[key]}</span>)}</div><h2>Important skills in this occupation</h2><div className="gap-list">{careerDetail.requirements?.slice(0,15).map((skill)=><div className="gap-row" key={skill.element_id}><strong>{skill.element_name}</strong><span>{skill.skill_category}</span><span className="gap-meta">Importance {Number(skill.importance).toFixed(1)} · Level {Number(skill.required_level).toFixed(1)}</span></div>)}</div><button className="secondary-button" onClick={()=>toggleCompare(careerDetail.onet_soc_code)}>{compareCodes.includes(careerDetail.onet_soc_code)?'✓ Added to compare':'Add to compare'}</button></>}</section>}
    {page === 'compare' && <section className="workspace-page"><div className="page-title"><p className="eyebrow"><span className="eyebrow-line" /> SIDE-BY-SIDE</p><h1>Compare career paths.</h1><p>Select up to three saved careers from your shortlist to compare their profile and score details.</p></div>{notice.includes("Compare up to three") && <p role="status">{notice}</p>}{savedCareers.length ? <div className="compare-picker">{savedCareers.map((career)=><label key={career.onet_soc_code}><input type="checkbox" checked={compareCodes.includes(career.onet_soc_code)} onChange={()=>toggleCompare(career.onet_soc_code)} />{career.occupation_title}</label>)}</div> : <div className="empty-panel">Save careers from your results to compare them here.</div>}{compareCodes.length ? <div className="compare-grid">{compareCodes.map((code)=>{const career=savedCareers.find((item)=>item.onet_soc_code===code);return career&&<article className="career-card" key={code}><h2>{career.occupation_title}</h2><p>{code}</p><strong>{Math.round(career.match_score)}% profile match</strong><p>{career.description}</p><p>{career.job_zone?'Job zone '+career.job_zone:'Job zone not listed'}</p></article>;})}</div> : null}</section>}

    {page === 'settings' && <section className="workspace-page settings-page"><div className="page-title"><p className="eyebrow"><span className="eyebrow-line" /> PREFERENCES</p><h1>Make Pathfinder feel like yours.</h1><p>Adjust how your workspace looks and manage your local profile data.</p></div><div className="settings-card"><div><span className="settings-icon">◐</span><div><strong>Appearance</strong><small>Choose a theme for your workspace.</small></div></div><div className="theme-options" role="group" aria-label="Theme setting">{[['light','☀','Light'],['dark','☾','Dark']].map(([id,icon,label])=><button type="button" key={id} aria-pressed={theme===id} className={theme===id?'selected':''} onClick={()=>setTheme(id)}><span>{icon}</span>{label}</button>)}</div></div><div className="settings-card"><div><span className="settings-icon">◎</span><div><strong>Your profile</strong><small>{profile.name || 'Name not added'} · {profile.stage || 'Career stage not selected'}</small></div></div><button className="secondary-button" onClick={()=>setPage('profile')}>Edit profile →</button></div><div className="settings-card"><div><span className="settings-icon">⌁</span><div><strong>Saved on this device</strong><small>Your theme, shortlist, and roadmap are stored in this browser.</small></div></div><span className="local-badge">LOCAL ONLY</span></div></section>}

    {page === 'explore' && <>
    <section className="hero" id="top">
      <div className="hero-copy">
        <p className="eyebrow"><span className="eyebrow-line" /> A QUICKER WAY TO FIND YOUR DIRECTION</p>
        <h1>Start with you.<br />Find your <em>next step.</em></h1>
        <p className="hero-text">A short profile is all it takes to explore careers that fit your interests and strengths. No long skills checklist.</p>
        <a className="hero-cta" href="#profile">Build my quick profile <span>↓</span></a>
        <div className="quick-facts"><span><strong>3</strong> quick steps</span><i /><span><strong>6</strong> skill ratings</span><i /><span><strong>2 min</strong> to complete</span></div>
      </div>
      <div className="hero-panel" id="how-it-works">
        <div className="panel-top"><span>YOUR CAREER SNAPSHOT</span><span className="panel-index">01 — 03</span></div>
        <h2>A few things about you<br />can open up new paths.</h2>
        <div className="journey">
          <div className="journey-item"><span className="journey-number">01</span><div><strong>Your background</strong><small>Study area and career stage</small></div><span className="journey-check">✓</span></div>
          <div className="journey-item"><span className="journey-number">02</span><div><strong>Your interests</strong><small>Choose the work you enjoy</small></div><span className="journey-arrow">↗</span></div>
          <div className="journey-item"><span className="journey-number">03</span><div><strong>Your strengths</strong><small>Rate six practical skill areas</small></div><span className="journey-arrow">↗</span></div>
        </div>
        <div className="panel-foot"><span>O*NET OCCUPATION DATA</span><span>1,016 careers to explore</span></div>
      </div>
    </section>

    <section className="assessment" id="profile" aria-labelledby="assessment-title">
      <div className="wizard-heading">
        <div><p className="eyebrow"><span className="eyebrow-line" /> YOUR QUICK PROFILE</p><h2 id="assessment-title">{step === 1 ? 'First, a little about you.' : step === 2 ? 'What kind of work sounds good?' : 'What are your strengths?'}</h2><p className="section-intro">{step === 1 ? 'Add your current stage and study or work area. Your interests and strengths will power the recommendations.' : step === 2 ? 'Pick up to three. There are no right or wrong answers.' : 'Rate six skill areas. These are real O*NET skills used by the matching engine.'}</p></div>
        <div className="progress-card"><div className="progress-label"><span>PROFILE STEP</span><strong>{String(step).padStart(2, '0')}<i> / 03</i></strong></div><div className="wizard-progress"><span className={step >= 1 ? 'done' : ''} /><span className={step >= 2 ? 'done' : ''} /><span className={step >= 3 ? 'done' : ''} /></div><small>{step === 1 ? 'About you' : step === 2 ? 'Interests' : 'Strengths'}</small></div>
      </div>

      {connection === 'offline' && !notice && <div className="offline-note"><span className="offline-icon">i</span><span><strong>Profile ready</strong> The career matching service is offline. You can finish your profile now and connect for results afterward.</span><BackendStartHelp /><button type="button" onClick={loadSkills} disabled={loadingSkills}>{loadingSkills ? 'Checking…' : 'Reconnect'}</button></div>}
      {notice && <div className="notice" role="status"><span>{notice}</span>{connection === 'offline' && <BackendStartHelp />}<button type="button" onClick={() => setNotice('')} aria-label="Dismiss message">×</button></div>}

      {loadingSkills ? <div className="loading-state"><span className="spinner" />Preparing your quick profile…</div>
        : !coreSkills.length ? <div className="empty-state">The quick profile skills are unavailable. Refresh the page or check the local skill catalog.</div>
        : <form onSubmit={step === 3 ? submit : (event) => { event.preventDefault(); nextStep(); }}>
          {step === 1 && <div className="profile-fields">
            <label className="field"><span>What should we call you? <small>OPTIONAL</small></span><input value={profile.name} onChange={(event) => setProfileField('name', event.target.value)} placeholder="Your first name" autoComplete="given-name" /></label>
            <label className="field"><span>Where are you in your journey? <b>REQUIRED</b></span><select value={profile.stage} onChange={(event) => setProfileField('stage', event.target.value)} required><option value="" disabled>Select your current stage</option><option value="Student">Student</option><option value="Recent graduate">Recent graduate</option><option value="Working professional">Working professional</option><option value="Career changer">Exploring a career change</option></select></label>
            <label className="field field-wide"><span>What are you studying or working in? <small>OPTIONAL</small></span><input value={profile.studyArea} onChange={(event) => setProfileField('studyArea', event.target.value)} placeholder="e.g. Computer science, commerce, healthcare" /></label>
          </div>}

          {step === 2 && <div className="interest-grid">{INTERESTS.map((interest) => <button type="button" key={interest.code} className={'interest-card ' + (interests.includes(interest.code) ? 'selected' : '')} aria-pressed={interests.includes(interest.code)} onClick={() => toggleInterest(interest.code)}><span className="interest-code">{interest.code}</span><span className="interest-text"><strong>{interest.title}</strong><small>{interest.description}</small></span><span className="interest-selected">{interests.includes(interest.code) ? '✓' : '+'}</span></button>)}</div>}

          {step === 3 && <><div className="rating-instructions"><span>YOUR LEVEL</span>{LEVELS.map((level) => <span key={level.value}><i className={'level l' + level.value} />{level.value} {level.label}</span>)}</div><div className="quick-skill-list">{coreSkills.map((skill) => <div className="quick-skill-row" key={skill.id}><div><strong>{skill.name}</strong><small>{skill.category} · O*NET skill</small></div><div className="rating-control" role="group" aria-label={'Rate ' + skill.name}>{LEVELS.map((level) => <button type="button" aria-label={'Rate ' + skill.name + ' ' + level.value + ' of 5'} aria-pressed={Number(ratings[skill.name]) === level.value} className={Number(ratings[skill.name]) === level.value ? 'chosen' : ''} onClick={() => chooseRating(skill.name, level.value)} key={level.value}>{level.value}</button>)}</div></div>)}</div><p className="rating-footnote">Your match uses only these six rated skills, together with your selected interests. Unrated skills are not treated as weaknesses.</p></>}

          <div className="wizard-actions">
            {step > 1 ? <button className="back-button" type="button" onClick={previousStep}>← Back</button> : <span className="privacy-note">Your profile stays on this page.</span>}
            {step < 3
              ? <button className="primary-button" type="submit" disabled={!canContinue}>Continue <span>→</span></button>
              : <button className="primary-button" type="submit" disabled={!canContinue || loadingMatch}>{loadingMatch ? <><span className="spinner light" />Finding careers…</> : <>Show my career ideas <span>→</span></>}</button>}
          </div>
        </form>}
    </section>

    {results && <section className="results" id="results" aria-labelledby="results-title"><div className="results-heading"><div><p className="eyebrow"><span className="eyebrow-line" /> YOUR CAREER SNAPSHOT</p><h2 id="results-title">{firstName ? 'Career ideas for ' + firstName + '.' : 'Career ideas to explore.'}</h2><p className="section-intro">Based on your interests and six rated O*NET skills. These are starting points for exploration.</p></div><span className="result-count">{results.length} IDEAS</span></div>
      {(profile.stage || profile.studyArea || interests.length > 0) && <div className="profile-summary"><span>{profile.stage || 'Career explorer'}</span>{profile.studyArea && <span>{profile.studyArea}</span>}{interests.map((code) => <span key={code}>{INTERESTS.find((item) => item.code === code)?.title}</span>)}</div>}
      <section className="what-if-panel" aria-labelledby="what-if-title"><div><p className="eyebrow">EXPLORE A POSSIBLE NEXT STEP</p><h3 id="what-if-title">What if you strengthened a skill?</h3><p>Change one to three ratings. Pathfinder will compare two real matcher runs and show how the top five careers shift.</p></div><div className="what-if-controls">{coreSkills.map((skill)=><label key={skill.id}>{skill.name}<select aria-label={'What if rating for ' + skill.name} disabled={!whatIfBaseSkills[skill.name]} value={whatIfRatings[skill.name] || ''} onChange={(event)=>setWhatIfRating(skill.name,event.target.value)}><option value="">{whatIfBaseSkills[skill.name] ? `Keep current (${whatIfBaseSkills[skill.name]})` : 'Rate in profile first'}</option>{LEVELS.map((level)=><option key={level.value} value={level.value}>{level.value} · {level.label}</option>)}</select></label>)}</div><p className="what-if-hint">Choose a different rating for up to three skills, then compare.</p><button type="button" className="secondary-button" disabled={whatIfLoading} onClick={runWhatIf}>{whatIfLoading ? 'Comparing…' : 'Compare scenario'}</button>{whatIfError && <p role="alert" className="what-if-error">{whatIfError} {whatIfError.includes('Rate at least three skills') && <button type="button" className="text-button" onClick={()=>setPage('profile')}>Go to profile →</button>}</p>}{whatIfData && <div className="what-if-results"><div><h4>Current profile</h4>{whatIfData.baseline.map((career)=><p key={career.onet_soc_code}>#{career.rank} {career.occupation_title} · {Math.round(career.match_score)}%</p>)}</div><div><h4>With your changes</h4>{whatIfData.scenario.map((career)=><p key={career.onet_soc_code}>#{career.rank} {career.occupation_title} · {Math.round(career.match_score)}% <small>{whatIfData.comparison.find((item)=>item.onet_soc_code===career.onet_soc_code)?.rank_change > 0 ? '↑ rank' : whatIfData.comparison.find((item)=>item.onet_soc_code===career.onet_soc_code)?.rank_change < 0 ? '↓ rank' : whatIfData.comparison.find((item)=>item.onet_soc_code===career.onet_soc_code)?.rank_change === null ? 'New in top five' : 'same rank'}</small></p>)}</div></div>}</section>
      {!results.length ? <div className="empty-state">No careers met the current match threshold. Try a different combination of interests and strengths.</div> : <div className="career-list">{results.map((career) => <article className="career-card" key={career.onet_soc_code}><div className="career-top"><div className="rank">{String(career.rank).padStart(2, '0')}</div><div className="career-name"><h3>{career.occupation_title}</h3><p>{career.onet_soc_code}{career.job_zone ? ' · Job zone ' + career.job_zone : ''}</p></div><div className="score"><strong>{Math.round(career.match_score)}<small>%</small></strong><span>profile match</span></div><button className={'save-button '+(savedCareers.some((item)=>item.onet_soc_code===career.onet_soc_code)?'saved':'')} onClick={()=>toggleSaved(career)}>{savedCareers.some((item)=>item.onet_soc_code===career.onet_soc_code)?'★ Saved':'☆ Save'}</button></div><p className="description">{career.description}</p><div className="score-track"><span style={{ width: Math.min(career.match_score, 100) + '%' }} /></div><div className="score-breakdown"><div><span>Skill similarity</span><strong>{Number(career.base_score || 0).toFixed(1)}%</strong></div><div><span>Interest alignment bonus</span><strong>+{Number(career.riasec_bonus || 0).toFixed(1)}</strong></div><div><span>Assessed skill coverage</span><strong>{career.skills_assessed || 0} / {career.skills_total || 0}</strong></div></div><p className="score-disclaimer">This score is a similarity measure based on your profile, not a hiring probability.</p><p className="contributing-skills"><strong>Strengths that contribute:</strong> {(career.strong_skills || []).slice(0, 4).join(', ') || 'Keep rating skills to see areas that match.'}</p><details><summary>Skills to strengthen <span>{career.skill_gaps.length} in your profile areas</span></summary><div className="gap-list">{career.skill_gaps.slice(0, 6).map((gap) => <div className="gap-row" key={gap.element_name}><span>{gap.element_name}</span><span className={'priority ' + gap.priority_label.toLowerCase()}>{gap.priority_label}</span><span className="gap-meta">Gap {gap.gap_size.toFixed(1)}</span></div>)}</div></details></article>)}</div>}
    </section>}

    <footer><a className="brand footer-brand" href="#top"><span className="brand-mark">P</span><span>pathfinder<small>CAREER GUIDANCE</small></span></a><span>Career ideas grounded in O*NET 31.0 data.</span><a href="#top">Back to top ↑</a></footer>
    </>}
  </CareerCockpitLayout>;
}

export default WorkspacePage;



