import type { FaceDetector } from '@mediapipe/tasks-vision'

/** Served from this app (public/), never from a CDN: see scripts/copy-mediapipe.mjs. */
const WASM_PATH = '/mediapipe/wasm'
const MODEL_PATH = '/models/blaze_face_short_range.tflite'

/** A face narrower than this fraction of the picture is too far away to count as "properly visible". */
const MIN_FACE_WIDTH_RATIO = 0.1
const MIN_CONFIDENCE = 0.6

let loading: Promise<FaceDetector> | null = null

/**
 * Loads the face detector once. The library and its runtime are several MB, so nothing is fetched until a
 * test that needs the camera is opened. A failed load is forgotten so the next call tries again.
 */
export function loadFaceDetector(): Promise<FaceDetector> {
  if (!loading) {
    loading = (async () => {
      const { FaceDetector: Detector, FilesetResolver } = await import('@mediapipe/tasks-vision')
      const files = await FilesetResolver.forVisionTasks(WASM_PATH)
      return Detector.createFromOptions(files, {
        baseOptions: { modelAssetPath: MODEL_PATH, delegate: 'CPU' },
        runningMode: 'VIDEO',
        minDetectionConfidence: MIN_CONFIDENCE,
      })
    })()
    loading.catch(() => {
      loading = null
    })
  }
  return loading
}

/** How many properly visible faces the camera shows right now. */
export function countFaces(detector: FaceDetector, video: HTMLVideoElement): number {
  if (video.readyState < 2 || video.videoWidth === 0) return 0
  const { detections } = detector.detectForVideo(video, performance.now())
  return detections.filter((d) => (d.boundingBox?.width ?? 0) >= video.videoWidth * MIN_FACE_WIDTH_RATIO).length
}

/** The oval drawn over the student's picture, as fractions of the picture. Shared by the check and the overlay. */
export const OVAL = { rx: 0.3, ry: 0.42 }
/** How far past the oval a corner of the face box may reach before the face counts as outside. */
const OVAL_TOLERANCE = 1.2

function insideOval(x: number, y: number, video: HTMLVideoElement, scale: number): boolean {
  const dx = (x / video.videoWidth - 0.5) / (OVAL.rx * scale)
  const dy = (y / video.videoHeight - 0.5) / (OVAL.ry * scale)
  return dx * dx + dy * dy <= 1
}

export interface FaceReading {
  /** Properly visible faces. */
  count: number
  /** With exactly one face: whether it sits inside the oval. */
  inOval: boolean
}

/** Like countFaces, and also whether the single face is inside the oval (its centre inside, its corners near). */
export function readFaces(detector: FaceDetector, video: HTMLVideoElement): FaceReading {
  if (video.readyState < 2 || video.videoWidth === 0) return { count: 0, inOval: false }
  const { detections } = detector.detectForVideo(video, performance.now())
  const faces = detections.filter((d) => (d.boundingBox?.width ?? 0) >= video.videoWidth * MIN_FACE_WIDTH_RATIO)
  const box = faces.length === 1 ? faces[0].boundingBox : undefined
  if (!box) return { count: faces.length, inOval: false }
  const { originX: x, originY: y, width: w, height: h } = box
  const inOval =
    insideOval(x + w / 2, y + h / 2, video, 1) &&
    [
      [x, y],
      [x + w, y],
      [x, y + h],
      [x + w, y + h],
    ].every(([cx, cy]) => insideOval(cx, cy, video, OVAL_TOLERANCE))
  return { count: faces.length, inOval }
}

export type CameraProblem = 'denied' | 'not-found' | 'in-use' | 'unsupported' | 'other'

/** Asks for the camera. Must be called from a click, so the browser may show its permission prompt. */
export async function openCamera(): Promise<MediaStream> {
  if (!navigator.mediaDevices?.getUserMedia) throw new CameraError('unsupported')
  try {
    return await navigator.mediaDevices.getUserMedia({
      video: { width: { ideal: 640 }, height: { ideal: 480 }, facingMode: 'user' },
      audio: false,
    })
  } catch (error) {
    const name = error instanceof DOMException ? error.name : ''
    if (name === 'NotAllowedError' || name === 'SecurityError') throw new CameraError('denied')
    if (name === 'NotFoundError' || name === 'OverconstrainedError') throw new CameraError('not-found')
    if (name === 'NotReadableError') throw new CameraError('in-use')
    throw new CameraError('other')
  }
}

export class CameraError extends Error {
  readonly problem: CameraProblem

  constructor(problem: CameraProblem) {
    super(problem)
    this.problem = problem
  }
}

export const CAMERA_PROBLEM_TEXT: Record<CameraProblem, string> = {
  denied: "Camera permission was blocked. Allow the camera using the icon in your browser's address bar, then try again.",
  'not-found': 'No camera was found on this device.',
  'in-use': 'Your camera is being used by another application. Close it and try again.',
  unsupported: 'This browser cannot use the camera here. Use a current Chrome, Edge or Firefox over https or localhost.',
  other: 'The camera could not be started. Try again.',
}

export function stopStream(stream: MediaStream | null | undefined) {
  stream?.getTracks().forEach((track) => track.stop())
}
