/** 将结构化回答文本解析为段落数组 */
export interface AnswerSection {
  title: string
  content: string
  type: 'desc' | 'law' | 'risk' | 'suggest' | 'cite' | 'raw'
}

const SECTION_MAP: { keyword: string; type: AnswerSection['type'] }[] = [
  { keyword: '问题描述', type: 'desc' },
  { keyword: '法规依据', type: 'law' },
  { keyword: '风险说明', type: 'risk' },
  { keyword: '整改建议', type: 'suggest' },
  { keyword: '引用来源', type: 'cite' },
]

export function parseAnswer(answer: string): AnswerSection[] {
  if (!answer) return []

  // 按已知标题分割
  const sections: AnswerSection[] = []
  let remaining = answer

  // 找到所有段落起始位置
  const splitPoints: { index: number; keyword: string; type: AnswerSection['type'] }[] = []

  for (const { keyword, type } of SECTION_MAP) {
    const regex = new RegExp(`(^|\\n)${keyword}[：:]`, 'g')
    let match
    while ((match = regex.exec(answer)) !== null) {
      splitPoints.push({ index: match.index, keyword, type })
    }
  }

  if (splitPoints.length === 0) {
    // 无结构，返回原始文本
    return [{ title: '', content: answer, type: 'raw' }]
  }

  splitPoints.sort((a, b) => a.index - b.index)

  for (let i = 0; i < splitPoints.length; i++) {
    const current = splitPoints[i]
    const next = splitPoints[i + 1]
    const start = current.index + (answer[current.index] === '\n' ? 1 : 0)
    const end = next ? next.index : answer.length
    const raw = answer.slice(start, end).trim()
    const colonIdx = raw.indexOf('：') !== -1 ? raw.indexOf('：') : raw.indexOf(':')
    const content = colonIdx >= 0 ? raw.slice(colonIdx + 1).trim() : raw
    sections.push({ title: current.keyword, content, type: current.type })
  }

  // 前面如果有文本未被任何段落覆盖
  if (splitPoints[0].index > 0) {
    const prefix = answer.slice(0, splitPoints[0].index).trim()
    if (prefix) {
      sections.unshift({ title: '', content: prefix, type: 'raw' })
    }
  }

  return sections
}

/** 将引用序号 [1] [2] 替换为可点击标记（返回带 data-cite 的 html 片段） */
export function linkifyCiteNumbers(text: string): string {
  return text.replace(/\[(\d+)\]/g, (_, n) =>
    `<span class="cite-ref" data-cite="${n}">[${n}]</span>`
  )
}
