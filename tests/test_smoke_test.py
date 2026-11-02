import sys
from unittest.mock import patch

from scripts.smoke_test import main


def test_smoke_script_checks_skills_matches_and_bad_request(capsys):
    responses = [
        (200, [{"id": "1", "name": "Critical Thinking", "category": "Essential"}]),
        (200, [{"occupation_title": "Software Developer", "match_score": 90}]),
        (400, {"error": "rating must be between 1 and 5"}),
    ]
    with patch.object(sys, "argv", ["smoke_test.py", "--base-url", "http://127.0.0.1:8091"]):
        with patch("scripts.smoke_test.send_json", side_effect=responses) as send:
            assert main() == 0

    assert send.call_count == 3
    assert "/api/skills" in send.call_args_list[0].args[0]
    assert "/api/matches" in send.call_args_list[1].args[0]
    valid_payload = send.call_args_list[1].args[1]
    assert valid_payload["assessedOnly"] is True
    invalid_payload = send.call_args_list[2].args[1]
    assert invalid_payload["studentSkills"]["Critical Thinking"] == 9
    assert "Smoke test passed." in capsys.readouterr().out


def test_smoke_script_fails_if_invalid_request_is_not_rejected(capsys):
    responses = [
        (200, [{"id": "1", "name": "Critical Thinking", "category": "Essential"}]),
        (200, [{"occupation_title": "Software Developer", "match_score": 90}]),
        (200, []),
    ]
    with patch.object(sys, "argv", ["smoke_test.py"]):
        with patch("scripts.smoke_test.send_json", side_effect=responses):
            assert main() == 1
    assert "expected HTTP 400" in capsys.readouterr().err
