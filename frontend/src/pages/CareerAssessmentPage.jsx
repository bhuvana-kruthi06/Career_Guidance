import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { request, matchErrorMessage } from '../api/client.js';
import { authApi } from '../api/auth.js';
import { useAuth } from '../auth/AuthContext.jsx';
import { useSessionProfile } from '../hooks/useSessionProfile.js';
import { readStorage, writeStorage } from '../lib/storage.js';
import { Button, Card, CardHeading, Badge } from '../components/ui/Primitives.jsx';
import { ProgressRing } from '../components/ui/Charts.jsx';

const choiceSets = [
  { key: 'workStyle', title: 'What kind of work energizes you?', choices: ['Solving complex problems','Creating useful things','Supporting people','Organizing projects','Exploring data and ideas','Building or repairing'] },
  { key: 'environment', title: 'Where do you do your best work?', choices: ['Focused independent work','Collaborative team settings','Fast-moving environments','Clear routines and structure','Flexible and changing work'] },
  { key: 'technicalComfort', title: 'How comfortable are you learning technical tools?', choices: ['I prefer minimal technical tools','I can learn tools with guidance','I enjoy using technical tools','I want a highly technical role'] },
  { key: 'careerGoal', title: 'What matters most in your next step?', choices: ['Explore different directions','Build specialist expertise','Make a positive impact','Grow into leadership','Find stable, practical work','Create or innovate'] },
];
const emptyAssessment = { workStyle: [], environment: [], technicalComfort: '', careerGoal: [], strengths: '' };

