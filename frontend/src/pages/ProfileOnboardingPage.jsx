import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import { authApi } from '../api/auth.js';
import { readStorage, writeStorage } from '../lib/storage.js';
import { useSessionProfile } from '../hooks/useSessionProfile.js';
import { Button, Card, CardHeading, Field, SelectInput, TextInput, Badge } from '../components/ui/Primitives.jsx';

const emptyProfile = { name: '', email: '', stage: '', education: '', studyArea: '', college: '', graduationYear: '',
  skills: {}, interests: [], preferredRoles: [], projects: [], internships: [], experience: '', certifications: [],
  targetCompanies: [], preferredLocations: [], expectedSalary: '', weeklyLearningHours: '', shortTermGoal: '', longTermGoal: '' };
const options = {
  skills: ['Communication', 'Critical Thinking', 'Active Listening', 'Writing', 'Programming', 'Data Analysis', 'Design', 'Project Management', 'Time Management', 'Problem Solving', 'Research', 'Leadership'],
  interests: [{code:'R',label:'Building things'},{code:'I',label:'Research and analysis'},{code:'A',label:'Creative work'},{code:'S',label:'Helping people'},{code:'E',label:'Leading teams'},{code:'C',label:'Planning and organizing'}],
  roles: ['Software Developer', 'Data Analyst', 'Product Designer', 'UX Researcher', 'Project Manager', 'Cybersecurity Analyst', 'Business Analyst', 'Healthcare Professional'],
  locations: ['Remote', 'Hybrid', 'On-site', 'United States', 'India', 'Canada', 'United Kingdom', 'Australia'],
};
const completionKeys = ['name','education','studyArea','college','graduationYear','skills','interests','preferredRoles','projects','experience','internships','certifications','targetCompanies','preferredLocations','weeklyLearningHours','shortTermGoal','longTermGoal'];
export function profileCompleteness(profile) {
  return Math.round(completionKeys.filter((key) => Array.isArray(profile?.[key]) ? profile[key].length > 0 : key === 'skills' ? Object.keys(profile[key] || {}).length > 0 : String(profile?.[key] || '').trim().length > 0).length * 100 / completionKeys.length);
}
const splitTags = (text) => text.split(',').map((item) => item.trim()).filter(Boolean).slice(0, 30);

