import React, { useRef } from 'react';
import {
  View,
  TextInput,
  StyleSheet,
  ViewStyle,
  NativeSyntheticEvent,
  TextInputKeyPressEventData,
} from 'react-native';

export interface OtpInputProps {
  length?: number;
  value: string;
  onChange: (otp: string) => void;
  disabled?: boolean;
  style?: ViewStyle;
}

export function OtpInput({
  length = 6,
  value,
  onChange,
  disabled = false,
  style,
}: OtpInputProps) {
  const inputRefs = useRef<Array<TextInput | null>>([]);

  const digits = value.split('').slice(0, length);
  while (digits.length < length) {
    digits.push('');
  }

  const handleChange = (text: string, index: number) => {
    // If user pasted a multi-digit string
    const clean = text.replace(/[^0-9]/g, '');
    if (clean.length > 1) {
      const newOtp = clean.slice(0, length);
      onChange(newOtp);
      const focusIndex = Math.min(newOtp.length, length - 1);
      inputRefs.current[focusIndex]?.focus();
      return;
    }

    const currentDigits = [...digits];
    currentDigits[index] = clean.slice(-1);
    const newOtp = currentDigits.join('');
    onChange(newOtp);

    // Auto-advance
    if (clean && index < length - 1) {
      inputRefs.current[index + 1]?.focus();
    }
  };

  const handleKeyPress = (
    e: NativeSyntheticEvent<TextInputKeyPressEventData>,
    index: number
  ) => {
    if (e.nativeEvent.key === 'Backspace') {
      if (!digits[index] && index > 0) {
        inputRefs.current[index - 1]?.focus();
        const currentDigits = [...digits];
        currentDigits[index - 1] = '';
        onChange(currentDigits.join(''));
      }
    }
  };

  return (
    <View style={[styles.container, style]}>
      {digits.map((digit, index) => {
        const isFocused = !disabled && (digit !== '' || digits.findIndex((d) => d === '') === index);
        return (
          <TextInput
            key={index}
            ref={(ref) => {
              inputRefs.current[index] = ref;
            }}
            style={[
              styles.box,
              digit ? styles.boxFilled : null,
              isFocused ? styles.boxFocused : null,
              disabled ? styles.boxDisabled : null,
            ]}
            value={digit}
            onChangeText={(text) => handleChange(text, index)}
            onKeyPress={(e) => handleKeyPress(e, index)}
            keyboardType="number-pad"
            maxLength={index === 0 ? length : 1}
            editable={!disabled}
            textAlign="center"
            selectTextOnFocus
          />
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginVertical: 14,
    gap: 8,
  },
  box: {
    flex: 1,
    height: 52,
    borderWidth: 1.5,
    borderColor: '#d0d0d0',
    borderRadius: 10,
    backgroundColor: '#fff',
    fontSize: 22,
    fontWeight: '700',
    color: '#1b1b1b',
  },
  boxFilled: {
    borderColor: '#2d6a4f',
    backgroundColor: '#f6fbf7',
  },
  boxFocused: {
    borderColor: '#2d6a4f',
    borderWidth: 2,
  },
  boxDisabled: {
    backgroundColor: '#f5f5f5',
    opacity: 0.6,
  },
});
