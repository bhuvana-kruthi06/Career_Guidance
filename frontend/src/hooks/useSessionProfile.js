import { useEffect, useState } from 'react';
import { readStorage, writeStorage } from '../lib/storage.js';

const interestsAreValid = (value) => Array.isArray(value) && value.every((item) => ['R', 'I', 'A', 'S', 'E', 'C'].includes(item));
const profileIsValid = (value) => value && typeof value.name === 'string' && typeof value.stage === 'string' && typeof value.studyArea === 'string';
const ratingsAreValid = (value) => value && typeof value === 'object' && !Array.isArray(value)
  && Object.values(value).every((rating) => rating === '' || Number.isInteger(rating) && rating >= 1 && rating <= 5);
const resultsAreValid = (value) => value === null || Array.isArray(value) && value.every((career) => career
  && typeof career === 'object' && typeof career.occupation_title === 'string'
  && typeof career.onet_soc_code === 'string' && Number.isFinite(Number(career.match_score))
  && Array.isArray(career.skill_gaps));
const profileStateIsValid = (value) => value && profileIsValid(value.profile) && ratingsAreValid(value.ratings)
  && interestsAreValid(value.interests) && resultsAreValid(value.results)
  && Number.isInteger(value.step) && value.step >= 1 && value.step <= 3;

const initialState = { profile: { name: '', stage: '', studyArea: '' }, ratings: {}, interests: [], results: null, step: 1 };

export function useSessionProfile() {
  const [state, setState] = useState(() => readStorage('pathfinder-profile-state', initialState, profileStateIsValid, 'sessionStorage'));
  useEffect(() => { writeStorage('pathfinder-profile-state', state, profileStateIsValid, 'sessionStorage'); }, [state]);
  const update = (key) => (value) => setState((current) => ({ ...current, [key]: typeof value === 'function' ? value(current[key]) : value }));
  return {
    ...state,
    setProfile: update('profile'),
    setRatings: update('ratings'),
    setInterests: update('interests'),
    setResults: update('results'),
    setStep: update('step'),
  };
}
