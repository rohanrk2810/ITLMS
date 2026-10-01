/** Why the microphone could not be opened, in terms the student can act on. */
export type MicProblem = 'denied' | 'not-found' | 'in-use' | 'unsupported' | 'other'

export class MicError extends Error {
  readonly problem: MicProblem

  constructor(problem: MicProblem) {
    super(problem)
    this.problem = problem
  }
}

export const MIC_PROBLEM_TEXT: Record<MicProblem, string> = {
  denied: "Microphone permission was blocked. Allow the microphone using the icon in your browser's address bar, then try again.",
  'not-found': 'No microphone was found on this device.',
  'in-use': 'Your microphone is being used by another application. Close it and try again.',
  unsupported: 'This browser cannot use the microphone here. Use a current Chrome, Edge or Firefox over https or localhost.',
  other: 'The microphone could not be started. Try again.',
}

/**
 * Asks for the microphone. Must be called from a click, so the browser may show its permission prompt.
 * Noise suppression and automatic gain are off: they exist to hide the very thing a level check needs to see.
 */
export async function openMicrophone(): Promise<MediaStream> {
  if (!navigator.mediaDevices?.getUserMedia) throw new MicError('unsupported')
  try {
    return await navigator.mediaDevices.getUserMedia({
      audio: { echoCancellation: true, noiseSuppression: false, autoGainControl: false },
      video: false,
    })
  } catch (error) {
    const name = error instanceof DOMException ? error.name : ''
    if (name === 'NotAllowedError' || name === 'SecurityError') throw new MicError('denied')
    if (name === 'NotFoundError' || name === 'OverconstrainedError') throw new MicError('not-found')
    if (name === 'NotReadableError') throw new MicError('in-use')
    throw new MicError('other')
  }
}

/** Loudness of the current moment, 0 to 1 (root-mean-square of the waveform). */
export function readLevel(analyser: AnalyserNode, buffer: Float32Array<ArrayBuffer>): number {
  analyser.getFloatTimeDomainData(buffer)
  let sum = 0
  for (const sample of buffer) sum += sample * sample
  return Math.sqrt(sum / buffer.length)
}

/** Opens an analyser on a stream. Close the returned context when done. */
export function openAnalyser(stream: MediaStream): { context: AudioContext; analyser: AnalyserNode } {
  const context = new AudioContext()
  const analyser = context.createAnalyser()
  analyser.fftSize = 1024
  context.createMediaStreamSource(stream).connect(analyser)
  void context.resume().catch(() => undefined)
  return { context, analyser }
}
