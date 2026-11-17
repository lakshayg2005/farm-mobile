import assert from 'node:assert/strict';
import { test } from 'node:test';
import { emptyAddress, validateAddress } from '../src/checkout/address.ts';

const valid = { ...emptyAddress, line1: ' 12 Green Park ', city: 'Jaipur', pincode: '302001' };

test('a valid form becomes the API address, trimmed, with a GeoJSON point ([lng, lat])', () => {
  const result = validateAddress({ ...valid, line2: '', contactPhone: '+919999999999' });

  assert.equal(result.ok, true);
  assert.deepEqual(result.value, {
    label: 'Home',
    line1: '12 Green Park',
    line2: null,
    city: 'Jaipur',
    pincode: '302001',
    location: { type: 'Point', coordinates: [75.7873, 26.9124] },
    contactPhone: '+919999999999',
  });
});

test('required fields are reported', () => {
  const result = validateAddress(emptyAddress);

  assert.equal(result.ok, false);
  assert.deepEqual(Object.keys(result.errors).sort(), ['city', 'line1', 'pincode']);
});

test('pincode must be 6 digits', () => {
  for (const pincode of ['12', '1234567', 'abcdef', '30200a']) {
    assert.equal(validateAddress({ ...valid, pincode }).ok, false, pincode);
  }
});

test('coordinates must be numbers in range', () => {
  for (const [field, value] of [
    ['lat', '91'],
    ['lat', ''],
    ['lng', '-181'],
    ['lng', 'x'],
  ]) {
    const result = validateAddress({ ...valid, [field]: value });
    assert.equal(result.ok, false, `${field}=${value}`);
    assert.ok(result.errors[field]);
  }
});

test('phone is optional but must look like a phone number', () => {
  assert.equal(validateAddress({ ...valid, contactPhone: '' }).ok, true);
  assert.equal(validateAddress({ ...valid, contactPhone: '12345' }).ok, false);
});
