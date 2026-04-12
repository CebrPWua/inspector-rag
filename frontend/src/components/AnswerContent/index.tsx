import { Alert } from 'antd'
import { parseAnswer, linkifyCiteNumbers } from '../../utils/parseAnswer'
import styles from './index.module.css'

interface AnswerContentProps {
  answer: string
  answerStatus?: 'success' | 'reject' | 'failed'
  onCiteClick?: (citeNo: number) => void
}

const SECTION_STYLES: Record<string, string> = {
  desc: styles.sectionDesc,
  law: styles.sectionLaw,
  risk: styles.sectionRisk,
  suggest: styles.sectionSuggest,
  cite: styles.sectionCite,
  raw: styles.sectionRaw,
}

export function AnswerContent({ answer, answerStatus, onCiteClick }: AnswerContentProps) {
  if (answerStatus === 'reject') {
    return (
      <Alert
        type="warning"
        showIcon
        message="未检索到有效法规依据"
        description={
          <div>
            <p style={{ margin: '4px 0 8px' }}>{answer}</p>
            <p style={{ margin: 0, color: '#888', fontSize: 13 }}>
              建议：补充问题场景、限定行业范围或扩充知识库后重试。
            </p>
          </div>
        }
      />
    )
  }

  if (answerStatus === 'failed') {
    return (
      <Alert
        type="error"
        showIcon
        message="回答生成失败"
        description="服务暂时不可用，请稍后重试。"
      />
    )
  }

  const sections = parseAnswer(answer)

  const handleClick = (e: React.MouseEvent<HTMLDivElement>) => {
    const target = e.target as HTMLElement
    if (target.classList.contains('cite-ref')) {
      const citeNo = parseInt(target.dataset.cite ?? '0', 10)
      if (citeNo && onCiteClick) onCiteClick(citeNo)
    }
  }

  return (
    <div className={styles.wrapper} onClick={handleClick}>
      {sections.map((section, i) => (
        <div key={i} className={`${styles.section} ${SECTION_STYLES[section.type] ?? ''}`}>
          {section.title && (
            <div className={styles.sectionTitle}>{section.title}：</div>
          )}
          <div
            className={styles.sectionContent}
            dangerouslySetInnerHTML={{ __html: linkifyCiteNumbers(section.content) }}
          />
        </div>
      ))}
    </div>
  )
}