export default function CareerAssessmentPage() {
  const { enabled, accessToken } = useAuth();
  const navigate = useNavigate();
  const { profile: sessionProfile, ratings: sessionRatings, interests: sessionInterests, setResults } = useSessionProfile();
  const [profile] = useState(() => readStorage('pathfinder-full-profile', { ...sessionProfile, skills: sessionRatings, interests: sessionInterests }));
  const [assessment, setAssessment] = useState(() => ({ ...emptyAssessment, ...readStorage('pathfinder-assessment', {}) }));
  const [step, setStep] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [roles, setRoles] = useState(() => readStorage('pathfinder-recommendations', []));
  const [success, setSuccess] = useState('');
  const [targetRole, setTargetRole] = useState(() => readStorage('pathfinder-full-profile', {}).targetRole || null);
  const [savingRoleCode, setSavingRoleCode] = useState('');
  const ratedSkills = useMemo(() => Object.fromEntries(Object.entries(profile.skills || sessionRatings || {}).filter(([,level]) => Number(level) >= 1 && Number(level) <= 5)), [profile.skills, sessionRatings]);
  const interests = Array.isArray(profile.interests) && profile.interests.length ? profile.interests : sessionInterests;
  const toggle = (key, value) => setAssessment((current) => {
    if (key === 'technicalComfort' || key === 'strengths') return { ...current, [key]: value };
    const values = current[key] || [];
    return { ...current, [key]: values.includes(value) ? values.filter((item) => item !== value) : [...values, value] };
  });

  async function finish() {
    setError(''); setSuccess('');
    if (!assessment.workStyle?.length || !assessment.environment?.length || !assessment.technicalComfort || !assessment.careerGoal?.length) {
      setError('Answer each assessment step before calculating your career matches.'); return;
    }
    if (Object.keys(ratedSkills).length < 3) { setError('Your profile needs at least three rated skills before we can calculate role matches. Complete the skills step in your profile first.'); return; }
    if (!interests?.length) { setError('Select at least one work interest in your profile before completing this assessment.'); return; }
    setLoading(true);
    try {
      const matchRequest = { studentSkills: ratedSkills, studentInterests: interests.slice(0,3), topN: 10, assessedOnly: true,
        assessment: { ...assessment, preferredRoles: profile.preferredRoles || [] } };
      const recommendations = await request('/recommendations', { method: 'POST', body: JSON.stringify(matchRequest) });
      const saved = { ...assessment, completedAt: new Date().toISOString(), interests, preferredRoles: profile.preferredRoles || [], skillRatings: ratedSkills };
      writeStorage('pathfinder-assessment', saved); writeStorage('pathfinder-recommendations', recommendations);
      writeStorage('pathfinder-full-profile', { ...profile, skills: ratedSkills, interests });
      setResults(recommendations); setRoles(recommendations);
      if (enabled && accessToken) await authApi.account('/assessments', accessToken, { method: 'POST', body: { assessment: saved, recommendations } });
      setSuccess(`Assessment complete. ${recommendations.length} role ${recommendations.length === 1 ? 'match is' : 'matches are'} ready to explore.`);
      setStep(choiceSets.length);
    } catch (requestError) { setError(matchErrorMessage(requestError) || 'Your assessment could not be completed. Try again.'); }
    finally { setLoading(false); }
  }

  return <main className="cockpit-form-page assessment-page">
    <header className="wizard-topline"><div><p className="eyebrow">CAREER ASSESSMENT</p><h1>Find roles that fit your signals.</h1><p>We combine your interests and skill ratings with your work preferences to rank real O*NET occupations.</p></div><ProgressRing value={step===choiceSets.length?100:Math.round(step/choiceSets.length*100)} label="complete" size={94} stroke={8} color="var(--accent-violet)" /></header>
    <div className="assessment-stepper" aria-label="Assessment progress">{choiceSets.map((question,index)=><span key={question.key} className={step===index?'current':step>index?'complete':''}>{String(index+1).padStart(2,'0')}</span>)}</div>
    {error && <div className="form-alert error" role="alert">{error}<button type="button" onClick={()=>navigate('/profile')}>Review my profile →</button></div>}
    {success && <div className="form-alert success" role="status">{success}</div>}
    {step < choiceSets.length ? <Card className="assessment-question-card"><CardHeading eyebrow={`STEP 0${step+1} / 04`} title={choiceSets[step].title} action={<Badge tone="cyan">ABOUT YOUR FIT</Badge>} />
      {step===2 ? <div className="assessment-options">{choiceSets[step].choices.map((choice)=><button type="button" key={choice} className={assessment.technicalComfort===choice?'selected':''} aria-pressed={assessment.technicalComfort===choice} onClick={()=>toggle('technicalComfort',choice)}>{choice}</button>)}</div>
        : <div className="assessment-options">{choiceSets[step].choices.map((choice)=><button type="button" key={choice} className={(assessment[choiceSets[step].key]||[]).includes(choice)?'selected':''} aria-pressed={(assessment[choiceSets[step].key]||[]).includes(choice)} onClick={()=>toggle(choiceSets[step].key,choice)}>{choice}</button>)}</div>}
      <p className="assessment-hint">{step===0||step===1||step===3?'Choose all that feel relevant.':'Pick the closest match. There is no wrong answer.'}</p>
      <div className="profile-wizard-actions">{step>0&&<Button type="button" variant="secondary" disabled={loading} onClick={()=>setStep(step-1)}>Back</Button>}<Button type="button" icon="arrowRight" disabled={loading} onClick={()=>step===choiceSets.length-1?finish():setStep(step+1)}>{loading?'Calculating matches…':step===choiceSets.length-1?'Calculate career matches':'Continue'}</Button></div>
    </Card> : <Card className="assessment-question-card"><CardHeading eyebrow="YOUR RESULTS" title="Career directions to explore" action={<Badge tone="green">O*NET MATCH</Badge>} />
      {!roles.length ? <p>No matches were returned. Adjust your profile skills or interests and run the assessment again.</p> : <div className="assessment-role-list">{roles.slice(0,10).map((role,index)=><article className="assessment-role-card" key={role.onet_soc_code}><div className="assessment-role-top"><span className="role-rank">0{index+1}</span><div><h3>{role.occupation_title}</h3><small>{role.onet_soc_code} · {role.data_basis}</small></div><strong>{Math.round(role.match_score)}<small>%</small></strong></div><p>{role.why_this_fits}</p><div className="profile-choice-grid">{(role.required_skills||[]).slice(0,5).map((skill)=><span className="ui-tag" key={skill.name}>{skill.name}</span>)}</div>{role.missing_skills?.length>0&&<p className="assessment-gaps"><strong>Growth areas:</strong> {role.missing_skills.slice(0,4).map((item)=>item.element_name).join(', ')}</p>}<details><summary>Career path and project ideas</summary><p>{(role.career_progression_path||[]).join(' → ')}</p><ul>{(role.recommended_projects||[]).map((project)=><li key={project.title}>{project.title} · {project.skill}</li>)}</ul>{role.learning_resources?.map((resource)=><p key={resource.skill}><a href={resource.url} target="_blank" rel="noreferrer">{resource.skill}: {resource.provider}</a></p>)}</details><Button type="button" variant={targetRole?.code===role.onet_soc_code?'primary':'secondary'} size="sm" disabled={savingRoleCode===role.onet_soc_code} onClick={async()=>{
        setSavingRoleCode(role.onet_soc_code); setError('');
        try { if(enabled&&accessToken) await authApi.account('/target-role',accessToken,{method:'PUT',body:{onetSocCode:role.onet_soc_code}}); const selected={code:role.onet_soc_code,title:role.occupation_title}; const current=readStorage('pathfinder-full-profile',{}); writeStorage('pathfinder-full-profile',{...current,targetRole:selected}); setTargetRole(selected); setSuccess(`${role.occupation_title} is now your target role.`); }
        catch(e){setError(e.message||'Could not save target role.');}
        finally {setSavingRoleCode('');}
      }}>{savingRoleCode===role.onet_soc_code?'Saving…':targetRole?.code===role.onet_soc_code?'✓ Target role selected':'Select as target role'}</Button></article>)}</div>}
      <div className="profile-wizard-actions"><Button variant="secondary" onClick={()=>setStep(0)}>Retake assessment</Button><Button icon="arrowRight" onClick={()=>navigate('/careers')}>Explore all roles</Button></div>
    </Card>}
    {enabled&&!accessToken&&<p className="guest-storage-note">Assessment results are saved on this device. Sign in to keep them with your account.</p>}
  </main>;
}
