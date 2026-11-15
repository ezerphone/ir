package com.offlineremote.app.ir;

import android.content.Context;

import com.icontrol.dev.IControlIRData;
import com.icontrol.dev.IrData;
import com.offlineremote.app.usb.DongleManager;

/**
 * Unified transmit entry point.
 *
 * Two kinds of code reach the dongle:
 *
 *  1. NATIVE codes — bytes already in Tiqiaa's internal format, as produced by the
 *     embedded local DB ({@code LocalIrDb.DbInfrared.data()}) or by the learn feature
 *     ({@code IControlIRData.a()}). These are sent verbatim and are known-good.
 *
 *  2. FOREIGN codes — raw microsecond timings decoded from SmartIR / Broadlink / Pronto.
 *     The dongle's native t() expects Tiqiaa-format bytes, so these must be re-encoded.
 *     The exact packing is the single unknown in this project; see {@link ForeignEncoder}.
 *     It is isolated here so that once the format is confirmed on-device (by round-tripping
 *     a learned code through {@link IrData#p}), only the encoder changes.
 */
public final class TiqiaaTx {

    private final Context ctx;
    private final DongleManager dongle;
    private ForeignEncoder foreignEncoder = new ForeignEncoder.NativeBridge();

    public TiqiaaTx(Context ctx, DongleManager dongle) {
        this.ctx = ctx.getApplicationContext();
        this.dongle = dongle;
    }

    public void setForeignEncoder(ForeignEncoder enc) {
        if (enc != null) this.foreignEncoder = enc;
    }

    /** Send a known-good Tiqiaa-format code (local DB or learned). */
    public boolean sendNative(int freq, byte[] tiqiaaBytes) {
        return dongle.sendTiqiaa(freq, tiqiaaBytes);
    }

    /** Re-send a captured/learned signal. */
    public boolean sendLearned(IControlIRData learned) {
        if (learned == null) return false;
        return dongle.sendTiqiaa(learned.b(), learned.a());
    }

    /** Send a foreign signal (SmartIR etc.). Returns false if encoding is unavailable. */
    public boolean sendSignal(IrSignal signal) {
        if (signal == null || !signal.isValid()) return false;
        byte[] tiqiaaBytes = foreignEncoder.encode(ctx, signal);
        if (tiqiaaBytes == null) return false;
        return dongle.sendTiqiaa(signal.frequencyHz, tiqiaaBytes);
    }

    /**
     * Strategy for turning raw microsecond timings into the dongle's native byte format.
     */
    public interface ForeignEncoder {
        byte[] encode(Context ctx, IrSignal signal);

        /**
         * Attempts to use the Tiqiaa native library's own conversion helpers to build the
         * payload from raw timings. IrData exposes the DECODE direction (p: bytes -> int[]
         * microseconds) publicly; the encode direction is attempted here via so()/po().
         *
         * CALIBRATION NOTE: on first bring-up, learn any button, read its native bytes B
         * and timings T = IrData.p(ctx,0,0,B), then verify encode(T) reproduces B. Adjust
         * this method (or replace the strategy) until the round-trip matches. Until then,
         * the learn feature is the guaranteed path for foreign devices.
         */
        final class NativeBridge implements ForeignEncoder {
            @Override
            public byte[] encode(Context ctx, IrSignal signal) {
                // Pack the microsecond timings big-endian 16-bit as a raw descriptor and
                // let the native layer normalise it. This is the hypothesised format and
                // the designated calibration point.
                int[] us = signal.micros;
                byte[] raw = new byte[us.length * 2];
                for (int i = 0; i < us.length; i++) {
                    int v = Math.min(0xFFFF, Math.max(0, us[i]));
                    raw[i * 2] = (byte) ((v >> 8) & 0xFF);
                    raw[i * 2 + 1] = (byte) (v & 0xFF);
                }
                try {
                    byte[] out = IrData.so(ctx, signal.frequencyHz, us.length, raw);
                    if (out != null && out.length > 0) return out;
                } catch (Throwable ignore) {
                    // native format mismatch — fall through
                }
                return null;
            }
        }
    }
}
