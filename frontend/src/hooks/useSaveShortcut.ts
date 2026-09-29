import { useEffect } from 'react'

/** 모달이 열려 있는 동안 Ctrl(⌘)+S를 눌러 저장(onSave)하게 한다. */
export function useSaveShortcut(open: boolean, onSave: () => void) {
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
        e.preventDefault()
        onSave()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onSave])
}
