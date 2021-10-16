/*
 * JNI wrapper for the SBSMS library
 * Used for high quality pitch and speed modulation
 * Based on Audacity's usage of the library
 * https://github.com/audacity/audacity/blob/master/src/effects/SBSMSEffect.cpp
 */

#include <cmath>
#include <cstring>

#include "jsbsms.h"
#include "sbsms.h"
#include "samplecount.h"
#include "MemoryX.h"

using namespace _sbsms_;

enum {
  SBSMSOutBlockSize = 512
};

/**
 * Resample data buffer
 */
class ResampleBuf
{
public:
   ResampleBuf() {
      processed = 0;
   }

   ~ResampleBuf() {}

   bool bPitch;
   ArrayOf<audio> buf;
   double ratio;
   sampleCount processed;
   size_t blockSize;
   long SBSMSBlockSize;
   sampleCount offset;
   sampleCount end;
   unsigned char channels;
   double** sampleBuf;
   std::unique_ptr<SBSMS> sbsms;
   std::unique_ptr<SBSMSInterface> iface;
   ArrayOf<audio> SBSMSBuf;

   // Not required by callbacks, but makes for easier cleanup
   std::unique_ptr<Resampler> resampler;
   std::unique_ptr<SBSMSQuality> quality;

   //std::exception_ptr mpException {};
};

/**
 * Interface where we pass our resampler
 */
class SBSMSEffectInterface final : public SBSMSInterfaceSliding {
public:
   SBSMSEffectInterface(Resampler *resampler,
                        Slide *rateSlide, Slide *pitchSlide,
                        bool bReferenceInput,
                        long samples, long preSamples,
                        SBSMSQuality *quality)
      : SBSMSInterfaceSliding(rateSlide,pitchSlide,bReferenceInput,samples,preSamples,quality)
   {
      this->resampler = resampler;
   }
   virtual ~SBSMSEffectInterface() {}

   long samples(audio *buf, long n) {
      return resampler->read(buf, n);
   }

protected:
   Resampler *resampler;
};

/**
 * Pitch slide implementation that supports arbitrary pitch factors per sample
 */
class VariableOutputRateSlideImp : public SlideImp {
public:
    VariableOutputRateSlideImp(float *rates, const SampleCountType &n) {
        this->rates = rates;
        this->idx = 0;
        if (n) {
            this->numSamples = n;
        }
    }
    float getTotalStretch() {
      return 1.0f;
    }
    float getStretchedTime(float t) {
      return t;
    }
    float getInverseStretchedTime(float t) {
      return t;
    }
    float getRate(float t) {
        size_t rateIdx = (int) (t * (numSamples - 1));
        if (rateIdx >= numSamples)
            rateIdx = numSamples - 1;
        return rates[rateIdx];
    }
    float getStretch(float t) {
        return 1.0f;
    }
    float getMeanStretch(float t0, float t1) {
        return 1.0f;
    }
    float getRate() {
        return rates[idx];
    }
    float getStretch() {
        return 1.0f;
    }
    void step() {
        idx++;
    }
protected:
    size_t idx;
    size_t numSamples;
    float *rates;
};

/**
 * Wrapper for VariableOutputRateSlideImp
 */
class VariableOutputRateSlide : public Slide {
public:
    VariableOutputRateSlide(float *rates, const SampleCountType &n) : Slide(SlideIdentity, 1.0f, 1.0f, n) {
        if (imp)
            delete imp;
        imp = new VariableOutputRateSlideImp(rates, n);
    }
};

size_t GetBestBlockSize(sampleCount start, sampleCount end) {
    return 1 << (int) std::floor(std::log2l((end - start).as_long_long()));
}

/**
 * Perform resample on a frame of sample data
 * @param cb_data   Callback data
 * @param data      Frame data
 */
