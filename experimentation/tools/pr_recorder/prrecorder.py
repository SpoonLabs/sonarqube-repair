"""Script for recording PRs made with Sorald."""
import argparse
import datetime
import json
import pathlib
import sys
import copy
from typing import List, Optional

import git
import github
import requests


PRS_JSON = "prs.json"

RECORD_INITIAL = "record-initial"
RECORD_FINAL = "record-final"
ADD_MANUAL_EDIT = "add-manual-edit"

ENCODING = "utf8"

PR_DATA_KEY = "pr"
DIFF_DATA_KEY = "diff"

MANUAL_EDITS_KEY = "manual_edits"
BEFORE_OPEN_PR_KEY = "before_open_pr"
AFTER_OPEN_PR_KEY = "after_open_pr"

RECORD_DATA_KEY = "record_metadata"
RECORD_MODIFIED_KEY = "last_modified"


def main():
    parsed_args = parse_args(sys.argv[1:])

    data = read_json_if_exists(parsed_args.prs_json_file, ENCODING)

    repo_slug = f"{parsed_args.owner}/{parsed_args.repo_name}"
    record_id = f"{repo_slug}#{parsed_args.pr_number}"

    gh = github.Github(base_url="https://api.github.com")
    repo = gh.get_repo(repo_slug)
    pr = repo.get_pull(parsed_args.pr_number)

    if parsed_args.command == RECORD_INITIAL:
        record = execute_record_initial(
            repo_slug, record_id, data, pr, parsed_args.sorald_stats_file
        )
    elif parsed_args.command == RECORD_FINAL:
        record = execute_record_final(record_id, data, pr)
        update_record_metadata(record)
    elif parsed_args.command == ADD_MANUAL_EDIT:
        record = execute_add_manual_edit(
            record_id,
            data,
            parsed_args.diff_file,
            parsed_args.edit_reason,
            parsed_args.edit_type,
        )
        update_record_metadata(record)
    else:
        raise RuntimeError(f"Unrecognized command {parsed_args.command}")

    data[record_id] = record
    parsed_args.prs_json_file.write_text(json.dumps(data, indent=4), encoding=ENCODING)


def execute_record_initial(
    repo_slug: str,
    record_id: str,
    data: dict,
    pr: github.PullRequest.PullRequest,
    sorald_stats_file: Optional[pathlib.Path],
) -> dict:
    if record_id in data:
        print(
            f"Cannot create inital record for {record_id}: already exists!",
            file=sys.stderr,
        )
        sys.exit(1)

    sorald_stats = read_json_if_exists(sorald_stats_file, sys.getdefaultencoding())
    return create_initial_record(repo_slug, pr, sorald_stats)


def execute_record_final(
    record_id: str,
    data: dict,
    pr: github.PullRequest.PullRequest,
) -> dict:
    if record_id not in data:
        print(
            f"Cannot create final record for {record_id}: "
            f"initial record does not exist. Please run {RECORD_INITIAL} "
            "for this record first."
        )
        sys.exit(1)

    record = copy.deepcopy(data[record_id])
    record[PR_DATA_KEY].update(get_pr_state(pr))
    record[DIFF_DATA_KEY]["final"] = get_diff(pr.diff_url)

    return record


def execute_add_manual_edit(
    record_id: str,
    data: dict,
    diff_file: pathlib.Path,
    edit_reason: str,
    edit_type: str,
) -> dict:
    if record_id not in data:
        print(f"No such record: {record_id}", file=sys.stderr)
        sys.exit(1)

    diff = diff_file.read_text(encoding=sys.getdefaultencoding())
    record = copy.deepcopy(data[record_id])
    record[MANUAL_EDITS_KEY].append(dict(type=edit_type, reason=edit_reason, diff=diff))

    return record


def create_initial_record(
    repo_slug: str, pr: github.PullRequest.PullRequest, sorald_stats: dict
) -> dict:
    created_at = str(datetime.datetime.now())
    return {
        "repo_slug": repo_slug,
        PR_DATA_KEY: get_pr_state(pr),
        "sorald_statistics": sorald_stats,
        DIFF_DATA_KEY: dict(initial=get_diff(pr.diff_url), final=None),
        MANUAL_EDITS_KEY: [],
        RECORD_DATA_KEY: {"created_at": created_at, RECORD_MODIFIED_KEY: created_at},
    }


def update_record_metadata(record: dict):
    record[RECORD_DATA_KEY][RECORD_MODIFIED_KEY] = str(datetime.datetime.now())


def get_diff(diff_url: str) -> str:
    resp = requests.get(diff_url)
    return resp.content.decode(encoding=resp.encoding)


def read_json_if_exists(path: Optional[pathlib.Path], encoding: str) -> dict:
    return (
        json.loads(path.read_text(encoding=encoding))
        if path is not None and path.is_file()
        else {}
    )


def get_pr_state(pr: github.PullRequest.PullRequest) -> dict:
    return dict(
        url=pr.html_url,
        created_at=str(pr.created_at),
        closed_at=str(pr.closed_at) if pr.closed_at else None,
        merged_at=str(pr.merged_at) if pr.closed_at else None,
        state=pr.state,
        is_merged=pr.merged,
        number=pr.number,
    )


def parse_args(args: List[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser()

    subparsers = parser.add_subparsers(required=True, dest="command")

    base_parser = argparse.ArgumentParser(add_help=False)
    base_parser.add_argument(
        "-o", "--owner", help="owner of the repository", type=str, required=True
    )
    base_parser.add_argument(
        "-r", "--repo-name", help="name of the repository", type=str, required=True
    )
    base_parser.add_argument(
        "-p", "--pr-number", help="the PR number", type=int, required=True
    )
    base_parser.add_argument(
        "-f",
        "--prs-json-file",
        help=f"the file to store records in (default: {PRS_JSON})",
        type=pathlib.Path,
        default=PRS_JSON,
    )

    initial_record_cmd = subparsers.add_parser(
        RECORD_INITIAL,
        help="make the initial record for a PR",
        description="Make the initial record for a PR.",
        parents=[base_parser],
    )
    initial_record_cmd.add_argument(
        "-s",
        "--sorald-stats-file",
        help="path to Sorald's statistics output",
        type=pathlib.Path,
    )

    subparsers.add_parser(
        RECORD_FINAL,
        help="make the final record for a PR",
        description="Make the final record for a PR. This requires an "
        "initial record to exist.",
        parents=[base_parser],
    )

    manual_edits_cmd = subparsers.add_parser(
        ADD_MANUAL_EDIT,
        help="add a diff representing a manual edit of a PR",
        description="Add a diff representing a manual edit of a PR. This can "
        "for example be a manual edit made before opening the PR as it "
        "was deemed crucial for the PR, or an edit made after opening the "
        "PR at the request of maintainers.",
        parents=[base_parser],
    )

    manual_edits_cmd.add_argument(
        "-d",
        "--diff-file",
        help="path to a file with the diff representing the manual edit",
        type=pathlib.Path,
        required=True,
    )
    manual_edits_cmd.add_argument(
        "-e",
        "--edit-reason",
        help="the reason for the edit (freeform text, but keep it short!)",
        type=str,
        required=True,
    )
    manual_edits_cmd.add_argument(
        "-t",
        "--edit-type",
        help="type of the edit",
        choices=[BEFORE_OPEN_PR_KEY, AFTER_OPEN_PR_KEY],
    )

    # workaround for bug
    return parser.parse_args(args)


if __name__ == "__main__":
    main()
