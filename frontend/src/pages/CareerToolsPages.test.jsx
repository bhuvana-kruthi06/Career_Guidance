import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { InterviewCoachPage } from './CareerToolsPages.jsx';

const { accountMock } = vi.hoisted(() => ({ accountMock: vi.fn() }));
vi.mock('../auth/AuthContext.jsx', () => ({ useAuth: () => ({ enabled: true, accessToken: 'test-token', loading: false }) }));
vi.mock('../api/auth.js', () => ({ authApi: { account: accountMock } }));

class MockSpeechRecognition {
  static latest;
  constructor() { MockSpeechRecognition.latest = this; }
  start() {}
  stop() { this.onend?.(); }
}

describe('interview answer submission', () => {
  beforeEach(() => {
    accountMock.mockReset();
    accountMock.mockImplementation((path, _token, options = {}) => {
      if (path === '/interviews' && options.method === 'POST') return Promise.resolve({
        sessionId: 'session-1', role: 'Analyst', type: 'Behavioral', difficulty: 'Beginner', status: 'In Progress',
        questions: [{ id: 'question-1', prompt: 'Tell me about a project.' }], answers: [],
      });
      if (path.endsWith('/answers')) return Promise.resolve({ feedback: { score: 80 }, currentQuestion: 1 });
      return Promise.resolve([]);
    });
    Object.defineProperty(globalThis, 'webkitSpeechRecognition', { configurable: true, value: MockSpeechRecognition });
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia: vi.fn().mockResolvedValue({ getTracks: () => [{ stop: vi.fn() }] }) } });
  });
  afterEach(() => { cleanup(); delete globalThis.webkitSpeechRecognition; delete navigator.mediaDevices; });

  async function openSession() {
    render(<InterviewCoachPage />);
    fireEvent.click(screen.getByRole('button', { name: 'Start interview' }));
    await screen.findByRole('heading', { name: 'Tell me about a project.' });
  }

  it('enables only for non-whitespace typed answers and updates when edited or cleared', async () => {
    await openSession();
    const submit = screen.getByRole('button', { name: 'Submit answer' });
    const input = screen.getByPlaceholderText('Tap Answer by voice or type your answer…');
    expect(submit.disabled).toBe(true);
    fireEvent.change(input, { target: { value: '   ' } });
    expect(submit.disabled).toBe(true);
    fireEvent.change(input, { target: { value: 'I planned and delivered a project.' } });
    expect(submit.disabled).toBe(false);
    fireEvent.change(input, { target: { value: '' } });
    expect(submit.disabled).toBe(true);
  });

  it('uses the recognized voice transcript as the same validated and submitted answer', async () => {
    await openSession();
    fireEvent.click(screen.getByRole('button', { name: /Answer by voice/ }));
    await waitFor(() => expect(MockSpeechRecognition.latest).toBeTruthy());
    MockSpeechRecognition.latest.onresult({ results: [{ 0: { transcript: 'I led a project team.' } }] });
    const submit = screen.getByRole('button', { name: 'Submit answer' });
    await waitFor(() => expect(submit.disabled).toBe(false));
    fireEvent.click(submit);
    await waitFor(() => expect(accountMock.mock.calls.filter(([path]) => path.endsWith('/answers'))).toHaveLength(1));
    expect(accountMock.mock.calls.find(([path]) => path.endsWith('/answers'))[2].body.answer).toBe('I led a project team.');
  });
});
