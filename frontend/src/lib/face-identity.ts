import type * as FaceApi from '@vladmandic/face-api'

/** Served from this app (public/models/face-api), never from a CDN: see scripts/copy-mediapipe.mjs. */
const MODEL_PATH = '/models/face-api'

/**
 * Two descriptors of the same person usually sit within about 0.4 of each other, two different people usually beyond
 * 0.6. A webcam in poor light or a turned head can stretch the first, so this is a warning level, not proof.
 */
export const SAME_FACE_MAX_DISTANCE = 0.6
/** The samples taken at the start must agree with each other this well, or the student is asked to hold still and again. */
export const REFERENCE_SPREAD_MAX = 0.45
export const REFERENCE_SAMPLES = 3

let loading: Promise<typeof FaceApi> | null = null

/**
 * Loads the face-recognition networks once. The library and its weights are several MB, so nothing is fetched until a
 * test that needs the camera is opened. A failed load is forgotten so the next call tries again.
 */
export function loadFaceIdentity(): Promise<typeof FaceApi> {
  if (!loading) {
    loading = (async () => {
      const faceapi = await import('@vladmandic/face-api')
      // The graphics card when there is one, the processor when there is not. The package's own typings leave out
      // the backend calls, so only the two that are used are described here.
      const tf = faceapi.tf as unknown as { setBackend(name: string): Promise<boolean>; ready(): Promise<void> }
      try {
        if (!(await tf.setBackend('webgl'))) await tf.setBackend('cpu')
      } catch {
        await tf.setBackend('cpu')
      }
      await tf.ready()
      await Promise.all([
        faceapi.nets.tinyFaceDetector.loadFromUri(MODEL_PATH),
        faceapi.nets.faceLandmark68TinyNet.loadFromUri(MODEL_PATH),
        faceapi.nets.faceRecognitionNet.loadFromUri(MODEL_PATH),
      ])
      return faceapi
    })()
    loading.catch(() => {
      loading = null
    })
  }
  return loading
}

/** A list of 128 numbers that describes the face in view, or null when there is no clear face. It is not a picture. */
export async function describeFace(faceapi: typeof FaceApi, video: HTMLVideoElement): Promise<Float32Array | null> {
  if (video.readyState < 2 || video.videoWidth === 0) return null
  const found = await faceapi
    .detectSingleFace(video, new faceapi.TinyFaceDetectorOptions({ inputSize: 320, scoreThreshold: 0.5 }))
    .withFaceLandmarks(true)
    .withFaceDescriptor()
  return found ? found.descriptor : null
}

export function faceDistance(a: Float32Array, b: Float32Array): number {
  let sum = 0
  for (let i = 0; i < a.length; i++) {
    const d = a[i] - b[i]
    sum += d * d
  }
  return Math.sqrt(sum)
}

/** How far the face is from the closest of the faces recorded at the start. */
export function distanceFromReference(reference: Float32Array[], face: Float32Array): number {
  return Math.min(...reference.map((r) => faceDistance(r, face)))
}

/** Whether the samples taken at the start look like one face. */
export function samplesAgree(samples: Float32Array[]): boolean {
  for (let i = 0; i < samples.length; i++) {
    for (let j = i + 1; j < samples.length; j++) {
      if (faceDistance(samples[i], samples[j]) > REFERENCE_SPREAD_MAX) return false
    }
  }
  return true
}
