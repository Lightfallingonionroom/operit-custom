#!/usr/bin/env bash
# 一键推送到 GitHub 私有仓库并触发定制构建。
#
# 用法：
#   GITHUB_TOKEN=ghp_xxx REPO=你的用户名/operit-custom bash tools/push-to-github.sh
#
# 说明：
#   - 需要 Personal Access Token（classic 勾 repo，或 fine-grained 勾 Contents: RW + Actions: RW）
#   - 首次推送后 GitHub Actions 会自动跑 .github/workflows/custom-build.yml
#   - 仓库总对象约 208MB，首次 push 视上行带宽需要几分钟到几十分钟

set -euo pipefail

REPO="${REPO:?需要设置 REPO=用户名/仓库名}"
TOKEN="${GITHUB_TOKEN:?需要设置 GITHUB_TOKEN=你的 PAT}"
BRANCH="${BRANCH:-main}"

cd "$(dirname "$0")/.."

if [ ! -d .git ]; then
  echo "当前目录不是 git 仓库，先执行 git init 并提交" >&2
  exit 1
fi

# 统一分支名为 main（GitHub 默认）
git branch -M "$BRANCH" 2>/dev/null || true

REMOTE_URL="https://${TOKEN}@github.com/${REPO}.git"

echo "==> 检查远端仓库是否存在"
if ! git ls-remote "$REMOTE_URL" >/dev/null 2>&1; then
  echo "远端不可达：$REPO" >&2
  echo "请先在 GitHub 上创建空仓库（不要勾选 README/gitignore），或用 API 创建：" >&2
  echo "  curl -H \"Authorization: Bearer \$GITHUB_TOKEN\" -d '{\"name\":\"${REPO##*/}\",\"private\":true}' https://api.github.com/user/repos" >&2
  exit 1
fi

if git remote get-url origin >/dev/null 2>&1; then
  git remote set-url origin "$REMOTE_URL"
else
  git remote add origin "$REMOTE_URL"
fi

echo "==> 推送 $BRANCH 到 $REPO"
git push -u origin "$BRANCH" --force

echo "==> 完成。Actions 页面："
echo "    https://github.com/${REPO}/actions"
echo "==> 手动触发构建（可选）："
echo "    curl -X POST -H \"Authorization: Bearer \$GITHUB_TOKEN\" \\"
echo "      -H 'Accept: application/vnd.github+json' \\"
echo "      https://api.github.com/repos/${REPO}/actions/workflows/custom-build.yml/dispatches \\"
echo "      -d '{\"ref\":\"${BRANCH}\",\"inputs\":{\"gradle_task\":\"assembleDebug\"}}'"