import { isTauri } from './cloud'

export const APP_VERSION = __APP_VERSION__

export interface AvailableUpdate {
  version: string
  notes: string
  /** 下载并安装，随后重启应用。 */
  install: (onProgress: (pct: number) => void) => Promise<void>
}

/**
 * 桌面端更新：Tauri updater 插件（GitHub Releases 的 latest.json + 签名校验）。
 * 浏览器端没有自更新概念，返回 null。
 */
export async function checkDesktopUpdate(): Promise<AvailableUpdate | null> {
  if (!isTauri()) return null
  const { check } = await import('@tauri-apps/plugin-updater')
  const update = await check()
  if (!update) return null
  return {
    version: update.version,
    notes: update.body ?? '',
    install: async (onProgress) => {
      let total = 0
      let downloaded = 0
      await update.downloadAndInstall((event) => {
        if (event.event === 'Started') {
          total = event.data.contentLength ?? 0
        } else if (event.event === 'Progress') {
          downloaded += event.data.chunkLength
          if (total > 0) onProgress(Math.min(100, Math.round((downloaded * 100) / total)))
        }
      })
      const invoke = (window as any).__TAURI_INTERNALS__?.invoke
      if (invoke) await invoke('restart_app')
    }
  }
}
