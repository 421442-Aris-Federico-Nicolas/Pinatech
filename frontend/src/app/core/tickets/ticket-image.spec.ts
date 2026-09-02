import { HttpErrorResponse } from '@angular/common/http';
import heic2any from 'heic2any';
import { prepareTicketImage, TicketImageError, ticketImageUploadError } from './ticket-image';

vi.mock('heic2any', () => ({ default: vi.fn() }));

describe('ticket image preparation', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('keeps a safe JPEG unchanged after validating its dimensions', async () => {
    const close = vi.fn();
    vi.stubGlobal('createImageBitmap', vi.fn().mockResolvedValue({ width: 1600, height: 1200, close }));
    const file = new File(['jpeg'], 'equipo.jpg', { type: 'image/jpeg' });

    await expect(prepareTicketImage(file)).resolves.toBe(file);
    expect(close).toHaveBeenCalledOnce();
  });

  it('converts HEIC to a JPEG file', async () => {
    vi.mocked(heic2any).mockResolvedValue(new Blob(['jpeg'], { type: 'image/jpeg' }));
    vi.stubGlobal('createImageBitmap', vi.fn().mockResolvedValue({ width: 1200, height: 900, close: vi.fn() }));
    const file = new File(['heic'], 'equipo.heic', { type: 'image/heic', lastModified: 123 });

    const result = await prepareTicketImage(file);

    expect(result.name).toBe('equipo.jpg');
    expect(result.type).toBe('image/jpeg');
    expect(result.lastModified).toBe(123);
  });

  it('reduces a standard mobile photo that exceeds the pixel cap', async () => {
    const drawImage = vi.fn();
    vi.stubGlobal('createImageBitmap', vi.fn().mockResolvedValue({ width: 4032, height: 3024, close: vi.fn() }));
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
      fillStyle: '',
      fillRect: vi.fn(),
      drawImage,
    } as unknown as CanvasRenderingContext2D);
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation((callback) => {
      callback(new Blob(['reduced'], { type: 'image/jpeg' }));
    });

    const result = await prepareTicketImage(new File(['jpeg'], 'camara.jpg', { type: 'image/jpeg' }));

    expect(result.type).toBe('image/jpeg');
    expect(drawImage).toHaveBeenCalledWith(expect.anything(), 0, 0, 2560, 1920);
  });

  it('rejects unsupported files before trying to decode them', async () => {
    const file = new File(['document'], 'archivo.pdf', { type: 'application/pdf' });

    await expect(prepareTicketImage(file)).rejects.toBeInstanceOf(TicketImageError);
  });
});

describe('ticket image upload errors', () => {
  it('reports backend dimension errors instead of blaming the connection', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: { detail: 'Image dimensions exceed the allowed limit.' },
    });

    expect(ticketImageUploadError(error)).toContain('resolución');
  });

  it('reports actual network failures', () => {
    expect(ticketImageUploadError(new HttpErrorResponse({ status: 0 }))).toContain('conectarnos');
  });
});
