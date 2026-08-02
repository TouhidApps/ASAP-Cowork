// Vision models generally recommend capping the long edge around this size —
// larger images cost more tokens without adding useful detail, and phone
// photos routinely exceed the backend's 10MB upload cap otherwise.
const MAX_DIMENSION = 1568
const MAX_BYTES_BEFORE_REENCODE = 2 * 1024 * 1024
const JPEG_QUALITY = 0.85

/**
 * Downscales/re-encodes an oversized image client-side before upload, so a
 * multi-megapixel phone photo doesn't get rejected by the backend's size
 * limit or bloat the payload sent to the model. Animated GIFs are left
 * untouched — drawing one to a canvas would flatten it to one frame.
 */
export async function resizeImageIfNeeded(file: File): Promise<File> {
  if (!file.type.startsWith('image/') || file.type === 'image/gif') return file

  let bitmap: ImageBitmap
  try {
    bitmap = await createImageBitmap(file)
  } catch {
    return file
  }

  const scale = Math.min(1, MAX_DIMENSION / Math.max(bitmap.width, bitmap.height))
  const needsResize = scale < 1
  const needsReencode = file.size > MAX_BYTES_BEFORE_REENCODE

  if (!needsResize && !needsReencode) {
    bitmap.close()
    return file
  }

  const width = Math.round(bitmap.width * scale)
  const height = Math.round(bitmap.height * scale)
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')
  if (!ctx) {
    bitmap.close()
    return file
  }
  ctx.drawImage(bitmap, 0, 0, width, height)
  bitmap.close()

  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', JPEG_QUALITY))
  if (!blob) return file

  const newName = `${file.name.replace(/\.[^.]+$/, '')}.jpg`
  return new File([blob], newName, { type: 'image/jpeg' })
}
