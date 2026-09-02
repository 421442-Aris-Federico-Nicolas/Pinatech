import { HttpErrorResponse } from '@angular/common/http';
import { problemDetail } from '../api/problem-detail';

const MAX_INPUT_SIZE = 30 * 1024 * 1024;
const MAX_OUTPUT_SIZE = 5 * 1024 * 1024;
const MAX_OUTPUT_EDGE = 2560;
const MAX_OUTPUT_PIXELS = 12_000_000;
const JPEG_QUALITIES = [0.86, 0.74, 0.62];
const HEIC_TYPES = new Set(['image/heic', 'image/heif', 'image/heic-sequence', 'image/heif-sequence']);
const SUPPORTED_TYPES = new Set(['image/jpeg', 'image/jpg', 'image/png']);

export class TicketImageError extends Error {}

export async function prepareTicketImage(file: File): Promise<File> {
  if (!isSupported(file)) {
    throw new TicketImageError(`“${file.name}” debe ser una imagen JPEG, PNG, HEIC o HEIF.`);
  }
  if (file.size > MAX_INPUT_SIZE) {
    throw new TicketImageError(`“${file.name}” supera el máximo de 30 MiB para procesar imágenes.`);
  }

  const heic = isHeic(file);
  const source = heic ? await convertHeic(file) : file;
  const decoded = await decodeImage(source, file.name);

  try {
    const pixels = decoded.width * decoded.height;
    const canKeepOriginal = !heic
      && file.size <= MAX_OUTPUT_SIZE
      && decoded.width <= MAX_OUTPUT_EDGE
      && decoded.height <= MAX_OUTPUT_EDGE
      && pixels <= MAX_OUTPUT_PIXELS;
    if (canKeepOriginal) return file;
    const canKeepConverted = heic
      && source.size <= MAX_OUTPUT_SIZE
      && decoded.width <= MAX_OUTPUT_EDGE
      && decoded.height <= MAX_OUTPUT_EDGE
      && pixels <= MAX_OUTPUT_PIXELS;
    if (canKeepConverted) {
      return new File([source], jpegName(file.name), { type: 'image/jpeg', lastModified: file.lastModified });
    }

    const normalized = await renderJpeg(decoded.source, decoded.width, decoded.height);
    return new File([normalized], jpegName(file.name), { type: 'image/jpeg', lastModified: file.lastModified });
  } finally {
    decoded.dispose();
  }
}

export function ticketImageUploadError(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) return 'No pudimos subir la imagen. Intentá nuevamente.';
  const detail = problemDetail(error)?.detail?.toLowerCase() ?? '';
  if (error.status === 0) return 'No pudimos conectarnos con el servicio. Revisá tu conexión e intentá nuevamente.';
  if (error.status === 413 || detail.includes('5 mib')) return 'La imagen supera el máximo permitido de 5 MiB.';
  if (error.status === 409 && detail.includes('10 attachment')) return 'El ticket ya alcanzó el máximo de 10 imágenes.';
  if (error.status === 409 && detail.includes('250 mib')) return 'Alcanzaste la cuota disponible para imágenes de servicio técnico.';
  if (error.status === 400 && detail.includes('dimension')) return 'La imagen tiene una resolución mayor que la permitida.';
  if (error.status === 400) return 'El archivo no es una imagen JPEG o PNG válida.';
  if (error.status === 401 || error.status === 403) return 'Tu sesión no permite agregar imágenes a este ticket. Volvé a iniciar sesión.';
  if (error.status >= 500) return 'El servidor no pudo procesar la imagen. Intentá nuevamente más tarde.';
  return 'No pudimos subir la imagen. Intentá nuevamente.';
}

function isSupported(file: File): boolean {
  const type = file.type.toLowerCase();
  if (SUPPORTED_TYPES.has(type) || HEIC_TYPES.has(type)) return true;
  return /\.(jpe?g|png|heic|heif)$/i.test(file.name) && (!type || type === 'application/octet-stream');
}

function isHeic(file: File): boolean {
  return HEIC_TYPES.has(file.type.toLowerCase()) || /\.(heic|heif)$/i.test(file.name);
}

async function convertHeic(file: File): Promise<Blob> {
  try {
    const { default: heic2any } = await import('heic2any');
    const converted = await heic2any({ blob: file, toType: 'image/jpeg', quality: 0.9 });
    return Array.isArray(converted) ? converted[0] : converted;
  } catch {
    throw new TicketImageError(`No pudimos convertir “${file.name}” desde HEIC/HEIF. Probá tomar otra foto o compartirla como JPEG.`);
  }
}

async function decodeImage(blob: Blob, fileName: string): Promise<DecodedImage> {
  try {
    if (typeof createImageBitmap === 'function') {
      const bitmap = await createImageBitmap(blob);
      if (bitmap.width <= 0 || bitmap.height <= 0) throw new Error('Invalid image dimensions');
      return { source: bitmap, width: bitmap.width, height: bitmap.height, dispose: () => bitmap.close() };
    }

    const url = URL.createObjectURL(blob);
    const image = new Image();
    try {
      image.src = url;
      await image.decode();
      if (image.naturalWidth <= 0 || image.naturalHeight <= 0) throw new Error('Invalid image dimensions');
      return { source: image, width: image.naturalWidth, height: image.naturalHeight, dispose: () => URL.revokeObjectURL(url) };
    } catch (error) {
      URL.revokeObjectURL(url);
      throw error;
    }
  } catch {
    throw new TicketImageError(`“${fileName}” no se pudo leer como una imagen válida.`);
  }
}

async function renderJpeg(source: CanvasImageSource, sourceWidth: number, sourceHeight: number): Promise<Blob> {
  let scale = Math.min(1, MAX_OUTPUT_EDGE / Math.max(sourceWidth, sourceHeight));
  const canvas = document.createElement('canvas');
  const context = canvas.getContext('2d', { alpha: false });
  if (!context) throw new TicketImageError('Este navegador no pudo preparar la imagen.');

  for (let attempt = 0; attempt < 4; attempt += 1) {
    canvas.width = Math.max(1, Math.round(sourceWidth * scale));
    canvas.height = Math.max(1, Math.round(sourceHeight * scale));
    context.fillStyle = '#ffffff';
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.drawImage(source, 0, 0, canvas.width, canvas.height);

    for (const quality of JPEG_QUALITIES) {
      const blob = await canvasBlob(canvas, quality);
      if (blob.size <= MAX_OUTPUT_SIZE && canvas.width * canvas.height <= MAX_OUTPUT_PIXELS) return blob;
    }
    scale *= 0.8;
  }

  throw new TicketImageError('No pudimos reducir la imagen al tamaño permitido.');
}

function canvasBlob(canvas: HTMLCanvasElement, quality: number): Promise<Blob> {
  return new Promise((resolve, reject) => canvas.toBlob(
    (blob) => blob ? resolve(blob) : reject(new TicketImageError('No pudimos procesar la imagen.')),
    'image/jpeg',
    quality,
  ));
}

function jpegName(fileName: string): string {
  const base = fileName.replace(/\.[^.]+$/, '').trim() || 'imagen';
  return `${base}.jpg`;
}

interface DecodedImage {
  source: CanvasImageSource;
  width: number;
  height: number;
  dispose: () => void;
}
