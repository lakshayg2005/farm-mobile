import React from 'react';
import { Text as RNText, StyleSheet, StyleProp, TextStyle } from 'react-native';

interface TextProps {
  children: React.ReactNode;
  variant?: 'h1' | 'h2' | 'body' | 'caption' | 'label';
  style?: StyleProp<TextStyle>;
}

export function Text({ children, variant = 'body', style }: TextProps) {
  return <RNText style={[styles[variant], style]}>{children}</RNText>;
}

const styles = StyleSheet.create({
  h1: { fontSize: 28, fontWeight: '700', color: '#1b1b1b' },
  h2: { fontSize: 22, fontWeight: '600', color: '#1b1b1b' },
  body: { fontSize: 15, color: '#333' },
  caption: { fontSize: 12, color: '#777' },
  label: { fontSize: 13, fontWeight: '500', color: '#555' },
});
