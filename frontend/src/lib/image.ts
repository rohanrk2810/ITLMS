/**
 * Shrinks a picture in the browser before it is sent, so a phone photo of several megabytes becomes a few
 * hundred kilobytes. PNG logos keep their transparency; everything else becomes JPEG. Falls back to the original
 * file when the browser cannot decode it (the server then accepts or refuses it on its own rules).
 */
export async function shrinkImage(file: File, maxSide: number, keepAlpha = false): Promise<Blob> {
  try {
    const bitmap = await createImageBitmap(file)
    const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height))
    if (scale === 1 && file.size < 400 * 1024 && (file.type === 'image/jpeg' || (keepAlpha && file.type === 'image/png'))) {
      bitmap.close()
      return file
    }
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(bitmap.width * scale)
    canvas.height = Math.round(bitmap.height * scale)
    const context = canvas.getContext('2d')
    if (!context) return file
    context.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
    bitmap.close()
    const png = keepAlpha && file.type === 'image/png'
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, png ? 'image/png' : 'image/jpeg', 0.85))
    return blob ?? file
  } catch {
    return file
  }
}