long resampleCB(void *cb_data, SBSMSFrame *data) {
    ResampleBuf *r = (ResampleBuf*) cb_data;

    auto blockSize = limitSampleBufferSize(
        r->blockSize,
        r->end - r->offset
    );

    // convert to sbsms audio format
    size_t o = r->offset.as_size_t();
    for (decltype(blockSize) i=0; i < blockSize && o < r->end; i++, o++) {
        for (unsigned char c = 0; c < r->channels; c++) {
            r->buf[i][c] = r->sampleBuf[c][o];
        }
    }

    data->buf = r->buf.get();
    data->size = blockSize;
    if(r->bPitch) {
        float t0 = r->processed.as_float() / r->iface->getSamplesToInput();
        float t1 = (r->processed + blockSize).as_float() / r->iface->getSamplesToInput();
        data->ratio0 = r->iface->getStretch(t0);
        data->ratio1 = r->iface->getStretch(t1);
    } else {
        data->ratio0 = r->ratio;
        data->ratio1 = r->ratio;
    }
    r->processed += blockSize;
    r->offset += blockSize;
    return blockSize;
}

/**
 * Callback passed to the SBSMS resampler
 * @param cb_data   Callback data
 * @param data      Frame data
 */
long postResampleCB(void *cb_data, SBSMSFrame *data) {
   ResampleBuf *r = (ResampleBuf*) cb_data;
   auto count = r->sbsms->read(r->iface.get(), r->SBSMSBuf.get(), r->SBSMSBlockSize);
   data->buf = r->SBSMSBuf.get();
   data->size = count;
   data->ratio0 = 1.0 / r->ratio;
   data->ratio1 = 1.0 / r->ratio;
   return count;
}

/**
 * Process WAV samples using tempo and pitch slide
 * @param sampleArray   WAV samples array
 * @param tempoSlide    Tempo slide
 * @param pitchSlide    Pitch slide
 */
jobjectArray process(JNIEnv *env, jclass cl, jobjectArray sampleArray, Slide &tempoSlide, Slide &pitchSlide) {

    jint channelCount = env->GetArrayLength(sampleArray);

    ResampleBuf rb;
    rb.sampleBuf = (double**) malloc(channelCount * sizeof(double*));

    jboolean isCopy;
    size_t inSampleCount = 1 << 31;
    for (int c = 0; c < channelCount; c++) {
        jobject elem = env->GetObjectArrayElement(sampleArray, c);
        jdoubleArray channelSamples = (jdoubleArray) elem;
        inSampleCount = std::min(inSampleCount, (size_t) env->GetArrayLength(channelSamples));
        rb.sampleBuf[c] = env->GetDoubleArrayElements(channelSamples, &isCopy);
    }

    float totalStretch = tempoSlide.getTotalStretch();

    auto maxBlockSize = GetBestBlockSize(0, inSampleCount);

    rb.blockSize = maxBlockSize;
    rb.buf.reinit(rb.blockSize, true);

    rb.bPitch = false;
    auto outSlideType = SlideIdentity;
    SBSMSResampleCB outResampleCB = postResampleCB;
    rb.ratio = 1.0;
    rb.channels = channelCount;
    rb.quality = std::make_unique<SBSMSQuality>(&SBSMSQualityStandard);
    rb.resampler = std::make_unique<Resampler>(resampleCB, &rb, SlideIdentity);
    rb.sbsms = std::make_unique<SBSMS>(rb.channels, rb.quality.get(), true);
    rb.SBSMSBlockSize = rb.sbsms->getInputFrameSize();
    rb.SBSMSBuf.reinit(static_cast<size_t>(rb.SBSMSBlockSize), true);

    rb.offset = 0;
    rb.end = inSampleCount;

    rb.iface = std::make_unique<SBSMSEffectInterface>
                      (rb.resampler.get(), &tempoSlide, &pitchSlide,
                       false,
                       // UNSAFE_SAMPLE_COUNT_TRUNCATION
                       // The argument type is only long!
                       static_cast<long> (inSampleCount),
                       // This argument type is also only long!
                       static_cast<long> (0),
                       rb.quality.get());

    Resampler resampler(outResampleCB, &rb, outSlideType);

    long sbOutCount = rb.iface->getSamplesToOutput();

    long pos = 0;
    long outputCount = -1;
    audio outBuf[SBSMSOutBlockSize];

    double **outputBuf = (double**) malloc(channelCount * sizeof(double*));
    for (int c = 0; c < channelCount; c++)
        outputBuf[c] = new double[sbOutCount];

    // Process
    while(pos < sbOutCount && outputCount) {
       const auto frames = limitSampleBufferSize(SBSMSOutBlockSize, sbOutCount - pos);

       outputCount = resampler.read(outBuf, frames);
       for(int i = 0; i < outputCount; i++) {
          for (int c = 0; c < channelCount; c++)
            outputBuf[c][i + pos] = outBuf[i][c];
       }
       pos += outputCount;
    }

    // Build output and cleanup
    jclass dblArrClass = env->GetObjectClass(env->GetObjectArrayElement(sampleArray, 0));
    jobjectArray outputArray = env->NewObjectArray(channelCount, dblArrClass, NULL);
    for (int c = 0; c < channelCount; c++) {

        // Release samples
        jobject elem = env->GetObjectArrayElement(sampleArray, c);
        jdoubleArray channelSamples = (jdoubleArray) elem;
        env->ReleaseDoubleArrayElements(channelSamples, rb.sampleBuf[c], JNI_ABORT);

        // Create output array
        jdoubleArray channelOutput = env->NewDoubleArray(sbOutCount);
        env->SetDoubleArrayRegion(channelOutput, 0, sbOutCount, outputBuf[c]);
        env->SetObjectArrayElement(outputArray, c, channelOutput);
        delete[] outputBuf[c];
    }

    delete[] outputBuf;
    delete[] rb.sampleBuf;

    return outputArray;

}

