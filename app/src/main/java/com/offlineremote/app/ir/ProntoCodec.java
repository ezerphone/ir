package com.offlineremote.app.ir;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses Pronto Hex (CCF) codes into an {@link IrSignal}.
 *
 * Pronto layout (space-separated 4-hex-digit words):
 *   w0 = format (0x0000 = raw oscillated / learned)
 *   w1 = frequency divisor; carrier period (us) = w1 * 0.241246
 *   w2 = burst-pair count of the "once" sequence
 *   w3 = burst-pair count of the "repeat" sequence
 *   then w2*2 + w3*2 burst values, each a count of carrier periods.
 */
public final class ProntoCodec {

    private static final double PRONTO_CLOCK_US = 0.241246;

    private ProntoCodec() {}

    public static IrSignal parse(String pronto) {
        if (pronto == null) return null;
        String[] toks = pronto.trim().split("\\s+");
        if (toks.length < 6) return null;
        int[] w = new int[toks.length];
        try {
            for (int i = 0; i < toks.length; i++) w[i] = Integer.parseInt(toks[i], 16);
        } catch (NumberFormatException e) {
            return null;
        }

        double periodUs = w[1] * PRONTO_CLOCK_US;
        if (periodUs <= 0) return null;
        int freq = (int) Math.round(1_000_000.0 / periodUs);

        int n1 = w[2];
        int n2 = w[3];
        int idx = 4;

        // Prefer the "once" sequence; fall back to the repeat sequence.
        int pairs = n1 > 0 ? n1 : n2;
        if (n1 == 0) idx = 4; // repeat sequence starts right after header when n1==0
        else idx = 4;

        List<Integer> out = new ArrayList<>();
        int count = pairs * 2;
        for (int k = 0; k < count && idx < w.length; k++, idx++) {
            out.add((int) Math.round(w[idx] * periodUs));
        }
        if (out.size() < 2) return null;
        int[] arr = new int[out.size()];
        for (int k = 0; k < arr.length; k++) arr[k] = out.get(k);
        return new IrSignal(freq, arr);
    }
}
