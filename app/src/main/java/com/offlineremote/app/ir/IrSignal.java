package com.offlineremote.app.ir;

/**
 * A device-independent infrared signal: a carrier frequency plus a pattern of
 * alternating ON/OFF durations in microseconds (pulse, space, pulse, space, ...).
 *
 * This is the lingua franca we convert every external database (Broadlink, Pronto, raw)
 * into. For codes that already come from the Tiqiaa engine (local DB / learned), we keep
 * the native byte payload instead and don't need this representation.
 */
public final class IrSignal {
    public final int frequencyHz;   // e.g. 38000
    public final int[] micros;      // on/off durations in microseconds

    public IrSignal(int frequencyHz, int[] micros) {
        this.frequencyHz = frequencyHz <= 0 ? 38000 : frequencyHz;
        this.micros = micros;
    }

    public boolean isValid() {
        return micros != null && micros.length >= 2;
    }

    /** Total duration in microseconds (useful for sanity checks). */
    public long totalMicros() {
        long t = 0;
        if (micros != null) for (int v : micros) t += v;
        return t;
    }
}