export default function ProfileOnboardingPage() {
  const { enabled, user, accessToken } = useAuth();
  const navigate = useNavigate();
  const { profile: sessionProfile, ratings, interests, setProfile, setRatings, setInterests } = useSessionProfile();
  const [profile, setFullProfile] = useState(() => {
    const stored = readStorage('pathfinder-full-profile', {});
    return { ...emptyProfile, ...stored, ...sessionProfile,
      skills: Object.keys(stored.skills || {}).length ? stored.skills : ratings,
      interests: Array.isArray(stored.interests) && stored.interests.length ? stored.interests : interests };
  });
  const [step, setStep] = useState(0);
  const [skillName, setSkillName] = useState('');
  const [loading, setLoading] = useState(Boolean(enabled && accessToken));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const pct = useMemo(() => profileCompleteness(profile), [profile]);

  useEffect(() => {
    let active = true;
    if (!enabled || !accessToken) { setLoading(false); return undefined; }
    authApi.account('/profile', accessToken).then((stored) => {
      if (!active) return;
      if (!stored || !Object.keys(stored).some((key)=>key!=='targetRole'&&key!=='completeness')) {
        setFullProfile({ ...emptyProfile, name:user?.name||'', email:user?.email||'' });
        setProfile({name:user?.name||'',stage:'',studyArea:''}); setRatings({}); setInterests([]);
        return;
      }
      const merged = { ...emptyProfile, ...stored, name: stored.name || sessionProfile.name || user?.name || '',
        email: stored.email || user?.email || '', skills: stored.skills || ratings, interests: stored.interests || [] };
      setFullProfile(merged); writeStorage('pathfinder-full-profile', merged);
    }).catch((requestError) => { if (active) setError(requestError.message || 'Could not load your saved profile.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [enabled, accessToken, user?.email, user?.name]);

  function update(field, value) { setFullProfile((current) => ({ ...current, [field]: value })); setSuccess(''); }
  function toggleArray(field, value) { const current = profile[field] || []; update(field, current.includes(value) ? current.filter((item) => item !== value) : [...current, value]); }
  function addSkill() {
    const name = skillName.trim();
    if (name && !profile.skills[name]) update('skills', { ...profile.skills, [name]: 3 });
    setSkillName('');
  }
  async function save(event) {
    event?.preventDefault(); setError(''); setSuccess('');
    if (!profile.name.trim() || !profile.education || !profile.studyArea.trim()) { setError('Add your name, education level, and field of study to continue.'); setStep(0); return; }
    if (Object.keys(profile.skills || {}).length < 3) { setError('Add and rate at least three skills to get useful career recommendations.'); setStep(1); return; }
    setSaving(true);
    try {
      const value = { ...profile, name: profile.name.trim(), email: profile.email || user?.email || '', completeness: pct };
      writeStorage('pathfinder-full-profile', value);
      setProfile({ ...sessionProfile, name: value.name, stage: value.stage || value.education, studyArea: value.studyArea });
      setRatings(value.skills);
      setInterests(value.interests);
      if (enabled && accessToken) await authApi.account('/profile', accessToken, { method: 'PUT', body: { profile: value } });
      setSuccess('Your profile is saved. You can return and edit it at any time.');
      window.setTimeout(() => navigate('/assessment'), 400);
    } catch (requestError) { setError(requestError.message || 'Could not save your profile. Please retry.'); }
    finally { setSaving(false); }
  }

  const tags = (field, values) => <div className="profile-choice-grid">{values.map((value) => <button key={value} type="button" className={`profile-choice ${(profile[field] || []).includes(value) ? 'selected' : ''}`} aria-pressed={(profile[field] || []).includes(value)} onClick={() => toggleArray(field, value)}>{value}</button>)}</div>;
  const skillTags = <div className="profile-choice-grid">{options.skills.map((value) => <button key={value} type="button" className={`profile-choice ${profile.skills?.[value] ? 'selected' : ''}`} aria-pressed={Boolean(profile.skills?.[value])} onClick={() => { const next={...profile.skills}; if(next[value]) delete next[value]; else next[value]=3; update('skills',next); }}>{value}</button>)}</div>;
  const interestTags = <div className="profile-choice-grid">{options.interests.map(({code,label})=><button key={code} type="button" className={`profile-choice ${(profile.interests||[]).includes(code)?'selected':''}`} aria-pressed={(profile.interests||[]).includes(code)} onClick={()=>toggleArray('interests',code)}>{label}</button>)}</div>;
  const tagInput = (field, label, placeholder) => <TextInput label={label} hint="Separate entries with commas" value={(profile[field] || []).join(', ')} onChange={(event) => update(field, splitTags(event.target.value))} placeholder={placeholder} />;

  if (loading) return <main className="cockpit-form-page"><p role="status">Loading your saved profile…</p></main>;
  return <main className="cockpit-form-page profile-wizard-page">
    <header className="wizard-topline"><div><p className="eyebrow">YOUR CAREER PROFILE</p><h1>Build the context behind your next move.</h1><p>Save the details that help make your career guidance personal.</p></div><div className="wizard-completeness"><span>PROFILE COMPLETENESS</span><strong>{pct}%</strong><div className="mini-progress"><span style={{ width: `${pct}%` }} /></div></div></header>
    {error && <div className="form-alert error" role="alert">{error}</div>}{success && <div className="form-alert success" role="status">{success}</div>}
    <div className="profile-stepper" aria-label="Profile steps">{['About you','Skills & direction','Experience & goals'].map((label,index)=><button key={label} type="button" className={step===index?'current':step>index?'complete':''} onClick={()=>setStep(index)}><span>0{index+1}</span>{label}</button>)}</div>
    <Card className="profile-form-card"><form onSubmit={save}>
      {step===0 && <><CardHeading eyebrow="STEP 01 / 03" title="Start with the basics" action={<Badge tone="violet">ABOUT YOU</Badge>} /><div className="profile-form-grid">
        <TextInput label="Full name" autoComplete="name" required maxLength={80} value={profile.name} onChange={(e)=>update('name',e.target.value)} placeholder="Your name" />
        <TextInput label="Email address" hint={enabled ? 'From your account' : 'Optional for guest profiles'} type="email" autoComplete="email" maxLength={254} readOnly={Boolean(user?.email)} value={profile.email || user?.email || ''} onChange={(e)=>update('email',e.target.value)} placeholder="you@example.com" />
        <SelectInput label="Education level" required value={profile.education} onChange={(e)=>update('education',e.target.value)}><option value="">Select education</option>{['High school','Diploma','Bachelor’s degree','Master’s degree','Doctorate','Bootcamp or certificate','Other'].map((x)=><option key={x}>{x}</option>)}</SelectInput>
        <TextInput label="Field of study" required maxLength={120} value={profile.studyArea} onChange={(e)=>update('studyArea',e.target.value)} placeholder="e.g. Computer science" />
        <TextInput label="College or institution" maxLength={140} value={profile.college} onChange={(e)=>update('college',e.target.value)} placeholder="School or institution" />
        <TextInput label="Graduation year" type="number" min="1950" max="2100" value={profile.graduationYear} onChange={(e)=>update('graduationYear',e.target.value)} placeholder="2027" />
      </div></>}
      {step===1 && <><CardHeading eyebrow="STEP 02 / 03" title="Skills and the work you enjoy" action={<Badge tone="cyan">YOUR SIGNALS</Badge>} /><h3 className="profile-subheading">Current skills and confidence</h3><p className="field-hint">Choose common skills or add your own. Rate each from 1 (new) to 5 (advanced).</p>{skillTags}<div className="skill-add-row"><TextInput value={skillName} onChange={(e)=>setSkillName(e.target.value)} placeholder="Add another skill" aria-label="Add a skill" maxLength={80}/><Button type="button" variant="secondary" onClick={addSkill} disabled={!skillName.trim()}>Add skill</Button></div>
        <div className="profile-skill-ratings">{Object.entries(profile.skills || {}).map(([name,level])=><div className="profile-skill-rating" key={name}><span>{name}</span><div role="group" aria-label={`Rate ${name}`}>{[1,2,3,4,5].map((n)=><button type="button" key={n} aria-label={`${name} ${n} of 5`} aria-pressed={Number(level)===n} className={Number(level)===n?'chosen':''} onClick={()=>update('skills',{...profile.skills,[name]:n})}>{n}</button>)}</div><button type="button" className="tag-remove" aria-label={`Remove ${name}`} onClick={()=>{const next={...profile.skills};delete next[name];update('skills',next);}}>×</button></div>)}</div>
        <h3 className="profile-subheading">Interests</h3>{interestTags}<h3 className="profile-subheading">Roles you want to explore</h3>{tags('preferredRoles',options.roles)}<TextInput label="Other preferred roles" hint="Comma-separated" value={(profile.customRoles||[]).join(', ')} onChange={(e)=>update('customRoles',splitTags(e.target.value))} placeholder="e.g. Environmental engineer" />
      </>}
      {step===2 && <><CardHeading eyebrow="STEP 03 / 03" title="Your experience and goals" action={<Badge tone="green">GROWTH CONTEXT</Badge>} /><div className="profile-form-grid">
        <Field label="Projects" hint="List names or short descriptions"><textarea value={(profile.projects||[]).join('\n')} onChange={(e)=>update('projects',e.target.value.split('\n').map(x=>x.trim()).filter(Boolean).slice(0,20))} rows={3} placeholder="One project per line" /></Field>
        <Field label="Internships"><textarea value={(profile.internships||[]).join('\n')} onChange={(e)=>update('internships',e.target.value.split('\n').map(x=>x.trim()).filter(Boolean).slice(0,20))} rows={3} placeholder="One internship per line" /></Field>
        <Field label="Work experience" hint="Role, organization, and what you did"><textarea value={profile.experience} onChange={(e)=>update('experience',e.target.value)} rows={3} maxLength={2000} placeholder="Share relevant experience" /></Field>
        <Field label="Certifications"><textarea value={(profile.certifications||[]).join('\n')} onChange={(e)=>update('certifications',e.target.value.split('\n').map(x=>x.trim()).filter(Boolean).slice(0,30))} rows={3} placeholder="One certification per line" /></Field>
        {tagInput('targetCompanies','Target companies','e.g. Microsoft, local startups')}{tagInput('preferredLocations','Preferred locations','e.g. Remote, Bengaluru')}
        <Field label="Expected salary" hint="Optional; use your preferred currency"><TextInput maxLength={80} value={profile.expectedSalary} onChange={(e)=>update('expectedSalary',e.target.value)} placeholder="e.g. 8–10 LPA or negotiable" /></Field>
        <Field label="Weekly learning time"><SelectInput value={profile.weeklyLearningHours} onChange={(e)=>update('weeklyLearningHours',e.target.value)}><option value="">Choose hours per week</option>{['1–3','4–6','7–10','11–15','16+'].map(x=><option key={x}>{x}</option>)}</SelectInput></Field>
        <Field label="Short-term career goal" hint="What would you like to achieve in the next year?"><textarea maxLength={1000} rows={3} value={profile.shortTermGoal} onChange={(e)=>update('shortTermGoal',e.target.value)} placeholder="A role, skill, project, or milestone" /></Field>
        <Field label="Long-term career goal" hint="Where would you like your path to lead?"><textarea maxLength={1000} rows={3} value={profile.longTermGoal} onChange={(e)=>update('longTermGoal',e.target.value)} placeholder="A direction, not a fixed plan" /></Field>
      </div></>}
      <div className="profile-wizard-actions">{step>0&&<Button type="button" variant="secondary" onClick={()=>setStep(step-1)}>Back</Button>}{step<2?<Button type="button" icon="arrowRight" onClick={()=>{setError('');setStep(step+1);}}>Continue</Button>:<Button type="submit" icon="arrowRight" disabled={saving}>{saving?'Saving…':'Save profile & start assessment'}</Button>}</div>
    </form></Card>
    {!enabled && <p className="guest-storage-note">Guest profile is saved on this device. Sign in to securely save it to your account.</p>}
  </main>;
}
