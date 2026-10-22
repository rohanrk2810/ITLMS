import type { ObjectDetector } from '@mediapipe/tasks-vision'

/** Served from this app (public/), never from a CDN: see scripts/copy-mediapipe.mjs. */
const WASM_PATH = '/mediapipe/wasm'
const MODEL_PATH = '/models/efficientdet_lite0.tflite'

/** How sure the detector must be. A phone is a clear object; a person is easier to see where there is none (a poster). */
const PHONE_MIN_SCORE = 0.45
const PERSON_MIN_SCORE = 0.55

let loading: Promise<ObjectDetector> | null = null

/** Loads the object detector once, on first use. A failed load is forgotten so the next call tries again. */
export function loadObjectDetector(): Promise<ObjectDetector> {
  if (!loading) {
    loading = (async () => {
      const { ObjectDetector: Detector, FilesetResolver } = await import('@mediapipe/tasks-vision')
      const files = await FilesetResolver.forVisionTasks(WASM_PATH)
      return Detector.createFromOptions(files, {
        baseOptions: { modelAssetPath: MODEL_PATH, delegate: 'CPU' },
        runningMode: 'VIDEO',
        scoreThreshold: 0.4,
        maxResults: 10,
        categoryAllowlist: ['cell phone', 'person'],
      })
    })()
    loading.catch(() => {
      loading = null
    })
  }
  return loading
}

export interface Scene {
  /** A mobile phone is in view. */
  phone: boolean
  /** How many people (bodies, not only faces) are in view. */
  people: number
}

export function lookAround(detector: ObjectDetector, video: HTMLVideoElement): Scene {
  if (video.readyState < 2 || video.videoWidth === 0) return { phone: false, people: 0 }
  const { detections } = detector.detectForVideo(video, performance.now())
  let phone = false
  let people = 0
  for (const detection of detections) {
    const best = detection.categories[0]
    if (!best) continue
    if (best.categoryName === 'cell phone' && best.score >= PHONE_MIN_SCORE) phone = true
    if (best.categoryName === 'person' && best.score >= PERSON_MIN_SCORE) people += 1
  }
  return { phone, people }
}
