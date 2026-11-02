import { useEffect, useState } from 'react';
import Icon from '../components/Icon.jsx';
import { BarChart, ProgressRing, RadarChart, Timeline } from '../components/ui/Charts.jsx';
import { Badge, Button, Card, CardHeading, MetricCard } from '../components/ui/Primitives.jsx';
import { useAuth } from '../auth/AuthContext.jsx';
import { authApi } from '../api/auth.js';

export default function CareerDashboard({ firstName, profile, ratings, interests, results, savedCount, completedSteps, totalSteps, setPage, onAskMentor, targetRole }) {
  const { enabled, accessToken } = useAuth();
  const [resumeScore, setResumeScore] = useState(null); const [skillCompletion, setSkillCompletion] = useState(null); const [skillGapData, setSkillGapData] = useState(null);
  const [applicationSummary, setApplicationSummary] = useState(null); const [interviewScore, setInterviewScore] = useState(null); const [recommendedProjects, setRecommendedProjects] = useState([]);
  const [savedRoadmap, setSavedRoadmap] = useState(null);
  const [followUps, setFollowUps] = useState([]);
  useEffect(() => {
    if (!enabled || !accessToken) { setResumeScore(null); setSkillCompletion(null); setSkillGapData(null); setApplicationSummary(null); setInterviewScore(null); setRecommendedProjects([]); setSavedRoadmap(null); setFollowUps([]); return undefined; }
    let active = true;
    Promise.all([authApi.account('/resumes', accessToken), authApi.account('/skill-gaps', accessToken), authApi.account('/applications/summary', accessToken), authApi.account('/interviews', accessToken), authApi.account('/projects', accessToken), authApi.account('/roadmap', accessToken), authApi.account('/applications', accessToken)])
      .then(([history, gaps, applications, interviews, projects, roadmap, applicationList]) => { if (active) {
        setResumeScore(history?.[0]?.atsScore ?? null); setSkillCompletion(gaps?.targetRole?.code ? gaps.completionPercent : null); setSkillGapData(gaps?.targetRole?.code ? gaps : null);
        setApplicationSummary(applications || null); setInterviewScore(interviews?.find((item)=>item.status==='Completed')?.readinessScore ?? null);
        setRecommendedProjects((projects?.projects || []).filter((item)=>item.status==='In Progress'||item.status==='Completed'||item.saved).slice(0,2));
        setSavedRoadmap(roadmap?.generated ? roadmap : null);
        setFollowUps((applicationList||[]).filter((item)=>item.followUpDate).sort((a,b)=>String(a.followUpDate).localeCompare(String(b.followUpDate))).slice(0,2));
      } })
      .catch(() => { if (active) { setResumeScore(null); setSkillCompletion(null); setSkillGapData(null); setApplicationSummary(null); setInterviewScore(null); setRecommendedProjects([]); setSavedRoadmap(null); setFollowUps([]); } });
    return () => { active = false; };
  }, [enabled, accessToken]);
  const topRole = targetRole?.title || results?.[0]?.occupation_title || 'Choose a target role';
  const realMatches = Array.isArray(results) && results.length > 0;
  const roles = realMatches ? results.slice(0, 3).map((career, index) => ({
    title: career.occupation_title,
    score: Math.round(Number(career.match_score) || 0),
    detail: `${career.onet_soc_code} · ${career.job_zone ? `Job zone ${career.job_zone}` : 'Career match'}`,
    tone: ['violet', 'cyan', 'green'][index],
  })) : [];
  const roadmapPercent = savedRoadmap?.completionPercent ?? (totalSteps ? Math.round(completedSteps / totalSteps * 100) : 0);
  const roadmapCompleted = savedRoadmap?.completedItems ?? completedSteps; const roadmapTotal = savedRoadmap?.totalItems ?? totalSteps;
  const roadmapTasks = savedRoadmap?.phases?.flatMap((phase)=>phase.weeks?.flatMap((week)=>week.tasks||[])||[]).filter((task)=>!task.complete).slice(0,3).map((task,index)=>({title:task.title,meta:'Personal roadmap',date:index===0?'Next up':`Step ${index+1}`})) || [];
  const upcomingTasks = [...followUps.map((item)=>({title:`Follow up with ${item.company}`,meta:item.role,date:item.followUpDate})),...roadmapTasks].slice(0,3);
  const skillData = skillGapData?.skills?.length
    ? skillGapData.skills.filter((skill) => skill.gap > 0).slice(0, 4).map((skill) => ({ label: skill.name, value: Math.max(1, Math.min(5, Number(skill.gap))), display: `gap ${Number(skill.gap).toFixed(0)}` }))
    : realMatches && results[0]?.skill_gaps?.length
    ? results[0].skill_gaps.slice(0, 4).map((gap) => ({ label: gap.element_name, value: Math.max(1, Math.min(5, Number(gap.gap_size) || 2)), display: `gap ${Number(gap.gap_size || 0).toFixed(1)}` }))
    : Object.entries(ratings || {}).slice(0,4).map(([label,value])=>({label,value:Number(value),display:`${value}/5`}));
  const interestSignal = ['I','A','E','S','R','C'].map((code)=>interests?.includes(code)?4:1);
  const profileComplete = [Boolean(profile?.stage), Boolean(profile?.studyArea), Boolean(interests?.length), Object.values(ratings || {}).filter(Boolean).length >= 3].filter(Boolean).length;
  const profilePct = Number.isFinite(Number(profile?.completeness)) ? Number(profile.completeness) : Math.min(100, Math.round((profileComplete / 4) * 100));
  const readinessParts = realMatches ? [[Number(results[0].match_score || 0), .55], [profilePct, .3]] : [[profilePct, .85]];
  if (resumeScore != null) readinessParts.push([resumeScore, .1]);
  if (skillCompletion != null) readinessParts.push([skillCompletion, .05]);
  if (interviewScore != null) readinessParts.push([interviewScore, .1]);
  const readiness = Math.round(readinessParts.reduce((sum, [score, weight]) => sum + score * weight, 0) / readinessParts.reduce((sum, [, weight]) => sum + weight, 0));
  const today = new Intl.DateTimeFormat('en-US', { weekday: 'long', month: 'long', day: 'numeric' }).format(new Date());

  return <main className="dashboard-page cockpit-dashboard">
    <div className="dashboard-heading">
      <div><p className="eyebrow"><span className="eyebrow-pulse" /> YOUR CAREER COCKPIT <span className="eyebrow-separator">/</span> {today.toUpperCase()}</p>
        <h1>{firstName ? `Good to see you, ${firstName}.` : 'Your next move starts here.'}</h1>
        <p>One clear view of your direction, momentum, and next best action.</p></div>
      {enabled && accessToken ? <Badge tone="cyan" className="demo-indicator">ACCOUNT DATA</Badge> : <Badge tone="neutral" className="demo-indicator">PROFILE PREVIEW</Badge>}
    </div>

    <div className="cockpit-grid">
      <Card variant="hero" className="readiness-card">
        <div className="readiness-main"><div className="readiness-copy"><p className="card-eyebrow">CAREER READINESS</p><h2>{realMatches?'Your profile has a clear signal.':'Build your career profile.'}</h2><p>{realMatches?'Readiness combines your top role match and profile completeness.':'Complete your profile and assessment to calculate a personal readiness signal.'}</p>
          <Button icon="arrowRight" onClick={() => setPage(realMatches?'assessment':'profile')}>{realMatches?'Review assessment':'Build my profile'}</Button>
        </div><ProgressRing value={readiness} label="readiness" size={168} stroke={11} color="var(--accent-violet)" />
        </div>
        <div className="readiness-footer"><span><Icon name="target" size={16} /> CURRENT TARGET</span><strong>{topRole}</strong><button onClick={() => setPage(targetRole?'careers':realMatches?'assessment':'profile')}>{targetRole?'View direction':'Choose role'} <Icon name="arrow" size={14} /></button></div>
      </Card>

      <Card className="next-action-card" variant="glow">
        <div className="next-action-top"><span className="ai-orb"><Icon name="sparkles" size={18} /></span><Badge tone="cyan">NEXT BEST ACTION</Badge></div>
        <p className="card-eyebrow">A SMALL STEP, A CLEARER PATH</p>
        <h2>{results?.length ? 'Turn your top match into a plan.' : 'Tell us what you want from work.'}</h2>
        <p>{results?.length ? `Start with the skills that matter most for ${topRole}.` : 'A short profile helps surface career directions that fit your interests and strengths.'}</p>
        <Button variant="ai" icon="arrowRight" onClick={() => setPage(results?.length ? 'roadmap' : 'explore')}>{results?.length ? 'Open my roadmap' : 'Start my profile'}</Button>
        <span className="action-index">01 <i>/</i> 03</span>
      </Card>

      <MetricCard className="profile-metric" label="Profile completeness" value={`${profilePct}%`} caption="Education, skills, interests, experience, and goals" icon="user" tone="violet">
        <div className="mini-progress"><span style={{ width: `${profilePct}%` }} /></div>
        <button className="text-link" onClick={() => setPage('profile')}>Edit profile <Icon name="arrow" size={13} /></button>
      </MetricCard>
      <MetricCard className="applications-metric" label="Active applications" value={applicationSummary == null ? '—' : applicationSummary.active} change={applicationSummary == null ? 'SIGN IN TO TRACK' : `${applicationSummary.interviewsScheduled} interviews`} caption={applicationSummary == null ? 'Track your applications and follow-ups' : `${applicationSummary.followUpsDue} follow-ups due this week`} icon="briefcase" tone="cyan">
        <button className="text-link" onClick={() => setPage('applications')}>Open tracker <Icon name="arrow" size={13} /></button>
      </MetricCard>

      <Card className="roles-card">
        <CardHeading eyebrow="CAREER DIRECTIONS" title="Roles to explore" action={<button className="text-link" onClick={() => setPage('careers')}>View all <Icon name="arrow" size={13} /></button>} />
        <div className="role-list">{roles.map((role, index) => <button className="role-row" key={`${role.title}-${index}`} onClick={() => setPage('assessment')}>
          <span className={`role-rank role-rank--${role.tone}`}>0{index + 1}</span><span className="role-copy"><strong>{role.title}</strong><small>{role.detail}</small></span><span className="role-score">{role.score}<small>%</small></span><Icon name="arrow" size={15} />
        </button>)}</div>
        {!realMatches && <div className="empty-state-ui"><h3>No role recommendations yet</h3><p>Complete the career assessment to see O*NET matches based on your profile.</p><Button size="sm" onClick={()=>setPage('assessment')}>Start assessment</Button></div>}
      </Card>

      <Card className="skill-card">
        <CardHeading eyebrow="STRENGTHS & GAPS" title="Skill signal" action={<Badge tone="cyan">O*NET</Badge>} />
        <div className="skill-visual">{interests?.length ? <RadarChart labels={['Think', 'Create', 'Lead', 'Help', 'Build', 'Plan']} values={interestSignal} /> : <p className="mock-note">Complete your profile to visualize your interest pattern.</p>}
          <div className="skill-bars">{skillData.length ? <BarChart items={skillData} /> : <p className="mock-note">Rate your skills in Profile.</p>}</div>
        </div>
        <button className="text-link" onClick={() => setPage('skillGaps')}>Explore skill gaps{skillCompletion == null ? '' : ` · ${skillCompletion}% learned`} <Icon name="arrow" size={13} /></button>
      </Card>

      <Card className="roadmap-card">
        <CardHeading eyebrow="GROWTH PLAN" title="Roadmap progress" action={<span className="mono-value">{roadmapCompleted}/{roadmapTotal}</span>} />
        <div className="roadmap-meter"><ProgressRing value={roadmapPercent} label="complete" size={94} stroke={8} color="var(--accent-cyan)" /><div><strong>{roadmapPercent}%</strong><p>{completedSteps ? 'Nice momentum. Keep the next step small.' : 'Your plan is ready when you are.'}</p></div></div>
        <Button variant="secondary" icon="arrowRight" onClick={() => setPage('roadmap')}>Continue roadmap</Button>
      </Card>

      <div className="score-stack">
        <MetricCard label="Resume ATS score" value={resumeScore == null ? '—' : `${resumeScore}`} change={resumeScore == null ? 'NO REPORT' : 'LATEST REVIEW'} caption={resumeScore == null ? 'Upload a resume to get a role-aware review' : 'Latest saved resume analysis'} icon="file" tone="green">
          <button className="text-link" onClick={() => setPage('resume')}>{resumeScore == null ? 'Analyze resume' : 'Open report'} <Icon name="arrow" size={13} /></button>
        </MetricCard>
        <MetricCard label="Interview readiness" value={interviewScore == null ? '—' : `${interviewScore}`} change={interviewScore == null ? 'NO SESSION' : 'LATEST SESSION'} caption={interviewScore == null ? 'Complete a mock interview to get a baseline' : 'Latest completed practice session'} icon="sparkles" tone="violet">
          <button className="text-link" onClick={() => setPage('interview')}>Practice now <Icon name="arrow" size={13} /></button>
        </MetricCard>
      </div>

      <Card className="project-card">
        <CardHeading eyebrow="BUILD PROOF" title="Project activity" action={<Badge tone={recommendedProjects.length ? 'cyan' : 'neutral'}>{recommendedProjects.length ? 'SAVED' : 'NO SAVED PROJECTS'}</Badge>} />
        {recommendedProjects.length ? recommendedProjects.map((project,index)=><div className="project-row" key={project.projectKey}><span className="project-number">0{index+1}</span><div><strong>{project.title}</strong><small>{project.status} · {project.difficulty || 'Portfolio project'}</small></div><Icon name="arrow" size={15} /></div>) : <div className="empty-state-ui"><h3>No project activity yet</h3><p>Save a role-aligned project and its current status will appear here.</p></div>}
        <button className="text-link" onClick={() => setPage('projects')}>Open project recommender <Icon name="arrow" size={13} /></button>
      </Card>

      <Card variant="insight" className="insight-card">
        <div className="insight-mark"><Icon name="sparkles" size={19} /></div><p className="card-eyebrow">AI INSIGHT OF THE DAY <span>· DEMO</span></p>
        <h2>Skills tell a richer story when you connect them to work you enjoy.</h2>
        <p>Try pairing one strength with one kind of problem you want to solve.</p>
        <button className="insight-link" onClick={onAskMentor}>Ask your mentor <Icon name="arrowRight" size={14} /></button>
      </Card>

      <Card className="tasks-card">
        <CardHeading eyebrow="YOUR WEEK" title="Next up" action={<button className="text-link" onClick={() => setPage('roadmap')}>Open plan <Icon name="arrow" size={13} /></button>} />
        <Timeline items={upcomingTasks.length ? upcomingTasks : [{title:'Generate a personalized roadmap',meta:'Growth plan',date:'Next up'}]} />
      </Card>

      <Card className="saved-card" variant="compact">
        <div className="saved-icon"><Icon name="briefcase" size={17} /></div><div><span className="card-eyebrow">SAVED DIRECTIONS</span><strong>{savedCount}</strong><small>{savedCount === 1 ? 'career in your shortlist' : 'careers in your shortlist'}</small></div>
        <button className="card-arrow" aria-label="Open saved careers" onClick={() => setPage('saved')}><Icon name="arrow" size={16} /></button>
      </Card>
    </div>
  </main>;
}
