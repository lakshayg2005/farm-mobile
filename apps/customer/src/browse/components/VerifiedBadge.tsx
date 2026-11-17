import React from 'react';
import { View, StyleSheet, Text as RNText, StyleProp, ViewStyle } from 'react-native';
import { getPhotoVerification } from '../format.ts';
import type { ProducePhoto } from '../types.ts';

interface VerifiedBadgeProps {
  photo?: ProducePhoto | null;
  compact?: boolean;
  style?: StyleProp<ViewStyle>;
}

export function VerifiedBadge({ photo, compact = false, style }: VerifiedBadgeProps) {
  const meta = getPhotoVerification(photo);

  if (compact) {
    return (
      <View
        style={[
          styles.badgeCompact,
          { backgroundColor: meta.badgeBg, borderColor: meta.borderColor },
          style,
        ]}
      >
        <RNText style={[styles.textCompact, { color: meta.badgeColor }]}>
          {meta.shortLabel}
        </RNText>
      </View>
    );
  }

  return (
    <View
      style={[
        styles.badgeDetailed,
        { backgroundColor: meta.badgeBg, borderColor: meta.borderColor },
        style,
      ]}
    >
      <View style={styles.headerRow}>
        <RNText style={[styles.textHeader, { color: meta.badgeColor }]}>
          {meta.label}
        </RNText>
      </View>
      <RNText style={[styles.textDesc, { color: meta.badgeColor }]}>
        {meta.description}
      </RNText>
    </View>
  );
}

const styles = StyleSheet.create({
  badgeCompact: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
    borderWidth: 1,
    alignSelf: 'flex-start',
  },
  textCompact: {
    fontSize: 11,
    fontWeight: '700',
  },
  badgeDetailed: {
    padding: 10,
    borderRadius: 8,
    borderWidth: 1,
    gap: 3,
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  textHeader: {
    fontSize: 13,
    fontWeight: '700',
  },
  textDesc: {
    fontSize: 11,
    lineHeight: 15,
    opacity: 0.9,
  },
});
