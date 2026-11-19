import { BASE_URL, getAuthToken } from './client';

export interface UploadPhotoOptions {
  uri: string;
  stopId: string;
  mimeType?: string;
  sizeBytes?: number;
}

export interface UploadPhotoResult {
  mediaId: string;
  url?: string;
  isMock?: boolean;
}

const PHOTO_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

async function mediaError(res: Response, step: string): Promise<Error> {
  try {
    const body = (await res.json()) as { error?: { message?: string } };
    if (body?.error?.message) return new Error(body.error.message);
  } catch {
    // not JSON
  }
  return new Error(`Photo upload failed at ${step} (${res.status})`);
}

/**
 * Uploads a proof of delivery photo to the media service following the 3-step upload flow:
 * 1. POST /media/upload-url -> gets signed uploadUrl + mediaId (signed for the exact size and type)
 * 2. PUT <uploadUrl> with the image blob
 * 3. POST /media/{mediaId}/complete -> confirms upload
 *
 * Throws if any step fails: logistics only accepts a delivery with a real photo id.
 */
export async function uploadDeliveryPhoto(options: UploadPhotoOptions): Promise<UploadPhotoResult> {
  const { uri, stopId } = options;
  const token = getAuthToken();
  const auth: Record<string, string> = token ? { Authorization: `Bearer ${token}` } : {};

  try {
    // The upload URL is signed for the exact byte size, so read the image first.
    const blob = await (await fetch(uri)).blob();
    const mimeType = options.mimeType ?? (PHOTO_TYPES.includes(blob.type) ? blob.type : 'image/jpeg');
    const sizeBytes = options.sizeBytes ?? blob.size;

    const uploadUrlRes = await fetch(`${BASE_URL}/media/upload-url`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...auth },
      body: JSON.stringify({
        ownerType: 'DELIVERY',
        ownerId: stopId,
        contentType: mimeType,
        sizeBytes,
      }),
    });
    if (!uploadUrlRes.ok) throw await mediaError(uploadUrlRes, 'upload-url');

    const { mediaId, uploadUrl, headers = {} } = (await uploadUrlRes.json()) as {
      mediaId: string;
      uploadUrl: string;
      headers?: Record<string, string>;
    };

    const putRes = await fetch(uploadUrl, {
      method: 'PUT',
      headers: { 'Content-Type': mimeType, ...headers },
      body: blob,
    });
    if (!putRes.ok) throw new Error(`Photo upload failed (${putRes.status})`);

    const completeRes = await fetch(`${BASE_URL}/media/${mediaId}/complete`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...auth },
      body: JSON.stringify({}),
    });
    if (!completeRes.ok) throw await mediaError(completeRes, 'complete');

    const data = (await completeRes.json()) as { id?: string; url?: string };
    return { mediaId: data.id ?? mediaId, url: data.url ?? uri, isMock: false };
  } catch (error) {
    // fetch rejects with a TypeError when there is no network
    if (error instanceof TypeError) throw new Error('No connection. Check your network and try again.');
    throw error;
  }
}
