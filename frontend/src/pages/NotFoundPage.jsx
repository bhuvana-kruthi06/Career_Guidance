import { Link } from 'react-router-dom';

export default function NotFoundPage() {
  return <main className="not-found-page">
    <p className="eyebrow">PATHFINDER · 404</p>
    <h1>We couldn’t find that page.</h1>
    <p>The link may be out of date. Your profile stays saved in this browser tab.</p>
    <Link className="primary-button" to="/match">Back to career match <span>→</span></Link>
  </main>;
}
