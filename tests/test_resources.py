import json
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

from scripts import check_links


class SkillResourceTests(unittest.TestCase):
    def test_catalog_covers_25_distinct_skills_with_required_fields(self):
        catalog = json.loads((Path(__file__).parents[1] / "data/resources/skill_resources.json").read_text(encoding="utf-8"))
        self.assertGreaterEqual(len(catalog), 25)
        self.assertEqual(len({entry["skill"] for entry in catalog}), len(catalog))
        for entry in catalog:
            self.assertTrue(entry["url"].startswith("https://"))
            self.assertTrue(entry["provider"] and entry["level"] and entry["cost"])
            self.assertGreater(entry["estimated_hours"], 0)

    def test_check_url_uses_https_and_accepts_successful_response(self):
        self.assertEqual(check_links.check_url("http://example.com")[0], False)
        response = MagicMock(status=200)
        response.__enter__.return_value = response
        with patch.object(check_links, "urlopen", return_value=response):
            self.assertEqual(check_links.check_url("https://example.com"), (True, "200"))

    def test_checker_exits_nonzero_when_a_resource_is_dead(self):
        catalog = Path(__file__).with_name(".resources-test.json")
        try:
            catalog.write_text(json.dumps([{"skill": "Example", "url": "https://example.com"}]), encoding="utf-8")
            with patch.object(check_links, "CATALOG", catalog), patch.object(check_links, "check_url", return_value=(False, "404")):
                self.assertEqual(check_links.main(), 1)
        finally:
            catalog.unlink(missing_ok=True)


if __name__ == "__main__":
    unittest.main()
