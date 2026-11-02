import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import App from './App.jsx';
import { AppRoutes } from './App.jsx';
import { MemoryRouter, useNavigate } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext.jsx';

afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.unstubAllEnvs(); window.history.replaceState({}, '', '/match'); });
beforeEach(() => { window.history.replaceState({}, '', '/match'); localStorage.clear(); sessionStorage.clear(); document.documentElement.removeAttribute('data-theme'); });

describe('career guidance flow', () => {
  const coreSkills = ['Complex Problem Solving', 'Critical Thinking', 'Active Listening', 'Writing', 'Programming', 'Time Management']
    .map((name, index) => ({ id: `skill-${index}`, name, category: 'Essential' }));
  const nav = () => within(screen.getByRole('navigation', { name: 'Main navigation' }));

  async function completeQuickProfile() {
    fireEvent.change(screen.getByLabelText(/Where are you in your journey/), { target: { value: 'Student' } });
    fireEvent.click(screen.getByRole('button', { name: /Continue/i }));
    fireEvent.click(screen.getByRole('button', { name: /Investigating/i }));
    fireEvent.click(screen.getByRole('button', { name: /Continue/i }));
    for (const name of coreSkills.map((skill) => skill.name)) {
      fireEvent.click(screen.getByRole('button', { name: `Rate ${name} 3 of 5` }));
    }
  }

  it('uses a short profile and renders results returned by the matching API', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => coreSkills })
      .mockResolvedValueOnce({ ok: true, json: async () => [{ rank: 1, onet_soc_code: '15-1252.00', occupation_title: 'Software Developers', match_score: 83, base_score: 75, riasec_bonus: 8, skills_assessed: 6, skills_total: 8, strong_skills: ['Programming', 'Writing'], description: 'Build software.', job_zone: 4, skill_gaps: [] }] });
    vi.stubGlobal('fetch', fetchMock);
    render(<App />);
    await screen.findByLabelText(/Where are you in your journey/);
    await completeQuickProfile();
    fireEvent.click(screen.getByRole('button', { name: /Show my career ideas/i }));
    expect(await screen.findByText('Software Developers')).toBeTruthy();
    expect(screen.getByText(/Skill similarity/)).toBeTruthy();
    expect(screen.getByText(/75.0%/)).toBeTruthy();
    expect(screen.getByText(/not a hiring probability/i)).toBeTruthy();
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    const submission = JSON.parse(fetchMock.mock.calls[1][1].body);
    expect(Object.keys(submission.studentSkills)).toHaveLength(6);
    expect(submission.studentSkills['Active Listening']).toBe(3);
    expect(submission.assessedOnly).toBe(true);
    expect(submission.studentInterests).toEqual(['I']);
  });

  it('loads the local real skill catalog when the API is unavailable', async () => {
    const fetchMock = vi.fn()
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce({ ok: true, json: async () => coreSkills });
    vi.stubGlobal('fetch', fetchMock);
    render(<App />);
    expect(await screen.findByText('Profile ready')).toBeTruthy();
    expect(screen.getByLabelText(/Where are you in your journey/)).toBeTruthy();
    expect(screen.getByText('First, a little about you.')).toBeTruthy();
  });

  it('opens the Overview section from the workspace navigation', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce({ ok: true, json: async () => coreSkills }));
    render(<App />);
    await screen.findByLabelText(/Where are you in your journey/);
    fireEvent.click(screen.getByRole('button', { name: /Overview/i }));
    expect(screen.getByRole('heading', { name: /Your next move starts here/i })).toBeTruthy();
    expect(screen.getByRole('heading', { name: /Roles to explore/i })).toBeTruthy();
  });

  it('updates and persists roadmap checklist progress', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce({ ok: true, json: async () => coreSkills }));
    render(<App />);
    await screen.findByLabelText(/Where are you in your journey/);
    fireEvent.click(screen.getByRole('button', { name: /My roadmap/i }));
    const firstStep = screen.getAllByRole('checkbox')[0];
    fireEvent.click(firstStep);
    expect(firstStep.checked).toBe(true);
    await waitFor(() => expect(JSON.parse(localStorage.getItem('pathfinder-roadmap'))).toHaveProperty('profile', true));
    expect(screen.getByText(/1 of 3 steps complete/i)).toBeTruthy();
  });

  it('shows and removes a locally saved career', async () => {
    localStorage.setItem('pathfinder-saved', JSON.stringify([{ onet_soc_code: '15-1252.00', occupation_title: 'Software Developers', match_score: 88, description: 'Build software.' }]));
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce({ ok: true, json: async () => coreSkills }));
    render(<App />);
    await screen.findByLabelText(/Where are you in your journey/);
    fireEvent.click(screen.getByRole('button', { name: /Saved careers/i }));
    expect(screen.getByRole('heading', { name: 'Software Developers' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /Remove Software Developers/i }));
    expect(screen.getByText(/Your shortlist starts with a spark/i)).toBeTruthy();
    await waitFor(() => expect(JSON.parse(localStorage.getItem('pathfinder-saved'))).toEqual([]));
  });

  it('applies and persists the selected Settings theme', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce({ ok: true, json: async () => coreSkills }));
    render(<App />);
    await screen.findByLabelText(/Where are you in your journey/);
    fireEvent.click(nav().getByRole('button', { name: /Settings/i }));
    fireEvent.click(screen.getByRole('button', { name: /Light/ }));
    fireEvent.click(screen.getByRole('button', { name: /Dark/ }));
    await waitFor(() => expect(document.documentElement.dataset.theme).toBe('dark'));
    expect(screen.getByRole('button', { name: /Dark/ }).getAttribute('aria-pressed')).toBe('true');
    expect(JSON.parse(localStorage.getItem('pathfinder-theme'))).toBe('dark');
  });

  it('keeps interview drafts when switching sections and clears them on request', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce({ ok: true, json: async () => coreSkills }));
    render(<App />);
    await screen.findByLabelText(/Where are you in your journey/);
    fireEvent.click(nav().getByRole('button', { name: /Interview studio/i }));
    const answer = screen.getByRole('textbox', { name: /Draft your answer/i });
    fireEvent.change(answer, { target: { value: 'I solved a problem by testing two approaches.' } });
    await waitFor(() => expect(JSON.parse(sessionStorage.getItem('pathfinder-interview-drafts'))['0']).toContain('testing two approaches'));
    fireEvent.click(screen.getByRole('button', { name: /Career match/i }));
    fireEvent.click(nav().getByRole('button', { name: /Interview studio/i }));
    expect(screen.getByRole('textbox', { name: /Draft your answer/i }).value).toContain('testing two approaches');
    fireEvent.click(screen.getByRole('button', { name: /Clear drafts/i }));
    expect(screen.getByRole('textbox', { name: /Draft your answer/i }).value).toBe('');
    await waitFor(() => expect(JSON.parse(sessionStorage.getItem('pathfinder-interview-drafts'))).toEqual({}));
  });

  it('moves to the next interview reflection prompt', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce({ ok: true, json: async () => coreSkills }));
    render(<App />);
    await screen.findByLabelText(/Where are you in your journey/);
    fireEvent.click(screen.getByRole('button', { name: /Interview studio/i }));
    expect(screen.getByRole('heading', { name: /Tell me about a project where you solved a difficult problem/i })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /Next prompt/i }));
    expect(screen.getByRole('heading', { name: /How do you learn a new tool or skill/i })).toBeTruthy();
  });

  it('recovers safely from corrupted browser storage', async () => {
    localStorage.setItem('pathfinder-theme', '{broken');
    localStorage.setItem('pathfinder-saved', '{broken');
    localStorage.setItem('pathfinder-roadmap', JSON.stringify(['not', 'a', 'record']));
    sessionStorage.setItem('pathfinder-interview-drafts', '{broken');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce({ ok: true, json: async () => coreSkills }));
    render(<App />);
    await screen.findByLabelText(/Where are you in your journey/);
    fireEvent.click(screen.getByRole('button', { name: /Overview/i }));
    expect(screen.getByRole('heading', { name: /Your next move starts here/i })).toBeTruthy();
    fireEvent.click(nav().getByRole('button', { name: /Interview studio/i }));
    expect(screen.getByRole('textbox', { name: /Draft your answer/i }).value).toBe('');
  });

  async function submitAndReadError(secondResponse) {
    const fetchMock = vi.fn().mockResolvedValueOnce({ ok: true, json: async () => coreSkills });
    if (secondResponse instanceof Error) fetchMock.mockRejectedValueOnce(secondResponse);
    else fetchMock.mockResolvedValueOnce(secondResponse);
    vi.stubGlobal('fetch', fetchMock);
    render(<App />);
    await screen.findByLabelText(/Where are you in your journey/);
    await completeQuickProfile();
    fireEvent.click(screen.getByRole('button', { name: /Show my career ideas/i }));
    return screen.findByRole('status');
  }

  it('explains a network failure and how to recover', async () => {
    const message = await submitAndReadError(new TypeError('Failed to fetch'));
    expect(message.textContent).toMatch(/Can’t reach the matching service.*backend is running/i);
  });

  it('shows the actionable validation detail for HTTP 400', async () => {
    const message = await submitAndReadError({ ok: false, status: 400, json: async () => ({ error: 'Choose one to three work interests.' }) });
    expect(message.textContent).toContain('Your profile needs attention: Choose one to three work interests.');
  });

  it('shows the service-unavailable guidance for HTTP 503', async () => {
    const message = await submitAndReadError({ ok: false, status: 503, json: async () => ({ error: 'Matcher unavailable.' }) });
    expect(message.textContent).toContain('The matching service is unavailable: Matcher unavailable.');
  });

  it('shows a friendly retry message for HTTP 429', async () => {
    const message = await submitAndReadError({ ok: false, status: 429, json: async () => ({ error: 'Wait a moment before matching again.' }) });
    expect(message.textContent).toContain('Wait a moment before matching again.');
  });

  it('distinguishes other server errors and includes the HTTP status', async () => {
    const message = await submitAndReadError({ ok: false, status: 500, json: async () => ({ error: 'Internal server detail.' }) });
    expect(message.textContent).toContain('server error (HTTP 500)');
    expect(message.textContent).toContain('check the backend terminal');
  });

  it('opens a settings deep link directly', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce({ ok: true, json: async () => coreSkills }));
    render(<MemoryRouter initialEntries={['/settings']}><AppRoutes /></MemoryRouter>);
    expect(await screen.findByRole('heading', { name: /Make Pathfinder feel like yours/i })).toBeTruthy();
  });

  it('restores a match result from session storage on its deep link', async () => {
    sessionStorage.setItem('pathfinder-profile-state', JSON.stringify({
      profile: { name: 'Mira', stage: 'Student', studyArea: 'Computer science' },
      ratings: { Programming: 4 }, interests: ['I'], step: 3,
      results: [{ rank: 1, onet_soc_code: '15-1252.00', occupation_title: 'Software Developers', match_score: 83, description: 'Build software.', skill_gaps: [] }],
    }));
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce({ ok: true, json: async () => coreSkills }));
    render(<MemoryRouter initialEntries={['/results/local-123']}><AppRoutes /></MemoryRouter>);
    expect(await screen.findByRole('heading', { name: 'Software Developers' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Career ideas for Mira.' })).toBeTruthy();
  });

  it('keeps the in-progress profile when routes unmount and remount the workspace', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => coreSkills }));
    render(<MemoryRouter initialEntries={['/match']}><AppRoutes /></MemoryRouter>);
    fireEvent.change(await screen.findByLabelText(/Where are you in your journey/i), { target: { value: 'Student' } });
    fireEvent.click(screen.getByRole('button', { name: /Continue/i }));
    expect(screen.getByRole('heading', { name: /What kind of work sounds good/i })).toBeTruthy();
    fireEvent.click(nav().getByRole('button', { name: /Settings/i }));
    fireEvent.click(nav().getByRole('button', { name: /Career match/i }));
    expect(await screen.findByRole('heading', { name: /Build the context behind your next move/i })).toBeTruthy();
    expect(sessionStorage.getItem('pathfinder-profile-state')).toContain('Student');
  });

  it('uses browser history navigation between workspace routes', async () => {
    function BackButton() {
      const navigate = useNavigate();
      return <button type="button" onClick={() => navigate(-1)}>Go back</button>;
    }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce({ ok: true, json: async () => coreSkills }));
    render(<MemoryRouter initialEntries={['/match', '/settings']} initialIndex={1}><BackButton /><AppRoutes /></MemoryRouter>);
    expect(await screen.findByRole('heading', { name: /Make Pathfinder feel like yours/i })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Go back' }));
    expect(await screen.findByLabelText(/Where are you in your journey/i)).toBeTruthy();
  });

  it('shows a not-found page for unknown routes', () => {
    render(<MemoryRouter initialEntries={['/not-a-real-page']}><AppRoutes /></MemoryRouter>);
    expect(screen.getByRole('heading', { name: /We couldn’t find that page/i })).toBeTruthy();
    expect(screen.getByRole('link', { name: /Back to career match/i }).getAttribute('href')).toBe('/match');
  });

  it('keeps account routes disabled and guest matching available by default', async () => {
    vi.stubEnv('VITE_AUTH_ENABLED', 'false');
    render(<MemoryRouter initialEntries={['/login']}><AuthProvider><AppRoutes /></AuthProvider></MemoryRouter>);
    expect(screen.getByRole('heading', { name: /Accounts are turned off/i })).toBeTruthy();
    expect(screen.getByRole('link', { name: /Continue as a guest/i }).getAttribute('href')).toBe('/match');
  });

  it('signs in, keeps the access token in memory, and opens the protected account route', async () => {
    vi.stubEnv('VITE_AUTH_ENABLED', 'true');
    const fetchMock = vi.fn(async (url) => {
      const path = String(url);
      if (path.endsWith('/auth/csrf')) return { ok: true, json: async () => ({ token: 'csrf-test' }) };
      if (path.endsWith('/auth/refresh')) return { ok: false, status: 401, json: async () => ({ error: 'expired' }) };
      if (path.endsWith('/auth/login')) return { ok: true, json: async () => ({ accessToken: 'memory-token', expiresIn: 900, user: { id: 1, email: 'student@example.com', role: 'USER' } }) };
      if (path.endsWith('/me')) return { ok: true, json: async () => ({ id: 1, email: 'student@example.com', role: 'USER' }) };
      if (path.endsWith('/sessions')) return { ok: true, json: async () => [] };
      throw new Error('Unexpected test request: ' + path);
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<MemoryRouter initialEntries={['/login']}><AuthProvider><AppRoutes /></AuthProvider></MemoryRouter>);
    fireEvent.change(await screen.findByLabelText(/Email address/i), { target: { value: 'student@example.com' } });
    fireEvent.change(screen.getByLabelText(/Password/i), { target: { value: 'a-strong-password-123' } });
    fireEvent.click(screen.getByRole('button', { name: /Sign in/i }));
    expect(await screen.findByRole('heading', { name: /Account details/i })).toBeTruthy();
    expect(await screen.findByText('student@example.com')).toBeTruthy();
    const loginCall = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/auth/login'));
    expect(JSON.parse(loginCall[1].body)).toEqual({ email: 'student@example.com', password: 'a-strong-password-123' });
    const meCall = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/me'));
    expect(meCall[1].headers.Authorization).toBe('Bearer memory-token');
  });

  it('signs out an existing admin session before showing the admin login form', async () => {
    vi.stubEnv('VITE_AUTH_ENABLED', 'true');
    const fetchMock = vi.fn(async (url) => {
      const path = String(url);
      if (path.endsWith('/auth/csrf')) return { ok: true, status: 200, json: async () => ({ token: 'csrf-admin' }) };
      if (path.endsWith('/auth/refresh')) return { ok: true, status: 200, json: async () => ({ accessToken: 'admin-token', expiresIn: 900, user: { id: 9, email: 'admin@example.com', role: 'ADMIN' } }) };
      if (path.endsWith('/auth/logout')) return { ok: true, status: 204, json: async () => null };
      throw new Error('Unexpected test request: ' + path);
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<MemoryRouter initialEntries={['/admin/login']}><AuthProvider><AppRoutes /></AuthProvider></MemoryRouter>);
    expect(await screen.findByRole('heading', { name: /Administrator sign in/i })).toBeTruthy();
    expect(screen.getByLabelText(/Email address/i)).toBeTruthy();
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/auth/logout'))).toBe(true);
  });

  it('keeps a newly authenticated admin session and opens the console', async () => {
    vi.stubEnv('VITE_AUTH_ENABLED', 'true');
    const fetchMock = vi.fn(async (url) => {
      const path = String(url);
      if (path.endsWith('/auth/csrf')) return { ok: true, status: 200, json: async () => ({ token: 'csrf-admin' }) };
      if (path.endsWith('/auth/refresh')) return { ok: false, status: 401, json: async () => ({ error: 'expired' }) };
      if (path.endsWith('/auth/login')) return { ok: true, status: 200, json: async () => ({ accessToken: 'admin-token', expiresIn: 900, user: { id: 9, email: 'admin@example.com', role: 'ADMIN' } }) };
      if (path.endsWith('/admin/overview')) return { ok: true, status: 200, json: async () => ({ totalUsers: 0, newUsersWeek: 0, activeUsers: 0, resumeAnalyses: 0, assessments: 0, roadmaps: 0, interviewsCompleted: 0, jobApplications: 0, activity: [], usersByTargetRole: [], usersBySkillCategory: [], recentSignups: [] }) };
      throw new Error('Unexpected test request: ' + path);
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<MemoryRouter initialEntries={['/admin/login']}><AuthProvider><AppRoutes /></AuthProvider></MemoryRouter>);
    fireEvent.change(await screen.findByLabelText(/Email address/i), { target: { value: 'admin@example.com' } });
    fireEvent.change(screen.getByLabelText(/Password/i), { target: { value: 'admin-password-123' } });
    fireEvent.click(screen.getByRole('button', { name: /Sign in/i }));
    expect(await screen.findByRole('heading', { name: 'Platform overview' })).toBeTruthy();
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/auth/logout'))).toBe(false);
  });

  it('redirects an unauthenticated account deep link to sign-in after silent refresh', async () => {
    vi.stubEnv('VITE_AUTH_ENABLED', 'true');
    vi.stubGlobal('fetch', vi.fn(async (url) => String(url).endsWith('/auth/csrf')
      ? { ok: true, json: async () => ({ token: 'csrf-test' }) }
      : { ok: false, status: 401, json: async () => ({ error: 'expired' }) }));
    render(<MemoryRouter initialEntries={['/account']}><AuthProvider><AppRoutes /></AuthProvider></MemoryRouter>);
    expect(await screen.findByRole('heading', { name: /Welcome back/i })).toBeTruthy();
    expect(screen.getByLabelText(/Email address/i)).toBeTruthy();
  });

  it('searches the career library and opens career requirements', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url) => {
      const path = String(url);
      if (path.endsWith('/skills')) return { ok: true, json: async () => coreSkills };
      if (path.includes('/careers?')) return { ok: true, json: async () => ({ total: 1, results: [{ onet_soc_code: '15-1252.00', occupation_title: 'Software Developers', description: 'Build software.' }] }) };
      if (path.endsWith('/careers/15-1252.00')) return { ok: true, json: async () => ({ onet_soc_code: '15-1252.00', occupation_title: 'Software Developers', description: 'Build software.', requirements: [{ element_id: '2.A', element_name: 'Programming', skill_category: 'Essential', importance: 4.2, required_level: 5 }] }) };
      throw new Error('Unexpected request: ' + path);
    }));
    render(<MemoryRouter initialEntries={['/careers']}><AppRoutes /></MemoryRouter>);
    expect(await screen.findByRole('heading', { name: /Explore the work behind the titles/i })).toBeTruthy();
    fireEvent.change(screen.getByRole('textbox', { name: /Search careers/i }), { target: { value: 'software' } });
    fireEvent.change(screen.getByRole('combobox', { name: /Filter careers by interest/i }), { target: { value: 'I' } });
    fireEvent.click(await screen.findByRole('button', { name: /Career details/i }));
    expect(await screen.findByRole('heading', { name: 'Software Developers' })).toBeTruthy();
    expect(await screen.findByText('Programming')).toBeTruthy();
  });

  it('limits side-by-side career comparison to three saved careers', async () => {
    localStorage.setItem('pathfinder-saved', JSON.stringify(['1','2','3','4'].map((index) => ({
      onet_soc_code: `15-125${index}.00`, occupation_title: `Career ${index}`, match_score: 70 + Number(index), description: `Description ${index}`,
    }))));
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce({ ok: true, json: async () => coreSkills }));
    render(<MemoryRouter initialEntries={['/compare']}><AppRoutes /></MemoryRouter>);
    expect(await screen.findByRole('heading', { name: /Compare career paths/i })).toBeTruthy();
    for (const index of ['1','2','3','4']) fireEvent.click(screen.getByLabelText(`Career ${index}`));
    expect(screen.getAllByRole('heading', { name: /Career [123]/ })).toHaveLength(3);
    expect(screen.getByRole('status').textContent).toContain('Compare up to three');
  });

  it('attaches a curated resource link to a career gap roadmap item', async () => {
    sessionStorage.setItem('pathfinder-profile-state', JSON.stringify({
      profile: { name: 'Mira', stage: 'Student', studyArea: '' }, ratings: { 'Time Management': 2 }, interests: ['I'], step: 3,
      results: [{ rank: 1, onet_soc_code: '11-1011.00', occupation_title: 'Chief Executives', match_score: 74,
        skill_gaps: [{ element_name: 'Time Management', gap_size: 2, gap_priority: 6, priority_label: 'Critical' }] }],
    }));
    const fetchMock = vi.fn();
    fetchMock.mockImplementation(async (url) => String(url).endsWith('/resources')
      ? { ok: true, json: async () => [{ skill: 'Time Management', provider: 'OpenLearn', url: 'https://www.open.edu/openlearn/money-business/leadership-management/managing-and-managing-people/content-section-0', level: 'Introductory', estimated_hours: 8, cost: 'Free' }] }
      : { ok: true, json: async () => coreSkills });
    vi.stubGlobal('fetch', fetchMock);
    render(<MemoryRouter initialEntries={['/roadmap']}><AppRoutes /></MemoryRouter>);
    const resource = await screen.findByRole('link', { name: /OpenLearn.*Introductory.*8h.*Free/i });
    expect(resource.getAttribute('target')).toBe('_blank');
    expect(resource.getAttribute('rel')).toContain('noopener');
  });

  it('runs a what-if skill change and shows the returned rank comparison', async () => {
    sessionStorage.setItem('pathfinder-profile-state', JSON.stringify({
      profile: { name: 'Mira', stage: 'Student', studyArea: '' },
      ratings: Object.fromEntries(coreSkills.map((skill) => [skill.name, 3])), interests: ['I'], step: 3,
      results: [{ rank: 1, onet_soc_code: '15-1252.00', occupation_title: 'Software Developers', match_score: 78, skill_gaps: [] }],
    }));
    const fetchMock = vi.fn(async (url) => String(url).endsWith('/what-if')
      ? { ok: true, json: async () => ({
        baseline: [{ rank: 1, occupation_title: 'Software Developers', onet_soc_code: '15-1252.00', match_score: 78 }],
        scenario: [{ rank: 1, occupation_title: 'Software Developers', onet_soc_code: '15-1252.00', match_score: 82 }],
        comparison: [{ onet_soc_code: '15-1252.00', rank_change: 0 }],
      }) }
      : { ok: true, json: async () => coreSkills });
    vi.stubGlobal('fetch', fetchMock);
    render(<MemoryRouter initialEntries={['/match']}><AppRoutes /></MemoryRouter>);
    fireEvent.change(await screen.findByLabelText('What if rating for Programming'), { target: { value: '5' } });
    fireEvent.click(screen.getByRole('button', { name: /Compare scenario/i }));
    expect(await screen.findByText(/With your changes/i)).toBeTruthy();
    expect(screen.getByText(/Software Developers · 82%/)).toBeTruthy();
    const call = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/what-if'));
    expect(JSON.parse(call[1].body).changes).toEqual({ Programming: 5 });
  });
});
