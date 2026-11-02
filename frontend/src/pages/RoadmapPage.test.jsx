import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

const { account } = vi.hoisted(() => ({ account: vi.fn() }));

vi.mock('../auth/AuthContext.jsx', () => ({
  useAuth: () => ({ enabled: true, accessToken: 'test-user-token', loading: false }),
}));
vi.mock('../api/auth.js', () => ({ authApi: { account } }));

import RoadmapPage from './RoadmapPage.jsx';

afterEach(() => { cleanup(); vi.clearAllMocks(); });
beforeEach(() => { account.mockReset(); });

function renderRoadmap() {
  render(<MemoryRouter><RoadmapPage /></MemoryRouter>);
}

describe('role-based roadmap setup', () => {
  it('asks the user to choose a role when no target role is saved', async () => {
    account.mockResolvedValueOnce({ generated: false, hasTargetRole: false, phases: [], completionPercent: 0 });
    renderRoadmap();

    expect(await screen.findByRole('heading', { name: /choose a target role first/i })).toBeTruthy();
    expect(screen.getByRole('link', { name: /choose a target role/i }).getAttribute('href')).toBe('/careers');
    expect(screen.queryByRole('button', { name: /generate my roadmap/i })).toBeNull();
  });

  it('offers generation and saves the roadmap after a target role is selected', async () => {
    account.mockResolvedValueOnce({ generated: false, hasTargetRole: true, roleTitle: 'Software Developer', phases: [], completionPercent: 0 });
    account.mockResolvedValueOnce({
      generated: true, hasTargetRole: true, roleTitle: 'Software Developer', weeklyHours: 6,
      careerGoal: 'Build portfolio evidence', completionPercent: 0, completedItems: 0, totalItems: 1,
      phases: [{ id: 'foundation', title: 'Foundation', description: 'Start here.', durationWeeks: 1,
        complete: false, state: 'active', weeks: [], milestone: { id: 'foundation-milestone', title: 'Baseline ready', complete: false } }],
    });
    renderRoadmap();

    fireEvent.click(await screen.findByRole('button', { name: /generate my roadmap/i }));

    await screen.findByText(/your roadmap is ready and saved to your account/i);
    await waitFor(() => expect(account).toHaveBeenLastCalledWith('/roadmap', 'test-user-token', { method: 'POST', body: {} }));
    expect(screen.getByRole('heading', { name: 'Software Developer' })).toBeTruthy();
  });
});
