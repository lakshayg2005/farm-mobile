import type { DeliveryAddress } from '@farm/api/src/order';

/** Delivery address as typed in the form (everything is a string until validated). */
export type AddressForm = {
  label: string;
  line1: string;
  line2: string;
  city: string;
  pincode: string;
  lat: string;
  lng: string;
  contactPhone: string;
};

export type AddressErrors = Partial<Record<keyof AddressForm, string>>;

/**
 * TODO(M1): the coordinates should come from the device location (expo-location, added with the
 * browse screen, farm-mobile #3). Until then the form starts with a sample point in Jaipur.
 */
export const emptyAddress: AddressForm = {
  label: 'Home',
  line1: '',
  line2: '',
  city: '',
  pincode: '',
  lat: '26.9124',
  lng: '75.7873',
  contactPhone: '',
};

export type AddressResult =
  { ok: true; value: DeliveryAddress } | { ok: false; errors: AddressErrors };

function coordinate(text: string, min: number, max: number): number | null {
  if (text.trim() === '') return null;
  const value = Number(text);
  return Number.isFinite(value) && value >= min && value <= max ? value : null;
}

/** Same rules as the order service, so the server rarely has to reject the address. */
export function validateAddress(form: AddressForm): AddressResult {
  const errors: AddressErrors = {};
  if (form.line1.trim() === '') errors.line1 = 'Enter the street address';
  if (form.city.trim() === '') errors.city = 'Enter the city';
  if (!/^\d{6}$/.test(form.pincode.trim())) errors.pincode = 'Enter a 6 digit pincode';
  const lat = coordinate(form.lat, -90, 90);
  const lng = coordinate(form.lng, -180, 180);
  if (lat === null) errors.lat = 'Latitude must be between -90 and 90';
  if (lng === null) errors.lng = 'Longitude must be between -180 and 180';
  const phone = form.contactPhone.trim();
  if (phone !== '' && !/^\+?\d{10,13}$/.test(phone)) errors.contactPhone = 'Enter a valid phone';

  if (Object.keys(errors).length > 0 || lat === null || lng === null) return { ok: false, errors };
  return {
    ok: true,
    value: {
      label: form.label.trim() || null,
      line1: form.line1.trim(),
      line2: form.line2.trim() || null,
      city: form.city.trim(),
      pincode: form.pincode.trim(),
      location: { type: 'Point', coordinates: [lng, lat] }, // GeoJSON is [lng, lat]
      contactPhone: phone || null,
    },
  };
}
