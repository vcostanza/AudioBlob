package software.blob.audio.audacity.fft;

import software.blob.audio.audacity.types.DoublePtr;

import java.util.HashMap;
import java.util.Map;

public class FFT {

    public static final Map<Integer, FFT> lengthCache = new HashMap<>();

    public static FFT get(int fftLen) {
        FFT fft = lengthCache.get(fftLen);
        if (fft == null)
            lengthCache.put(fftLen, fft = new FFT(fftLen));
        return fft;
    }

    public final int length;
    public int points;
    public double[] sinTable;
    public int[] bitReversed;

    private FFT(int fftLen) {
        /*
         *  FFT size is only half the number of data points
         *  The full FFT output can be reconstructed from this FFT's output.
         *  (This optimization can be made since the data is real.)
         */
        this.length = fftLen;
        this.points = fftLen / 2;

        this.sinTable = new double[2*this.points];

        this.bitReversed = new int[this.points];

        for(int i = 0; i < this.points; i++) {
            int temp = 0;
            for(int mask = this.points / 2; mask > 0; mask >>= 1)
                temp = (temp >> 1) + ((i & mask) != 0 ? this.points : 0);

            this.bitReversed[i] = temp;
        }

        for(int i = 0; i < this.points; i++) {
            this.sinTable[this.bitReversed[i]] = -Math.sin(2*Math.PI*i/(2*this.points));
            this.sinTable[this.bitReversed[i]+1] = -Math.cos(2*Math.PI*i/(2*this.points));
        }
    }

    public void hannWindowFunc(boolean extraSample, DoublePtr in) {
        int NumSamples = this.length;
        if (extraSample)
            --NumSamples;

        // Hann
        double multiplier = 2 * Math.PI / NumSamples;
        double coeff0 = 0.5, coeff1 = -0.5;
        for (int ii = 0; ii < NumSamples; ++ii)
            in.multiply(ii, coeff0 + coeff1 * Math.cos(ii * multiplier));

        if (extraSample) {
            double value = 0.0;
            in.multiply(NumSamples, value);
        }
    }

    public void hannWindowFunc(boolean extraSample, double[] in) {
        hannWindowFunc(extraSample, new DoublePtr(in));
    }

    public void apply(double[] RealIn, double[] RealOut, double[] ImagOut) {
        double[] pFFT = new double[this.length];
        // Copy the data into the processing buffer
        if (this.length >= 0) System.arraycopy(RealIn, 0, pFFT, 0, this.length);

        // Perform the FFT
        apply(pFFT);

        // Copy the data into the real and imaginary outputs
        for (int i = 1; i<(this.length / 2); i++) {
            RealOut[i] = pFFT[this.bitReversed[i]  ];
            ImagOut[i] = pFFT[this.bitReversed[i]+1];
        }
        // Handle the (real-only) DC and Fs/2 bins
        RealOut[0] = pFFT[0];
        RealOut[this.length / 2] = pFFT[1];
        ImagOut[0] = ImagOut[this.length / 2] = 0;
        // Fill in the upper half using symmetry properties
        for(int i = this.length / 2 + 1; i < this.length; i++) {
            RealOut[i] =  RealOut[this.length-i];
            ImagOut[i] = -ImagOut[this.length-i];
        }
    }

