"""Generate categorized notes from the release tag's reachable Git history."""

import os
import subprocess
import sys
from pathlib import Path


def git(*args):
    return subprocess.check_output(['git', *args], encoding='utf-8').strip()


def generate_notes(tag, republish_from='', republished_at=''):
    # Start at the parent to exclude the release itself, including on a rerun.
    previous = subprocess.run(
        ['git', 'describe', '--tags', '--match', 'v[0-9]*', '--abbrev=0', f'{tag}^'],
        capture_output=True, encoding='utf-8', check=False,
    )
    previous_tag = previous.stdout.strip() if previous.returncode == 0 else ''
    revision = f'{previous_tag}..{tag}' if previous_tag else tag
    repo_url = f'{os.environ.get("GITHUB_SERVER_URL", "https://github.com")}/{os.environ["GITHUB_REPOSITORY"]}'
    groups = {
        'feat': ('🚀 Features', []),
        'fix': ('🐛 Bug Fixes', []),
        'test': ('🧪 Tests', []),
        'opt': ('⚡ Optimizations', []),
        'dep': ('📦 Dependencies', []),
        'other': ('📝 Other Changes', []),
    }
    seen = set()
    for commit in git('rev-list', '--reverse', revision).splitlines():
        author = git('show', '-s', '--format=%an', commit)
        subject = git('show', '-s', '--format=%s', commit)
        if author == 'github-actions[bot]' or subject.startswith((
            'version:', 'docs: add github release automation workflow',
            'mvn versions:set', 'Updating simple-api-doc-ui version',
        )):
            continue
        files = git('diff-tree', '--root', '--no-commit-id', '--name-only', '-r', commit).splitlines()
        if not any(not name.startswith(('.github/', 'release.yml')) for name in files):
            continue
        if subject in seen:
            continue
        seen.add(subject)
        category = subject.partition(':')[0]
        category = 'fix' if category == 'bug' else category
        groups.get(category, groups['other'])[1].append((subject, commit))

    lines = []
    if republish_from:
        commit = git('rev-parse', f'{tag}^{{commit}}')
        lines.extend([
            '## 同版本重新发布', '',
            f'- 发布时间（UTC）：{republished_at}',
            f'- 原提交：[{republish_from[:12]}]({repo_url}/commit/{republish_from})',
            f'- 构建提交：[{commit[:12]}]({repo_url}/commit/{commit})',
            f'- 本次变更：[查看差异]({repo_url}/compare/{republish_from}...{commit})',
            '', '### 本次修复', '',
        ])
        changes = git('log', '--reverse', '--format=%H%x09%s', f'{republish_from}..{commit}')
        for change in changes.splitlines():
            change_commit, subject = change.split('\t', 1)
            lines.append(f'- [{subject}]({repo_url}/commit/{change_commit})')
        if not changes:
            lines.append('- 无新增提交，仅重新构建发布。')
        lines.append('')
    lines.extend(["## What's Changed", ''])
    for title, entries in groups.values():
        if entries:
            lines.extend([f'### {title}', ''])
            lines.extend(f'{index}. [{subject}]({repo_url}/commit/{commit})'
                         for index, (subject, commit) in enumerate(entries, 1))
            lines.append('')
    if previous_tag:
        lines.append(f'**Full Changelog**: {repo_url}/compare/{previous_tag}...{tag}')
    return '\n'.join(lines) + '\n'


if __name__ == '__main__':
    Path(sys.argv[2]).write_text(generate_notes(
        sys.argv[1], os.environ.get('REPUBLISH_PREVIOUS_COMMIT', ''),
        os.environ.get('REPUBLISHED_AT', ''),
    ), encoding='utf-8')
