import { expect, test } from '@playwright/test';

test('profile, match, account login, save a career, and refresh', async ({ page }) => {
  const suffix = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  const email = `pathfinder-e2e-${suffix}@example.test`;

  await page.goto('/match');
  await expect(page.getByRole('heading', { name: 'First, a little about you.' })).toBeVisible();
  await page.getByLabel(/What should we call you/).fill('E2E Student');
  await page.getByLabel(/Where are you in your journey/).selectOption('Student');
  await page.getByLabel(/What are you studying or working in/).fill('Computer science');
  await page.getByRole('button', { name: /Continue/ }).click();

  await expect(page.getByRole('heading', { name: 'What kind of work sounds good?' })).toBeVisible();
  await page.getByRole('button', { name: /Investigating/ }).click();
  await page.getByRole('button', { name: /Continue/ }).click();

  await expect(page.getByRole('heading', { name: 'What are your strengths?' })).toBeVisible();
  const skillGroups = page.getByRole('group', { name: /^Rate / });
  await expect(skillGroups).toHaveCount(6);
  for (let index = 0; index < await skillGroups.count(); index += 1) {
    const group = skillGroups.nth(index);
    const groupName = await group.getAttribute('aria-label');
    const skillName = groupName.replace(/^Rate /, '');
    await group.getByRole('button', { name: `Rate ${skillName} 4 of 5` }).click();
  }
  await page.getByRole('button', { name: /Show my career ideas/ }).click();
  await expect(page.getByRole('heading', { name: /Career ideas/ })).toBeVisible({ timeout: 30_000 });
  await expect(page.locator('.career-card').first()).toBeVisible({ timeout: 30_000 });
  const matchPath = new URL(page.url()).pathname;

  await page.goto('/register');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel(/Password/).fill('PathfinderE2E!2026');
  await page.getByRole('button', { name: /Create account/ }).click();
  await expect(page).toHaveURL(/\/account$/);
  await page.getByRole('button', { name: 'Sign out' }).click();
  await page.goto('/login');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel(/Password/).fill('PathfinderE2E!2026');
  await page.getByRole('button', { name: /Sign in/ }).click();
  await expect(page).toHaveURL(/\/account$/);

  await page.goto(matchPath);
  await expect(page.locator('.career-card').first()).toBeVisible();
  const careerName = await page.locator('.career-card h3').first().innerText();
  await page.locator('.career-card').first().getByRole('button', { name: /Save/ }).click();
  await expect(page.locator('.career-card').first().getByRole('button', { name: /Saved/ })).toBeVisible();

  await page.evaluate(() => localStorage.removeItem('pathfinder-saved'));
  await page.reload();
  await page.goto('/account');
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible();
  await page.goto(matchPath);
  await page.getByRole('button', { name: /Saved careers/ }).click();
  await expect(page.getByRole('heading', { name: 'Careers worth coming back to.' })).toBeVisible();
  await expect(page.getByRole('heading', { name: careerName })).toBeVisible();
});
