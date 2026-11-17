import type { Grade, ProducePhoto } from './types.ts';

/**
 * Format distance in metres into user-friendly string.
 * Example: 450 -> "450 m", 2400 -> "2.4 km"
 */
export function formatDistance(metres: number): string {
  if (metres < 1000) {
    return `${Math.round(metres)} m`;
  }
  const km = metres / 1000;
  return `${km < 10 ? km.toFixed(1) : Math.round(km)} km`;
}

/**
 * Format ISO date string into readable date.
 * Example: "2026-11-15" -> "15 Nov 2026"
 */
export function formatHarvestDate(dateStr: string): string {
  try {
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return dateStr;
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    return `${d.getUTCDate()} ${months[d.getUTCMonth()]} ${d.getUTCFullYear()}`;
  } catch {
    return dateStr;
  }
}

/**
 * Metadata for produce grade badge.
 */
export function formatGradeBadge(grade?: Grade | null): {
  label: string;
  badgeBg: string;
  badgeColor: string;
  description: string;
} {
  switch (grade) {
    case 'A':
      return {
        label: 'Grade A',
        badgeBg: '#d8f3dc',
        badgeColor: '#1b4332',
        description: 'Premium quality - highest size, uniformity and freshness standard',
      };
    case 'B':
      return {
        label: 'Grade B',
        badgeBg: '#e0e7ff',
        badgeColor: '#3730a3',
        description: 'Standard quality - fresh and nutritious with minor cosmetic variation',
      };
    case 'C':
      return {
        label: 'Grade C',
        badgeBg: '#fef3c7',
        badgeColor: '#92400e',
        description: 'Commercial grade - great for cooking, preserves and juices',
      };
    default:
      return {
        label: 'Ungraded',
        badgeBg: '#f3f4f6',
        badgeColor: '#6b7280',
        description: 'Quality inspection pending',
      };
  }
}

/**
 * Detailed verification information for photos.
 * Implements requirement: "photos with verified badge".
 * If isGeoVerified is true, image was taken on farm / plot (within 2km).
 * If false, flags photo as unverified.
 */
export function getPhotoVerification(photo?: ProducePhoto | null): {
  isVerified: boolean;
  label: string;
  shortLabel: string;
  badgeBg: string;
  badgeColor: string;
  borderColor: string;
  description: string;
} {
  if (!photo) {
    return {
      isVerified: false,
      label: 'Photo Unverified',
      shortLabel: 'Unverified',
      badgeBg: '#f3f4f6',
      badgeColor: '#6b7280',
      borderColor: '#d1d5db',
      description: 'No GPS metadata found in photo',
    };
  }

  if (photo.isGeoVerified) {
    const distText = photo.distanceM !== null && photo.distanceM !== undefined
      ? ` (${formatDistance(photo.distanceM)} from farm centroid)`
      : '';
    return {
      isVerified: true,
      label: '✓ Verified Farm Photo',
      shortLabel: '✓ Verified',
      badgeBg: '#dcfce7',
      badgeColor: '#166534',
      borderColor: '#86efac',
      description: `GPS verified: Photo taken at the farm location${distText}`,
    };
  }

  // Not geo-verified
  let desc = 'Photo could not be verified at farm coordinates';
  if (photo.geoStatus === 'TOO_FAR') {
    desc = `Photo taken too far from farm location${
      photo.distanceM ? ` (${formatDistance(photo.distanceM)} away)` : ''
    }`;
  } else if (photo.geoStatus === 'NO_GPS') {
    desc = 'Photo lacks GPS EXIF metadata';
  } else if (photo.rejectReason) {
    desc = photo.rejectReason;
  }

  return {
    isVerified: false,
    label: '⚠ Unverified Photo',
    shortLabel: '⚠ Unverified',
    badgeBg: '#fee2e2',
    badgeColor: '#991b1b',
    borderColor: '#fca5a5',
    description: desc,
  };
}