/**
 * Process WAV samples using linear tempo and pitch slides
 * @param sampleArray   WAV samples array
 * @param startTempo    Start tempo factor (1 = default)
 * @param endTempo      End tempo factor
 * @param startPitch    Start pitch factor
 * @param endPitch      End pitch factor
 */
JNIEXPORT jobjectArray JNICALL Java_software_blob_audio_effects_sbsms_SBSMSEffect_process___3_3DDDDD(
             JNIEnv *env, jclass cl, jobjectArray sampleArray,
             jdouble startTempo, jdouble endTempo, jdouble startPitch, jdouble endPitch) {

    Slide tempoSlide(startTempo == endTempo ? SlideConstant : SlideLinearOutputRate, startTempo, endTempo);
    Slide pitchSlide(startPitch == endPitch ? SlideConstant : SlideLinearOutputRate, startPitch, endPitch);

    return process(env, cl, sampleArray, tempoSlide, pitchSlide);
}

/**
 * Process WAV samples using a linear tempo slide and an array of pitch modulations per each sample
 * @param sampleArray   WAV samples array
 * @param startTempo    Start tempo factor (1 = default)
 * @param endTempo      End tempo factor
 * @param pitchArray    Pitch modulation factors (per each entry in sampleArray)
 */
JNIEXPORT jobjectArray JNICALL Java_software_blob_audio_effects_sbsms_SBSMSEffect_process___3_3DDD_3F(
        JNIEnv *env, jclass cl, jobjectArray sampleArray,
        jdouble startTempo, jdouble endTempo, jfloatArray pitchArray) {

    jboolean isCopy;
    jint sampleCount = env->GetArrayLength(pitchArray);
    float *pitches = env->GetFloatArrayElements(pitchArray, &isCopy);

    Slide tempoSlide(startTempo == endTempo ? SlideConstant : SlideLinearOutputRate, startTempo, endTempo);
    VariableOutputRateSlide pitchSlide(pitches, sampleCount);

    return process(env, cl, sampleArray, tempoSlide, pitchSlide);
}