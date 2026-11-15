package com.icontrol.dev;

import android.content.Context;

/**
 * JNI-boundary class. Native methods are implemented in libtiqiaadev.so and bound by
 * exact name/signature. Reconstructed from the decompiled ZaZaRemote 5.4.0 class.
 *
 * These native helpers convert between Tiqiaa's internal IR byte format and raw
 * microsecond timing arrays:
 *   p(ctx, 0, 0, data)  -> int[]   : decode Tiqiaa bytes to microsecond timings
 *   si/pi               -> IrData  : parse received bytes into an IrData (freq, quality, data)
 *   so/po/bo            -> byte[]   : pack/encode (used by the IRS/plug drivers)
 *
 * NOTE: For the USB dongle (TiqiaaUsbController.t), the pattern bytes are passed to the
 * native layer directly and encoded there. The pack direction (raw timings -> Tiqiaa
 * bytes) for FOREIGN codes is what TiqiaaTx must calibrate on-device.
 */
public final class IrData {
    public byte[] data;
    public int freq;
    public int quality;

    public IrData(int i10, int i11, byte[] bArr) {
        this.freq = i10;
        this.quality = i11;
        this.data = bArr;
    }

    private static native byte[] bo(Context context, int i10, int i11, byte[] bArr);

    private static native byte[] c(Context context, byte[] bArr, byte[] bArr2);

    public static native byte[] d(Context context, byte[] bArr);

    public static byte[] getEdaData(Context context, int i10, byte[] bArr) {
        return bo(context, 4, i10, bArr);
    }

    public static byte[] getPlugPattern(Context context, int i10, byte[] bArr) {
        return po(context, i10, bArr);
    }

    public static byte[] getYaoyaoData(Context context, int i10, byte[] bArr) {
        return bo(context, 0, i10, bArr);
    }

    /** Decode Tiqiaa-format bytes into raw microsecond on/off timings. */
    public static native int[] p(Context context, int i10, int i11, byte[] bArr);

    public static native IrData pi(Context context, byte[] bArr);

    private static native byte[] po(Context context, int i10, byte[] bArr);

    public static native IrData si(Context context, int i10, byte[] bArr);

    public static native byte[] so(Context context, int i10, int i11, byte[] bArr);

    public static native long st(Context context);
}