    public void apply(DoublePtr buffer) {
        int A, B;
        int sptr;
        int endptr1, endptr2;
        int br1, br2;
        double HRplus,HRminus,HIplus,HIminus;
        double v1,v2,sin,cos;

        int ButterfliesPerGroup = this.points / 2;

        /*
         *  Butterfly:
         *     Ain-----Aout
         *         \ /
         *         / \
         *     Bin-----Bout
         */

        endptr1 = this.points * 2;

        while (ButterfliesPerGroup > 0) {
            A = 0;
            B = ButterfliesPerGroup * 2;
            sptr = 0;

            while (A < endptr1) {
                sin = this.sinTable[sptr];
                cos = this.sinTable[sptr+1];
                endptr2 = B;
                while (A < endptr2) {
                    v1 = buffer.get(B) * cos + buffer.get(B + 1) * sin;
                    v2 = buffer.get(B) * sin - buffer.get(B + 1) * cos;
                    buffer.set(B, buffer.get(A) + v1);
                    buffer.set(A++, buffer.get(B++) - 2 * v1);
                    buffer.set(B, buffer.get(A) - v2);
                    buffer.set(A++, buffer.get(B++) + 2 * v2);
                }
                A = B;
                B += ButterfliesPerGroup * 2;
                sptr += 2;
            }
            ButterfliesPerGroup >>= 1;
        }
        /* Massage output to get the output for a real input sequence. */
        br1 = 1;
        br2 = this.points - 1;

        while(br1 < br2) {
            sin = this.sinTable[this.bitReversed[br1]];
            cos = this.sinTable[this.bitReversed[br1] + 1];
            A = this.bitReversed[br1];
            B = this.bitReversed[br2];
            HRplus = (HRminus = buffer.get(A) - buffer.get(B)) + (buffer.get(B) * 2);
            HIplus = (HIminus = buffer.get(A+1) - buffer.get(B+1)) + (buffer.get(B+1) * 2);
            v1 = (sin*HRminus - cos*HIplus);
            v2 = (cos*HRminus + sin*HIplus);
            buffer.set(A, (HRplus  + v1) * 0.5d);
            buffer.set(B, buffer.get(A) - v1);
            buffer.set(A+1, (HIminus + v2) * 0.5d);
            buffer.set(B+1, buffer.get(A+1) - HIminus);

            br1++;
            br2--;
        }
        /* Handle the center bin (just need a conjugate) */
        A = this.bitReversed[br1] + 1;
        buffer.set(A, -buffer.get(A));
        /* Handle DC bin separately - and ignore the Fs/2 bin
           buffer[0]+=buffer[1];
           buffer[1]=(fft_type)0;*/
        /* Handle DC and Fs/2 bins separately */
        /* Put the Fs/2 value into the imaginary part of the DC bin */
        v1 = buffer.get(0) - buffer.get(1);
        buffer.add(0, buffer.get(1));
        buffer.set(1, v1);
    }

    public void apply(double[] buffer) {
        apply(new DoublePtr(buffer));
    }

    // XXX - DELETE
    public void applyCopy(double[] buffer) {
        int A, B;
        int sptr;
        int endptr1, endptr2;
        int br1, br2;
        double HRplus,HRminus,HIplus,HIminus;
        double v1,v2,sin,cos;

        int ButterfliesPerGroup = this.points / 2;

        /*
         *  Butterfly:
         *     Ain-----Aout
         *         \ /
         *         / \
         *     Bin-----Bout
         */

        endptr1 = this.points * 2;

        while (ButterfliesPerGroup > 0) {
            A = 0;
            B = ButterfliesPerGroup * 2;
            sptr = 0;

            while (A < endptr1) {
                sin = this.sinTable[sptr];
                cos = this.sinTable[sptr+1];
                endptr2 = B;
                while (A < endptr2) {
                    v1 = buffer[B] * cos + buffer[B + 1] * sin;
                    v2 = buffer[B] * sin - buffer[B + 1] * cos;
                    buffer[B] = (buffer[A] + v1);
                    buffer[A++] = buffer[B++] - 2 * v1;
                    buffer[B] = (buffer[A] - v2);
                    buffer[A++] = buffer[B++] + 2 * v2;
                }
                A = B;
                B += ButterfliesPerGroup * 2;
                sptr += 2;
            }
            ButterfliesPerGroup >>= 1;
        }
        /* Massage output to get the output for a real input sequence. */
        br1 = 1;
        br2 = this.points - 1;

        while(br1 < br2) {
            sin = this.sinTable[this.bitReversed[br1]];
            cos = this.sinTable[this.bitReversed[br1] + 1];
            A = this.bitReversed[br1];
            B = this.bitReversed[br2];
            HRplus = (HRminus = buffer[A] - buffer[B]) + (buffer[B] * 2);
            HIplus = (HIminus = buffer[A+1] - buffer[B+1]) + (buffer[B+1] * 2);
            v1 = (sin*HRminus - cos*HIplus);
            v2 = (cos*HRminus + sin*HIplus);
            buffer[A] = (HRplus  + v1) * 0.5d;
            buffer[B] = buffer[A]- v1;
            buffer[A+1] = (HIminus + v2) * 0.5d;
            buffer[B+1] = buffer[A+1] - HIminus;

            br1++;
            br2--;
        }
        /* Handle the center bin (just need a conjugate) */
        A = this.bitReversed[br1] + 1;
        buffer[A] = -buffer[A];
        /* Handle DC bin separately - and ignore the Fs/2 bin
           buffer[0]+=buffer[1];
           buffer[1]=(fft_type)0;*/
        /* Handle DC and Fs/2 bins separately */
        /* Put the Fs/2 value into the imaginary part of the DC bin */
        v1 = buffer[0] - buffer[1];
        buffer[0] += buffer[1];
        buffer[1] = v1;
    }
}
