package com.offlineremote.app.ir;

import android.util.Base64;

import java.util.ArrayList;
import java.util.List;

/**
 * Decodes Broadlink IR packets (as used by the SmartIR database, "Base64" or "Hex"
 * encodings) into an {@link IrSignal} of raw microsecond timings.
 *
 * Broadlink IR packet layout:
 *   [0]      = 0x26 (IR)   (0xb2/0xd7 would be RF — not handled)
 *   [1]      = repeat count
 *   [2],[3]  = little-endian length of the timing payload that follows
 *   payload  = tick values; a 0x00 byte means "the next two bytes are a big-endian word"
 *   (optional trailing 0x0d 0x05 end marker)
 *
 * One Broadlink tick = 269/8192 milliseconds = 32.8369 microseconds.
 * Broadlink does not encode the carrier frequency, so we assume 38 kHz.
 */
public final class BroadlinkCodec {

    private static final double MICROS_PER_TICK = 269000.0 / 8192.0; // 32.8369 us

    private BroadlinkCodec() {}

    public static IrSignal decodeBase64(String b64) {
        if (b64 == null) return null;
        byte[] raw;
        try {
            raw = Base64.decode(b64.trim(), Base64.DEFAULT);
        } catch (Exception e) {
            return null;
        }
        return decode(raw);
    }

    public static IrSignal decodeHex(String hex) {
        byte[] raw = hexToBytes(hex);
        return decode(raw);
    }

    public static IrSignal decode(byte[] b) {
        if (b == null || b.length < 6) return null;
        // b[0] should be 0x26 for IR; tolerate other values but bail on RF.
        int header = b[0] & 0xFF;
        if (header == 0xb2 || header == 0xd7) return null; // RF, unsupported

        int len = (b[2] & 0xFF) | ((b[3] & 0xFF) << 8);
        int end = Math.min(b.length, 4 + len);
        if (len <= 0) end = b.length; // some payloads have a zero length field

        List<Integer> out = new ArrayList<>();
        int i = 4;
        while (i < end) {
            int v = b[i] & 0xFF;
            i++;
            if (v == 0) {
                if (i + 1 >= end) break;
                v = ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
                i += 2;
            }
            // Drop the trailing end marker if it slips through as a tiny tail.
            int micros = (int) Math.round(v * MICROS_PER_TICK);
            out.add(micros);
        }
        if (out.size() < 2) return null;
        int[] arr = new int[out.size()];
        for (int k = 0; k < arr.length; k++) arr[k] = out.get(k);
        return new IrSignal(38000, arr);
    }

    public static byte[] hexToBytes(String hex) {
        if (hex == null) return null;
        String s = hex.replaceAll("[^0-9a-fA-F]", "");
        if (s.length() % 2 != 0) return null;
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
